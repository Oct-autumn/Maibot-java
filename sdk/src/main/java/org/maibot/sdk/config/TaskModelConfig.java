package org.maibot.sdk.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 任务模型配置类，在配置文件中定义每个任务使用的模型及参数（覆写）
 *
 * @param taskName        任务名称，需与代码中定义的任务名称一致
 * @param choosableModels 任务使用的模型列表，按优先级顺序排列，程序会依次尝试使用列表中的模型进行调用，直到成功为止
 */
public record TaskModelConfig(
  @JsonProperty(value = "task_name") String taskName,
  @JsonProperty(value = "choosable_models") List<ChoosableModel> choosableModels
) {
    /**
     * 任务模型配置项，包含模型名称和参数覆写
     *
     * @param modelName       模型名称，需与 ModelConfig.Model.name 中定义的模型名称一致
     * @param temperature     温度参数，控制生成文本的随机程度，值越大越随机，通常在 0.0 到 1.0 之间
     * @param maxTokens       生成文本的最大长度，单位为 token，超过该长度后模型将停止生成
     * @param forceStreamMode 强制使用流式响应模式，适用于需要实时处理模型输出的任务，如对话生成、长文本生成等
     * @param enableThinking  启用思考（如果模型支持开关）
     */
    public record ChoosableModel(
      @JsonProperty(value = "model_name") String modelName,
      @JsonProperty(value = "max_retry") Integer maxRetry,
      @JsonProperty(value = "temperature") Double temperature,
      @JsonProperty(value = "max_tokens") Integer maxTokens,
      @JsonProperty(value = "force_stream_mode") Boolean forceStreamMode,
      @JsonProperty(value = "enable_thinking") Boolean enableThinking
    ) {
    }
}