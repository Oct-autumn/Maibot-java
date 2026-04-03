package org.maibot.sdk.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 任务模型配置项，包含模型名称和参数覆写
 *
 * @param modelName       模型名称，需与 ModelConfig.Model.name 中定义的模型名称一致
 * @param temperature     温度参数，控制生成文本的随机程度，值越大越随机，通常在 0.0 到 1.0 之间
 * @param maxTokens       生成文本的最大长度，单位为 token，超过该长度后模型将停止生成
 * @param forceStreamMode 强制使用流式响应模式，适用于需要实时处理模型输出的任务，如对话生成、长文本生成等
 * @param enableThinking  启用思考（如果模型支持开关）
 */
@JvmRecord
data class ChoosableModel(
    @field:JsonProperty(value = "model_name") val modelName: String,
    @field:JsonProperty(value = "max_retry") val maxRetry: Int?,
    @field:JsonProperty(value = "temperature") val temperature: Double?,
    @field:JsonProperty(value = "max_tokens") val maxTokens: Int?,
    @field:JsonProperty(value = "force_stream_mode") val forceStreamMode: Boolean?,
    @field:JsonProperty(value = "enable_thinking") val enableThinking: Boolean?
)