package org.maibot.core.model.oaiclassic

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.core.Timeout
import com.openai.models.FunctionDefinition
import com.openai.models.FunctionParameters
import com.openai.models.chat.completions.*
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam.Content.ChatCompletionRequestAssistantMessageContentPart
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.exceptions.ModelRequestFailed
import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.ModelClientBase
import org.maibot.sdk.model.payload.AvailableFunctionItem
import org.maibot.sdk.model.payload.MessageContextItem
import org.maibot.sdk.util.MapperUtils
import org.slf4j.LoggerFactory.getLogger
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.io.encoding.Base64
import kotlin.jvm.optionals.getOrNull

/**
 * 对OpenAI API的客户端重封装，提供统一的接口以供ModelRequestHandler调用。
 */
class OpenAIClassicClientReCap : ModelClientBase {
    private val client: OpenAIClient

    constructor(
        baseURL: String,
        apiKey: String,
        connectTimeout: Long,
    ) : super(baseURL, apiKey, connectTimeout) {
        val timeoutConfig = Timeout.builder()
            .connect(Duration.ofSeconds(connectTimeout))
            .read(Duration.ofSeconds(connectTimeout))
            .write(Duration.ofSeconds(connectTimeout))
            .request(Duration.ZERO)  // 请求超时设置为0，不限制整个请求的总耗时
            .build()

        client = OpenAIOkHttpClient.builder()
            .baseUrl(baseURL)
            .apiKey(apiKey)
            .timeout(timeoutConfig)
            .maxRetries(1) // 由当前重封装层控制重试逻辑，避免库内自动重试导致的不可控行为
            .build()
    }

    override fun getResponse(
        modelIdentifier: String,
        messageContext: List<MessageContextItem>,
        availableFunc: List<AvailableFunctionItem>?,
        formatClass: Class<*>?,
        retryDelayBase: Long,
        maxRetry: Int,
        maxTokens: Long,
        temperature: Double,
        enableThinking: Boolean,
        forceStreamMode: Boolean
    ): APIResponse {
        val params = ChatCompletionCreateParams.builder().apply {
            model(modelIdentifier)
            messages(messageContext.map { toChatCompletionMessageParam(it) })
            maxCompletionTokens(maxTokens)
            temperature(temperature)
            putAdditionalBodyProperty("enable_thinking", JsonValue.from(enableThinking))

            // 可选字段
            availableFunc?.let { tools ->
                {
                    tools(tools.map { toChatCompletionTool(it) })
                }
            }
            formatClass?.let {
                responseFormat(it)
            }
        }.build()

        var delayTime = retryDelayBase

        for (attempt in 0..maxRetry) {
            try {
                val resp = client.chat().completions().create(params)
                resp.validate()
                // 提取响应内容并封装成APIResponse对象
                return toAPIResponse(resp)
            } catch (e: Exception) {
                if (attempt == maxRetry) {
                    // 达到最大重试次数，返回异常
                    throw ModelRequestFailed("Failed to get response from model after $maxRetry attempts", e)
                }
                Thread.sleep(retryDelayBase)
                delayTime *= 2  // 指数级增加重试间隔时间
            }
        }

        // 理论上不应该到达这里，因为循环内要么成功返回，要么抛出异常
        throw FatalError("Unexpected error in getResponse: exceeded max retry attempts without throwing exception")
    }

    override fun getEmbedding(
        modelIdentifier: String,
        input: List<String>,
        embeddingFuture: CompletableFuture<List<List<Double>>>
    ) {
        TODO("Not yet implemented")
    }

    /**
     * 将MessageContext转换为ChatCompletionMessageParam，适配OpenAI API的输入格式
     */
    private fun toChatCompletionMessageParam(messageContextItem: MessageContextItem): ChatCompletionMessageParam {

        fun contentToUserPart(content: MessageContextItem.Content): ChatCompletionContentPart? {
            when {
                content.isText() -> {
                    val textContent = content.asText()!!
                    return ChatCompletionContentPart.ofText(
                        ChatCompletionContentPartText.builder()
                            .text(textContent)
                            .build()
                    )
                }

                content.isImage() -> {
                    val imageContent = content.asImage()!!
                    val imageUrl =
                        "data:image/${imageContent.format};base64,${Base64.encode(imageContent.data)}"
                    return ChatCompletionContentPart.ofImageUrl(
                        ChatCompletionContentPartImage.builder()
                            .imageUrl(
                                ChatCompletionContentPartImage.ImageUrl.builder()
                                    .url(imageUrl)
                                    .detail(
                                        when (imageContent.detail) {
                                            MessageContextItem.Content.Detail.AUTO -> ChatCompletionContentPartImage.ImageUrl.Detail.AUTO
                                            MessageContextItem.Content.Detail.LOW -> ChatCompletionContentPartImage.ImageUrl.Detail.LOW
                                            MessageContextItem.Content.Detail.HIGH -> ChatCompletionContentPartImage.ImageUrl.Detail.HIGH
                                        }
                                    )
                                    .build()
                            )
                            .build()

                    )
                }

                else -> {
                    // 其他类型暂不支持，忽略
                    log.warn("不支持的User内容类型, 已忽略")
                    return null
                }
            }
        }

        fun contentToAssistantTextPart(content: MessageContextItem.Content): ChatCompletionRequestAssistantMessageContentPart? {
            when {
                content.isText() -> {
                    val textContent = content.asText()!!
                    return ChatCompletionRequestAssistantMessageContentPart.ofText(
                        ChatCompletionContentPartText.builder()
                            .text(textContent)
                            .build()
                    )
                }

                else -> {
                    // 其他类型暂不支持，忽略
                    log.warn("不支持的Assistant内容类型, 已忽略")
                    return null
                }
            }
        }

        fun contentToToolPart(content: MessageContextItem.Content): ChatCompletionContentPartText? {
            when {
                content.isText() -> {
                    val textContent = content.asText()!!
                    return ChatCompletionContentPartText.builder()
                        .text(textContent)
                        .build()
                }

                else -> {
                    // 其他类型暂不支持，忽略
                    log.warn("不支持的Tool内容类型, 已忽略")
                    return null
                }
            }
        }

        when (messageContextItem.role) {
            MessageContextItem.Role.SYSTEM -> {
                // 系统消息只包含一条文本内容
                val messageParamBuilder = ChatCompletionSystemMessageParam.builder()
                messageContextItem.contentList.getOrNull(0)!!.let {
                    val textContent = it.asText()!!
                    messageParamBuilder.content(textContent)
                }

                return ChatCompletionMessageParam.ofSystem(messageParamBuilder.build())
            }

            MessageContextItem.Role.USER -> {
                val messageParamBuilder = ChatCompletionUserMessageParam.builder()
                val contentParts = messageContextItem.contentList.mapNotNull { contentToUserPart(it) }
                messageParamBuilder.contentOfArrayOfContentParts(contentParts)

                return ChatCompletionMessageParam.ofUser(messageParamBuilder.build())
            }

            MessageContextItem.Role.ASSISTANT -> {
                val messageParamBuilder = ChatCompletionAssistantMessageParam.builder()
                val contentParts = messageContextItem.contentList.mapNotNull { contentToAssistantTextPart(it) }
                messageParamBuilder.contentOfArrayOfContentParts(contentParts)

                return ChatCompletionMessageParam.ofAssistant(messageParamBuilder.build())
            }

            MessageContextItem.Role.TOOL -> {
                val messageParamBuilder = ChatCompletionToolMessageParam.builder()
                val contentParts = messageContextItem.contentList.mapNotNull { contentToToolPart(it) }
                messageParamBuilder.contentOfArrayOfContentParts(contentParts)
                messageParamBuilder.toolCallId(messageContextItem.toolCallId!!)

                return ChatCompletionMessageParam.ofTool(messageParamBuilder.build())
            }
        }
    }

    /**
     * 将AvailableToolItem转换为ChatCompletionTool，适配OpenAI API的工具调用格式
     */
    private fun toChatCompletionTool(availableFunctionItem: AvailableFunctionItem): ChatCompletionTool {
        val funcParam = availableFunctionItem.params?.let {
            FunctionParameters.builder().apply {
                putAdditionalProperty("type", JsonValue.from("object"))

                val requiredParam = mutableListOf<String>()

                val properties = mutableMapOf<String, JsonValue>()

                it.fields.forEach { paramField ->
                    // 获取@JsonProperty注解
                    val jPropAnn = paramField.getAnnotation(JsonProperty::class.java)
                    // 获取@JsonPropertyDescription注解
                    val jPropDescAnn = paramField.getAnnotation(JsonPropertyDescription::class.java)

                    // 参数名称优先使用@JsonProperty注解指定的名称，如果没有则使用字段名
                    val paramName = jPropAnn?.value ?: paramField.name
                    if (jPropAnn != null && jPropAnn.required) {
                        requiredParam.add(paramName)
                    }
                    // 构建参数属性的JSON对象
                    val paramSchema = mutableMapOf<String, JsonValue>()
                    // 仅作基本类型支持
                    when (paramField.type) {
                        String::class.java -> paramSchema["type"] = JsonValue.from("string")
                        Int::class.java, Integer::class.java -> paramSchema["type"] = JsonValue.from("integer")
                        Long::class.java, java.lang.Long::class.java -> paramSchema["type"] = JsonValue.from("integer")
                        Float::class.java, java.lang.Float::class.java,
                        Double::class.java, java.lang.Double::class.java -> paramSchema["type"] =
                            JsonValue.from("float")

                        Boolean::class.java, java.lang.Boolean::class.java -> paramSchema["type"] =
                            JsonValue.from("boolean")

                        else -> {
                            log.warn("不支持的参数类型: ${paramField.type}, 已忽略该参数")
                            return@forEach
                        }
                    }

                    jPropDescAnn?.let { desc ->
                        paramSchema["description"] = JsonValue.from(desc.value)
                    }

                    properties[paramName] = JsonValue.from(paramSchema)
                }

                putAdditionalProperty(
                    "properties", JsonValue.from(
                        properties
                    )
                )
            }.build()
        }

        val funcDef = FunctionDefinition.builder().apply {
            name(availableFunctionItem.name)
            description(availableFunctionItem.description)

            funcParam?.let {
                parameters(funcParam)
            }
        }.build()


        return ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder()
                .function(funcDef)
                //.type(JsonValue.from("function"))   // This field is default
                .build()
        )
    }

    /**
     * 将ChatCompletion响应转换为APIResponse，提取其中的思考内容、最终输出、工具调用信息和token统计等
     */
    private fun toAPIResponse(resp: ChatCompletion): APIResponse {
        val choice = resp.choices().firstOrNull() ?: run {
            log.warn("模型返回了0条结果")
            throw IllegalStateException("None choice returned from model")
        }
        // 有两种可能：
        // 1) 正常响应，“content”为正常输出，additionalContent中“reasoning_content”为思考内容
        // 2) 思考内容和最终输出混在一起，通过<think>标签区分
        // 优先使用第一种方式，如果没有思考内容则尝试第二种方式，如果两者都没有则认为没有思考内容
        var reasoningContent = choice.message()._additionalProperties()["reasoning_content"]?.asString()?.getOrNull()
        var responseContent = choice.message().content().getOrNull()

        if (reasoningContent == null) {
            val matchResult = REASONING_CAPTURE_REGEX.find(responseContent!!)
            if (matchResult != null) {
                reasoningContent = matchResult.groups["thinking"]?.value
                responseContent = matchResult.groups["response"]?.value
            }
        }

        val response = responseContent?.let {
            MessageContextItem.ofAssistant(
                listOf(
                    MessageContextItem.Content.ofText(it)
                )
            )
        }

        val toolCalls = choice.message().toolCalls().getOrNull()?.mapNotNull { toolCall ->
            val functionCall = toolCall.function().getOrNull() ?: return@mapNotNull null
            
            // 解析Args
            val args = functionCall.function().arguments()
            val argJsonNode = MapperUtils.jsonMapper.readTree(args)



            APIResponse.ToolCallInfo(
                functionCall.function().name(),
                argJsonNode,
                functionCall.id()
            )
        } ?: emptyList()

        val usage = resp.usage().getOrNull()?.let {
            APIResponse.TokenStatistics(
                it.promptTokens(),
                it.completionTokens(),
                it.totalTokens()
            )
        }

        return APIResponse(
            resp,
            reasoningContent,
            response,
            toolCalls,
            usage
        )
    }

    companion object {
        private val log = getLogger(OpenAIClassicClientReCap::class.java)

        private val REASONING_CAPTURE_REGEX =
            Regex("^(<think>(?<thinking>[\\s\\S]*?)</think>)?(?<response>[\\s\\S]*?)$")
    }
}