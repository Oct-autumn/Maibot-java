package org.maibot.core.model

import org.maibot.core.ioc.Instance
import org.maibot.core.util.TaskExecuteServiceImpl
import org.maibot.sdk.config.ChoosableModel
import org.maibot.sdk.config.ModelApiConfig
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.Value
import org.maibot.sdk.model.*

// TODO: 支持GeminiClient
@Component
class ModelManagerImpl
@AutoInject private constructor(
    @Value($$"${choosableModels:*}") config: ModelApiConfig,
    private val taskExecuteService: TaskExecuteServiceImpl
) : ModelManager() {
    /** API提供者名称与客户端实例的映射 */
    private val providers = HashMap<String, ModelClientBase>()

    /** 模型名称与模型配置的映射 */
    private val models = HashMap<String, ModelConfig>()

    /** 请求任务名与可选模型配置的映射 */
    private val taskModels = HashMap<String, MutableList<ModelConfig>>()

    init {
        val defaultRetryDelayBase = HashMap<String, Long>()
        val defaultMaxRetryMap = HashMap<String, Int>()
        val defaultTemperatureMap = HashMap<String, Double>()
        val defaultMaxTokensMap = HashMap<String, Long>()

        // 注册API提供者
        for (provider in config.apiProviders) {
            val client = Instance.get(ModelClientFactory::class.java, provider.clientType)
                .baseUrl(provider.baseUrl)
                .apiKey(provider.apiKey)
                .connectTimeout(provider.connectTimeout.toLong())
                .build()

            this.providers[provider.name] = client

            // 存储API提供者的默认参数，供模型配置使用
            defaultRetryDelayBase[provider.name] = provider.retryDelayBase
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
                model.retryDelayBase ?: defaultRetryDelayBase[model.apiProvider]!!,
                model.maxRetry ?: defaultMaxRetryMap[model.apiProvider]!!,
                model.temperature ?: defaultTemperatureMap[model.apiProvider]!!,
                model.maxTokens ?: defaultMaxTokensMap[model.apiProvider]!!,
                model.enableThinking,
                model.forceStreamMode
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
                choosableModel.retryDelayBase ?: modelConfig.retryDelayBase,
                choosableModel.maxRetry ?: modelConfig.maxRetry,
                choosableModel.temperature ?: modelConfig.temperature,
                choosableModel.maxTokens ?: modelConfig.maxTokens,
                choosableModel.enableThinking ?: modelConfig.enableThinking,
                choosableModel.forceStreamMode ?: modelConfig.forceStreamMode
            )

            modifiedChoosableModels.add(modifiedModel)
        }

        taskModels[taskName] = modifiedChoosableModels
    }

    override fun getRequestHandler(taskName: String): ModelRequestHandler {
        val choosableModels: MutableList<ModelConfig> =
            taskModels[taskName] ?: throw IllegalArgumentException("No configuration found for task: $taskName")

        val apiProviderMap = HashMap<String, ModelClientBase>()
        for (model in choosableModels) {
            val apiProviderName = model.apiProvider
            apiProviderMap[apiProviderName] = providers[apiProviderName]
                ?: throw IllegalStateException("API provider $apiProviderName not found for model: ${model.modelIdentifier}")
        }

        return ModelRequestHandlerImpl(this.taskExecuteService, apiProviderMap, choosableModels)
    }
}
