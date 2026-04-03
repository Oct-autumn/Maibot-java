package org.maibot.sdk.config

import com.fasterxml.jackson.annotation.JsonProperty

@JvmRecord
data class ModelApiConfig(
    @field:JsonProperty(value = "version", required = true) val version: String,
    @field:JsonProperty(value = "api_providers") val apiProviders: List<ApiProvider> = listOf(),
    @field:JsonProperty(value = "models") val models: List<Model> = listOf()
) {
    @JvmRecord
    data class ApiProvider(
        @field:JsonProperty(value = "name", required = true) val name: String,
        @field:JsonProperty(value = "base_url", required = true) val baseUrl: String,
        @field:JsonProperty(value = "api_key", required = true) val apiKey: String,
        @field:JsonProperty(value = "client_type") val clientType: String = "openai",
        @field:JsonProperty(value = "timeout") val timeout: Int = 60,
        @field:JsonProperty(value = "default_max_retry") val defaultMaxRetry: Int = 3,
        @field:JsonProperty(value = "default_temperature") val defaultTemperature: Double = 0.7,
        @field:JsonProperty(value = "default_max_tokens") val defaultMaxTokens: Int = 4096,
    )

    @JvmRecord
    data class Model(
        @field:JsonProperty(value = "model_identifier", required = true) val modelIdentifier: String,
        @field:JsonProperty(value = "api_provider", required = true) val apiProvider: String,
        @field:JsonProperty(value = "name") val name: String?,
        @field:JsonProperty(value = "price_in") val priceIn: Double = 0.0,
        @field:JsonProperty(value = "price_out") val priceOut: Double = 0.0,
        @field:JsonProperty(value = "max_retry") val maxRetry: Int? = null,
        @field:JsonProperty(value = "temperature") val temperature: Double? = null,
        @field:JsonProperty(value = "max_tokens") val maxTokens: Int? = null,
        @field:JsonProperty(value = "force_stream_mode") val forceStreamMode: Boolean = false,
        @field:JsonProperty(value = "enable_thinking") val enableThinking: Boolean = false
    )
}
