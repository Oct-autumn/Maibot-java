package org.maibot.core.manager

import jakarta.persistence.EntityManager
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.manager.BinFileManager
import org.maibot.sdk.manager.ImageDescribeManager
import org.maibot.sdk.manager.ImageDescribeManager.ImageDescWithFuture
import org.maibot.sdk.model.ModelManager
import org.maibot.sdk.model.payload.MessageContextItem
import org.maibot.sdk.storage.db.dao.ImageDescribe
import org.maibot.sdk.task.TaskExecuteService
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Component
class ImageDescribeManagerImpl
@AutoInject private constructor(
    private val binFileManager: BinFileManager,
    private val modelManager: ModelManager,
    private val taskExecuteService: TaskExecuteService
) : ImageDescribeManager {
    // 锁对象映射，用于防止实例的重复创建，并避免重复调用LLM进行图像描述
    private val lockMap = ConcurrentHashMap<String, CompletableFuture<ImageDescWithFuture>>()

    /**
     * 获取或创建图像描述记录（如果不存在）
     *
     * @param em       实体管理器
     * @param binFile  图像的二进制文件信息
     * @param isEmoji  是否为表情图片
     * @return 图像描述记录及其描述文本的异步结果(涉及LLM调用)，若创建失败则返回 null
     */
    override fun getOrCreatIfAbsent(
        em: EntityManager, hash: String, fileType: String, imgData: ByteArray, isEmoji: Boolean
    ): ImageDescWithFuture? {
        // 使用锁对象，防止重复查询和创建
        val lockObject = CompletableFuture<ImageDescWithFuture>()
        val mappedLock = lockMap.putIfAbsent(hash, lockObject)

        if (mappedLock == null) {
            // 当前线程获得锁，执行获取或创建逻辑
            try {
                val imageDescWithFuture = (this.get(em, hash) ?: run { // 不存在则创建新实体
                    val binFile = binFileManager.getOrCreatIfAbsent(
                        em, hash, fileType, imgData
                    )?.binFile ?: throw RuntimeException("Unable to get/create BinFile")

                    ImageDescribe().run {
                        this.binFile = binFile
                        this.isEmoji = isEmoji
                        em.persist(this)
                        ImageDescWithFuture(this, CompletableFuture())
                    }
                }).also { imageDescWithFuture ->
                    imageDescWithFuture.descFuture?.let {
                        // 存在DescFuture，说明需要生成图像描述
                        // 因为图像描述往往不要求非常实时，因此异步执行图像描述生成，避免阻塞当前线程
                        taskExecuteService.submit(true) {
                            val desc = generateImageDescription(
                                imgData, fileType, isEmoji, modelManager
                            )

                            if (desc != null) {
                                imageDescWithFuture.descFuture?.complete(desc)
                                imageDescWithFuture.imageDesc.description = desc
                                em.merge(imageDescWithFuture.imageDesc)
                            } else {
                                imageDescWithFuture.descFuture?.complete("[描述生成失败]")
                            }
                        }
                    }
                }

                // 完成锁对象，通知等待的线程
                lockObject.complete(imageDescWithFuture)
                return imageDescWithFuture
            } catch (e: Exception) {
                log.error("获取或创建 ImageDescribe (binFileHash: {}) 时发生异常", hash, e)
                lockObject.completeExceptionally(e)
                return null
            } finally {
                // 移除锁对象，允许其他线程继续尝试获取或创建
                lockMap.remove(hash)
            }
        } else {
            // 其他线程已经在处理该图像描述的获取或创建，等待结果
            return try {
                mappedLock.get()
            } catch (e: Exception) {
                log.error("等待获取 ImageDescribe (binFileHash: {}) 结果时发生异常", hash, e)
                null
            }
        }
    }

    /**
     * 根据 BinFile 获取图像描述记录
     *
     * @param em      实体管理器
     * @param binFile 图像的二进制文件信息
     * @return 图像描述记录及其描述文本的异步结果(涉及LLM调用)，若不存在则返回 null
     */
    override fun get(
        em: EntityManager, hash: String
    ): ImageDescWithFuture? {
        try {
            val imageDesc = em.createQuery(
                "SELECT imgDesc FROM ImageDescribe imgDesc WHERE imgDesc.binFile.hash = :hash",
                ImageDescribe::class.java
            ).setParameter("hash", hash).resultList.firstOrNull() ?: return null

            return ImageDescWithFuture(imageDesc, null)
        } catch (e: Exception) {
            log.error("获取 ImageDescribe (binFileHash: {}) 时发生异常", hash, e)
            return null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImageDescribeManagerImpl::class.java)

        private const val IMAGE_DESC_PROMPT =
            "这是一张图片。\n" + "请用中文描述这张图片的内容：请留意其主题、直观感受；如果有文字，请把文字描述概括出来；输出为一段平文本，最多30字，请注意不要分点，只输出一段文本。"
        private const val GIF_DESC_PROMPT =
            "这是一个动图的抽帧，每一张图代表了动图的某一帧，黑色背景代表透明。\n" + "请用中文描述这张动图的内容：请留意其主题、直观感受；如果有文字，请把文字描述概括出来；输出为一段平文本，最多30字，请注意不要分点，只输出一段文本。"
        private const val EMOJI_DESC_PROMPT =
            "这是一个表情包。\n" + "请简要描述一下表情包所表达的情感和内容：简单描述内容、从互联网梗/meme的角度去分析。"
        private const val EMOJI_GIF_DESC_PROMPT =
            "这是一个动图表情包的抽帧，每一张图代表了动图的某一帧，黑色背景代表透明。\n" + "请简要描述一下表情包所表达的情感和内容：简单描述内容、从互联网梗/meme的角度去分析。"

        private fun generateImageDescription(
            imageData: ByteArray, imageType: String, isEmoji: Boolean, modelManager: ModelManager
        ): String? {
            // 构造请求体
            // TODO: 支持GIF

            val input = MessageContextItem.ofUser(
                listOf(
                    MessageContextItem.Content.ofImage(imageType, imageData), MessageContextItem.Content.ofText(
                        when {
                            isEmoji && imageType == "gif" -> EMOJI_GIF_DESC_PROMPT
                            isEmoji -> EMOJI_DESC_PROMPT
                            imageType == "gif" -> GIF_DESC_PROMPT
                            else -> IMAGE_DESC_PROMPT
                        }
                    )
                )
            )

            try {
                modelManager["image_desc"]!!.getResponse(listOf(input)).get().let { resp ->
                    return resp.response?.contentList[0]?.asText()
                }
            } catch (e: Exception) {
                log.error("调用模型生成图像描述失败", e)
                return null
            }
        }
    }
}