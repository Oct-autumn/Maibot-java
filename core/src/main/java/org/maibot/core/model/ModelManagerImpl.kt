package org.maibot.core.model

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import org.maibot.core.util.TaskExecuteServiceImpl
import org.maibot.sdk.config.ModelApiConfig
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.model.ChoosableModel
import org.maibot.sdk.model.ModelConfig
import org.maibot.sdk.model.ModelManager
import org.maibot.sdk.model.ModelRequestHandler
import java.time.Duration
import java.time.temporal.ChronoUnit

// TODO: 支持GeminiClient
@Component
class ModelManagerImpl
@AutoInject private constructor(
    @Value("choosableModels:*") config: ModelApiConfig,
    private val taskExecuteService: TaskExecuteServiceImpl
) : ModelManager() {
    /** API提供者名称与客户端实例的映射 */
    private val providers = HashMap<String, OpenAIClient>()

    /** 模型名称与模型配置的映射 */
    private val models = HashMap<String, ModelConfig>()

    /** 请求任务名与可选模型配置的映射 */
    private val taskModels = HashMap<String, MutableList<ModelConfig>>()

    init {
        val defaultMaxRetryMap = HashMap<String, Int>()
        val defaultTemperatureMap = HashMap<String, Double>()
        val defaultMaxTokensMap = HashMap<String, Int>()

        // 注册API提供者
        for (provider in config.apiProviders) {
            val client = OpenAIOkHttpClient.builder()
                .baseUrl(provider.baseUrl)
                .apiKey(provider.apiKey)
                .maxRetries(0) // 由调用方根据需要自行处理重试逻辑，避免库内自动重试导致的不可控行为
                .timeout(Duration.of(provider.timeout.toLong(), ChronoUnit.SECONDS))
                .build()
            this.providers[provider.name] = client

            // 存储API提供者的默认参数，供模型配置使用
            defaultMaxRetryMap[provider.name] = provider.defaultMaxRetry
            defaultTemperatureMap[provider.name] = provider.defaultTemperature
            defaultMaxTokensMap[provider.name] = provider.defaultMaxTokens
        }

        // 注册模型配置
        for (model in config.models) {
            require(providers.containsKey(model.apiProvider)) { "API provider ${model.apiProvider} not found for model: ${model.modelIdentifier}" }

            var keyToPut = model.name
            if (keyToPut.isNullOrBlank()) {
                keyToPut = model.modelIdentifier
            }
            if (keyToPut.isBlank()) {
                require(!keyToPut.isBlank()) { "Model must have a non-blank identifier" }
            }

            require(!this.models.containsKey(keyToPut)) { "Duplicate choosableModels name or identifier: $keyToPut" }

            val modifiedModel = ModelConfig(
                keyToPut,
                model.modelIdentifier,
                model.apiProvider,
                model.priceIn,
                model.priceOut,
                model.maxRetry ?: defaultMaxRetryMap[model.apiProvider]!!,
                model.temperature ?: defaultTemperatureMap[model.apiProvider]!!,
                model.maxTokens ?: defaultMaxTokensMap[model.apiProvider]!!,
                model.forceStreamMode,
                model.enableThinking
            )

            this.models[keyToPut] = modifiedModel
        }
    }

    override fun registerRequestTask(taskName: String, choosableModels: List<ChoosableModel>) {
        require(!taskModels.containsKey(taskName)) { "Duplicate task name: $taskName" }

        val modifiedChoosableModels = ArrayList<ModelConfig>()

        for (choosableModel in choosableModels) {
            val modelConfig: ModelConfig = this.models[choosableModel.modelName] ?: run {
                throw IllegalArgumentException("Model not found for choosableModel: ${choosableModel.modelName}")
            }

            val modifiedModel = ModelConfig(
                modelConfig.name,
                modelConfig.modelIdentifier,
                modelConfig.apiProvider,
                modelConfig.priceIn,
                modelConfig.priceOut,
                // 如果任务配置中指定了参数，则使用指定的参数；否则使用模型配置中的默认参数
                choosableModel.maxRetry ?: modelConfig.maxRetry,
                choosableModel.temperature ?: modelConfig.temperature,
                choosableModel.maxTokens ?: modelConfig.maxTokens,
                choosableModel.forceStreamMode ?: modelConfig.forceStreamMode,
                choosableModel.enableThinking ?: modelConfig.enableThinking
            )

            modifiedChoosableModels.add(modifiedModel)
        }

        taskModels[taskName] = modifiedChoosableModels
    }

    override fun getRequestHandler(taskName: String): ModelRequestHandler {
        val choosableModels: MutableList<ModelConfig> =
            taskModels[taskName] ?: throw IllegalArgumentException("No configuration found for task: $taskName")

        val apiProviderMap = HashMap<String, OpenAIClient>()
        for (model in choosableModels) {
            val apiProviderName = model.apiProvider
            apiProviderMap[apiProviderName] = providers[apiProviderName]
                ?: throw IllegalStateException("API provider $apiProviderName not found for model: ${model.modelIdentifier}")
        }

        return ModelRequestHandlerImpl(this.taskExecuteService, apiProviderMap, choosableModels)
    }
}
