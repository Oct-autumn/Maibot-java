package org.maibot.core.chat

import jakarta.persistence.EntityManager
import org.maibot.core.config.CoreConfig
import org.maibot.core.msg.OutgoingMessageService
import org.maibot.sdk.SNoGenerator
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.manager.InteractionEntityManager
import org.maibot.sdk.manager.MessageManager
import org.maibot.sdk.model.ModelManager
import org.maibot.sdk.model.payload.MessageContextItem
import org.maibot.sdk.msg.OutgoingMessage
import org.maibot.sdk.msg.SelfInfoRegistry
import org.maibot.sdk.storage.db.DatabaseService
import org.maibot.sdk.storage.db.dao.InteractionStream
import org.maibot.sdk.storage.db.dao.Message
import org.maibot.sdk.storage.model.msgevt.AbstractMessageEvent
import org.maibot.sdk.task.TaskExecuteService
import org.maibot.sdk.util.MapperUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 私聊聊天服务
 *
 * 负责一次完整的“收到消息 → 组装上下文 → 调用模型 → 发送回复 → 回复落库”流程。
 *
 * M1 阶段只支持私聊纯文本：
 * - 上下文只取最近 [CoreConfig.Chat.historyLimit] 条消息；
 * - Bot 自己的回复也会写入消息表，保证后续对话能接上；
 * - 同一个会话内的任务串行执行，避免连续发消息时上下文错乱。
 */
@Component
class ChatService
@AutoInject private constructor(
    @param:Value($$"${*}") private val coreConfig: CoreConfig,
    private val modelManager: ModelManager,
    private val databaseService: DatabaseService,
    private val messageManager: MessageManager,
    private val interactionEntityManager: InteractionEntityManager,
    private val selfInfoRegistry: SelfInfoRegistry,
    private val outgoingMessageService: OutgoingMessageService,
    private val taskExecuteService: TaskExecuteService
) {
    /** 该实例是否已成功注册聊天任务模型 */
    private val modelReady: Boolean

    /** 每个会话的串行任务链 */
    private val streamChains = ConcurrentHashMap<String, CompletableFuture<Void>>()

    init {
        val chatModels = coreConfig.modelTasks["chat"]
        modelReady = if (chatModels.isNullOrEmpty()) {
            log.warn("未配置 model_tasks.chat，聊天功能不可用")
            false
        } else {
            modelManager["chat"] = chatModels
            log.info("聊天任务模型已注册: {}", chatModels.joinToString { it.modelName })
            true
        }
    }

    /**
     * 提交一个按会话串行执行的任务
     *
     * 同一个会话上的任务会排队依次执行；不同会话之间互不影响。
     *
     * @param conversationKey 会话标识（平台 + 平台侧用户 ID）
     * @param task            任务体
     */
    fun submit(conversationKey: String, task: () -> Unit) {
        val chain = streamChains.compute(conversationKey) { _, previous ->
            val base = previous ?: CompletableFuture.completedFuture(null)
            // handle：即使上一个任务异常，也不影响后续任务继续排队
            base.handle { _, _ -> null }
                .thenRunAsync({ task() }, taskExecuteService.virtualExecutor())
        }

        chain?.whenComplete { _, _ -> streamChains.remove(conversationKey, chain) }
    }

    /**
     * 处理一条私聊消息：组装上下文、调用模型、发送回复并落库
     *
     * @param evt 消息事件（已持久化）
     */
    fun reply(evt: AbstractMessageEvent) {
        if (!modelReady) {
            return
        }

        val platform = evt.messageMeta.platform
        val peerInfo = evt.messageMeta.streamInfo.privateInfo ?: run {
            log.warn("私聊消息缺少对端信息，跳过回复 (sNo: {})", evt.serialNo.toHexString())
            return
        }

        val selfInfo = selfInfoRegistry.get(platform) ?: run {
            log.warn("平台 {} 尚未注册自身信息，无法生成回复", platform)
            return
        }

        val chatConfig = coreConfig.chat

        // 消息事件里只有平台侧信息，交互流主键需要按消息序列号反查
        val streamId = try {
            databaseService.exec { em -> messageManager.findStreamIdBySequence(em, evt.serialNo.sNo) }
        } catch (e: Exception) {
            log.error("查询消息所属交互流失败 (sNo: {})", evt.serialNo.toHexString(), e)
            return
        } ?: run {
            log.warn("未找到消息所属的交互流，跳过回复 (sNo: {})", evt.serialNo.toHexString())
            return
        }

        val context = try {
            this.buildContext(streamId, evt, selfInfo.platformUserId, selfInfo.nickname)
        } catch (e: Exception) {
            log.error("组装对话上下文失败 (stream: {})", streamId, e)
            return
        }

        if (context.isEmpty()) {
            log.debug("对话上下文为空，跳过回复 (stream: {})", streamId)
            return
        }

        val replyText = try {
            this.requestModel(context, chatConfig.replyTimeoutSec)
        } catch (e: Exception) {
            log.error("调用聊天模型失败 (stream: {})", streamId, e)
            return
        }

        if (replyText.isNullOrBlank()) {
            log.warn("模型返回空回复，跳过发送 (stream: {})", streamId)
            return
        }

        val sent = outgoingMessageService.send(
            OutgoingMessage.text(platform, peerInfo.platformId, replyText)
        )
        if (!sent) {
            log.warn("回复发送失败 (stream: {})", streamId)
            return
        }

        log.info("已回复私聊消息 (stream: {})，内容长度: {}", streamId, replyText.length)

        // 落库：Bot 的回复也写入消息表，作为后续对话的上下文
        try {
            this.saveBotMessage(streamId, replyText, selfInfo.platformUserId, selfInfo.nickname, platform)
        } catch (e: Exception) {
            log.error("保存 Bot 回复消息失败 (stream: {})", streamId, e)
        }
    }

    /**
     * 组装模型请求上下文
     *
     * 顺序：system（人设） + 最近若干条历史消息（按发送者区分 user / assistant）。
     * 当前消息在事件触发前已经落库，因此会出现在历史中，无需额外追加。
     */
    private fun buildContext(
        streamId: String,
        evt: AbstractMessageEvent,
        selfPlatformId: String,
        selfNickname: String
    ): List<MessageContextItem> {
        val platform = evt.messageMeta.platform
        val peerNickname = evt.messageMeta.senderInfo.nickname
        val historyLimit = coreConfig.chat.historyLimit

        return databaseService.exec { em ->
            val botEntity = interactionEntityManager.getOrCreatIfAbsent(
                em, platform, selfPlatformId, selfNickname
            )
            val botEntityId = botEntity?.id
            val history = messageManager.listRecentMessages(em, streamId, historyLimit)

            val items = ArrayList<MessageContextItem>(history.size + 1)
            items.add(MessageContextItem.ofSystem(this.buildSystemPrompt(peerNickname)))

            for (message in history) {
                val text = message.promptStr?.takeIf { it.isNotBlank() } ?: continue
                val isBotMessage = botEntityId != null && message.sender?.id == botEntityId
                items.add(
                    if (isBotMessage) {
                        MessageContextItem.ofAssistant(listOf(MessageContextItem.Content.ofText(text)))
                    } else {
                        MessageContextItem.ofUser(listOf(MessageContextItem.Content.ofText(text)))
                    }
                )
            }

            items
        }
    }

    /**
     * 调用聊天模型并返回纯文本回复
     */
    private fun requestModel(context: List<MessageContextItem>, timeoutSec: Long): String? {
        val handler = modelManager["chat"] ?: run {
            log.warn("聊天任务模型未注册")
            return null
        }

        val response = handler.getResponse(context).get(timeoutSec, TimeUnit.SECONDS)
        return response.response?.contentList?.firstOrNull()?.asText()?.trim()
    }

    /**
     * 构造 system prompt：人设 + Bot 身份 + 回复要求
     */
    private fun buildSystemPrompt(peerNickname: String): String {
        val persona = this.loadPersona()
        val botInfo = coreConfig.botInfo
        val aliases = if (botInfo.aliases.isEmpty()) "" else "（也可以叫：${botInfo.aliases.joinToString("、")}）"

        return buildString {
            appendLine(persona)
            appendLine()
            appendLine("你的名字是 ${botInfo.name}$aliases，现在你正在 QQ 上和用户“$peerNickname”私聊。")
            appendLine("要求：")
            appendLine("- 像真人聊天一样口语化、简短自然地回复，不要使用 Markdown、列表或颜文字堆砌；")
            appendLine("- 不要复述或解释这段设定，不要提到自己是 AI 模型；")
            appendLine("- 只输出要发送给对方的内容。")
        }.trim()
    }

    /**
     * 读取人设文件；文件不存在时退回内置的默认人设
     */
    private fun loadPersona(): String {
        val path = try {
            Path.of(coreConfig.chat.personaFile)
        } catch (e: Exception) {
            log.warn("人设文件路径无效: {}", coreConfig.chat.personaFile, e)
            return DEFAULT_PERSONA
        }

        return try {
            if (Files.exists(path)) {
                Files.readString(path).trim().ifBlank { DEFAULT_PERSONA }
            } else {
                log.warn("人设文件不存在: {}，使用默认人设", path.toAbsolutePath())
                DEFAULT_PERSONA
            }
        } catch (e: Exception) {
            log.warn("读取人设文件失败: {}，使用默认人设", path.toAbsolutePath(), e)
            DEFAULT_PERSONA
        }
    }

    /**
     * 把 Bot 的回复写入消息表
     *
     * 复用 message 表：sender 指向 Bot 自身的 InteractionEntity，objectType 标记为核心生成。
     */
    private fun saveBotMessage(
        streamId: String,
        text: String,
        selfPlatformId: String,
        selfNickname: String,
        platform: String
    ) {
        databaseService.exec { em: EntityManager ->
            DatabaseService.execInTransaction(em) {
                val botEntity = interactionEntityManager.getOrCreatIfAbsent(
                    em, platform, selfPlatformId, selfNickname
                ) ?: throw IllegalStateException("无法创建 Bot 的交互实体")

                val stream = em.find(InteractionStream::class.java, streamId)
                    ?: throw IllegalStateException("交互流不存在: $streamId")

                val message = Message().apply {
                    this.timestamp = System.currentTimeMillis()
                    this.sequence = SNoGenerator.nextSeq().sNo
                    this.promptStr = text
                    this.rawContentJson = MapperUtils.jsonMapper.writeValueAsString(mapOf("text" to text))
                    this.objectType = BOT_MESSAGE_OBJECT_TYPE
                    this.sender = botEntity
                    this.stream = stream
                }
                em.persist(message)
            }
        }
    }

    companion object {
        /** Bot 自身消息的对象类型标识（与 Adapter 的消息事件类型区分） */
        private const val BOT_MESSAGE_OBJECT_TYPE = "core.bot_text"

        /** 默认人设（人设文件缺失时使用） */
        private const val DEFAULT_PERSONA = "你是一个有自己性格、说话自然的聊天伙伴。"

        private val log: Logger = LoggerFactory.getLogger(ChatService::class.java)
    }
}
