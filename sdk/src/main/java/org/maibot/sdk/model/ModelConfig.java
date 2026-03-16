package org.maibot.sdk.model;

/**
 * 模型配置类，包含模型标识、API提供商、价格信息和调用参数等
 *
 * @param name            模型名称，需与配置文件中定义的模型名称一致
 * @param modelIdentifier 模型标识，需与配置文件中定义的模型标识一致
 * @param apiProvider     API提供商名称，需与配置文件中定义的API提供商名称一致
 * @param priceIn         调用模型的输入价格，单位为元/M-token
 * @param priceOut        调用模型的输出价格，单位为元/M-token
 * @param maxRetry        模型调用失败后的最大重试次数，超过该次数后将放弃调用该模型
 * @param temperature     温度参数，控制生成文本的随机程度，值越大越随机，通常在 0.0 到 1.0 之间
 * @param maxTokens       生成文本的最大长度，单位为 token，超过该长度后模型将停止生成
 * @param forceStreamMode 强制使用流式响应模式，适用于需要实时处理模型输出的任务，如对话生成、长文本生成等
 * @param enableThinking  启用思考（如果模型支持开关），可以让模型在生成过程中进行内部思考，提升生成质量和准确性
 */
public record ModelConfig(
  String name,
  String modelIdentifier,
  String apiProvider,
  Double priceIn,
  Double priceOut,
  Integer maxRetry,
  Double temperature,
  Integer maxTokens,
  Boolean forceStreamMode,
  Boolean enableThinking
) {
}
