package org.maibot.sdk.config

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 任务模型配置项，包含模型名称和参数覆写
 *
 * @param modelName       模型名称，需与 ModelConfig.Model.name 中定义的模型名称一致
 * @param retryDelayBase  模型调用失败后的重试间隔时间，单位为毫秒，超过该时间后将再次尝试调用该模型
 * @param maxRetry        模型调用失败后的最大重试次数，超过该次数后将放弃调用该模型
 * @param temperature     温度参数，控制生成文本的随机程度，值越大越随机，通常在 0.0 到 1.0 之间
 * @param maxTokens       生成文本的最大长度，单位为 token，超过该长度后模型将停止生成
 * @param enableThinking  启用思考（如果模型支持开关）
 * @param forceStreamMode 强制使用流式响应模式，适用于需要实时处理模型输出的任务，如对话生成、长文本生成等
 */
@JvmRecord
data class ChoosableModel(
    @field:JsonProperty(value = "model_name", required = true) val modelName: String,
    @field:JsonProperty(value = "retry_delay_base") val retryDelayBase: Long? = null,
    @field:JsonProperty(value = "max_retry") val maxRetry: Int? = null,
    @field:JsonProperty(value = "temperature") val temperature: Double? = null,
    @field:JsonProperty(value = "max_tokens") val maxTokens: Long? = null,
    @field:JsonProperty(value = "enable_thinking") val enableThinking: Boolean? = null,
    @field:JsonProperty(value = "force_stream_mode") val forceStreamMode: Boolean? = null,
)

