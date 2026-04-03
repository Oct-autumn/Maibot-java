package org.maibot.core.model;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.maibot.core.util.TaskExecuteServiceImpl;
import org.maibot.sdk.config.ModelApiConfig;
import org.maibot.sdk.config.TaskModelConfig;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.Value;
import org.maibot.sdk.model.ModelConfig;
import org.maibot.sdk.model.ModelManager;
import org.maibot.sdk.model.ModelRequestHandler;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: 支持GeminiClient

@Component
public class ModelManagerImpl implements ModelManager {
    private final TaskExecuteServiceImpl taskExecuteService;

    /// API提供者名称与客户端实例的映射
    private final Map<String, OpenAIClient>      providers  = new HashMap<>();
    /// 模型名称与模型配置的映射
    private final Map<String, ModelConfig>       models     = new HashMap<>();
    /// 请求任务名与可选模型配置的映射
    private final Map<String, List<ModelConfig>> taskModels = new HashMap<>();

    @AutoInject
    private ModelManagerImpl(
      @Value("choosableModels:*") ModelApiConfig config,
      TaskExecuteServiceImpl taskExecuteService
    ) {
        this.taskExecuteService = taskExecuteService;
        var apiProviders = config.apiProviders();

        var defaultMaxRetryMap = new HashMap<String, Integer>();
        var defaultTemperatureMap = new HashMap<String, Double>();
        var defaultMaxTokensMap = new HashMap<String, Integer>();

        for (var provider : apiProviders) {
            var client = new OpenAIOkHttpClient.Builder().baseUrl(provider.baseUrl())
              .apiKey(provider.apiKey())
              .maxRetries(0) // 由调用方根据需要自行处理重试逻辑，避免库内自动重试导致的不可控行为
              .timeout(Duration.of(provider.timeout(), ChronoUnit.SECONDS))
              .build();
            this.providers.put(provider.name(), client);

            defaultMaxRetryMap.put(
              provider.name(),
              provider.defaultMaxRetry() != null ? provider.defaultMaxRetry() : 3
            );
            defaultTemperatureMap.put(
              provider.name(),
              provider.defaultTemperature() != null ? provider.defaultTemperature() : 0.7
            );
            defaultMaxTokensMap.put(
              provider.name(),
              provider.defaultMaxTokens() != null ? provider.defaultMaxTokens() : 1024
            );
        }

        var models = config.models();
        for (var model : models) {
            var apiProvider = this.providers.get(model.apiProvider());
            if (apiProvider == null) {
                throw new IllegalArgumentException("API provider not found for choosableModels: " + model.name());
            }

            String keyToPut = model.name();
            if (keyToPut.isBlank()) {
                keyToPut = model.modelIdentifier();
                if (keyToPut.isBlank()) {
                    throw new IllegalArgumentException("Model must have a non-blank name or choosableModels identifier");
                }
            }
            if (this.models.containsKey(keyToPut)) {
                throw new IllegalArgumentException("Duplicate choosableModels name or identifier: " + keyToPut);
            }

            var modifiedModel = new ModelConfig(
              keyToPut,
              model.modelIdentifier(),
              model.apiProvider(),
              model.priceIn(),
              model.priceOut(),
              model.maxRetry() != null ? model.maxRetry() : defaultMaxRetryMap.get(model.apiProvider()),
              model.temperature() != null ? model.temperature() : defaultTemperatureMap.get(model.apiProvider()),
              model.maxTokens() != null ? model.maxTokens() : defaultMaxTokensMap.get(model.apiProvider()),
              model.forceStreamMode() != null ? model.forceStreamMode() : false,
              model.enableThinking() != null ? model.enableThinking() : false
            );

            this.models.put(keyToPut, modifiedModel);
        }
    }

    /**
     * 注册请求任务配置
     *
     * @param taskConfig 任务模型配置，包含任务名称和可选模型列表，程序会根据任务名称获取对应的可选模型列表，并尝试使用这些模型进行调用
     */
    @Override
    public void registerRequestTask(TaskModelConfig taskConfig) {
        if (taskModels.containsKey(taskConfig.taskName())) {
            throw new IllegalArgumentException("Duplicate task name: " + taskConfig.taskName());
        }

        var modifiedChoosableModels = new ArrayList<ModelConfig>();

        for (var choosableModel : taskConfig.choosableModels()) {
            var modelConfig = this.models.get(choosableModel.modelName());
            if (modelConfig == null) {
                throw new IllegalArgumentException("Model not found for choosableModel: " + choosableModel.modelName());
            }

            var modifiedModel = new ModelConfig(
              modelConfig.name(),
              modelConfig.modelIdentifier(),
              modelConfig.apiProvider(),
              modelConfig.priceIn(),
              modelConfig.priceOut(),
              choosableModel.maxRetry() != null ? choosableModel.maxRetry() : modelConfig.maxRetry(),
              choosableModel.temperature() != null ? choosableModel.temperature() : modelConfig.temperature(),
              choosableModel.maxTokens() != null ? choosableModel.maxTokens() : modelConfig.maxTokens(),
              choosableModel.forceStreamMode() != null ? choosableModel.forceStreamMode() : modelConfig.forceStreamMode(),
              choosableModel.enableThinking() != null ? choosableModel.enableThinking() : modelConfig.enableThinking()
            );

            modifiedChoosableModels.add(modifiedModel);
        }

        taskModels.put(taskConfig.taskName(), modifiedChoosableModels);
    }

    /**
     * 获取任务需要的请求处理器
     *
     * @param taskName 任务名称，需与注册的任务名称一致
     * @return 请求处理器
     */
    @Override
    public ModelRequestHandler getRequestHandler(String taskName) {
        var choosableModels = taskModels.get(taskName);
        if (choosableModels == null) {
            throw new IllegalArgumentException("No configuration found for task: " + taskName);
        }

        var apiProviderMap = new HashMap<String, OpenAIClient>();
        for (var model : choosableModels) {
            var apiProviderName = model.apiProvider();
            if (!providers.containsKey(apiProviderName)) {
                throw new IllegalArgumentException("API provider not found for model: " + model.modelIdentifier());
            }
            apiProviderMap.put(apiProviderName, providers.get(apiProviderName));
        }

        return new ModelRequestHandlerImpl(this.taskExecuteService, apiProviderMap, choosableModels);
    }
}
