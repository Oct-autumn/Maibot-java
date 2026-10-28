package org.maibot.core.thinking.tool

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyDescription

/**
 * `speak` 工具参数
 *
 * 只传发言意图，不传正文；正文由 SpeechGenerator 根据观察窗口生成。
 * 使用 public field 是为了让 OpenAI client 的 schema 反射能读到。
 */
class SpeakParams {
    @JvmField
    @JsonProperty(value = "intent", required = true)
    @JsonPropertyDescription("想说什么的大体方向，例如“回应对方刚才的提问”“主动开启一个轻松的话题”。")
    var intent: String = ""
}
