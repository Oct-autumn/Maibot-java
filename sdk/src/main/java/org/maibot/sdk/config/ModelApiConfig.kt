package org.maibot.sdk.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ModelApiConfig(
  @JsonProperty(value = "version", required = true) String version,
  @JsonProperty(value = "api_providers") List<ApiProvider> apiProviders,
  @JsonProperty(value = "models") List<Model> models
) {
    public record ApiProvider(
      @JsonProperty(value = "name", required = true) String name,
      @JsonProperty(value = "base_url", required = true) String baseUrl,
      @JsonProperty(value = "api_key", required = true) String apiKey,
      @JsonProperty(value = "client_type") String clientType,
      @JsonProperty(value = "timeout") Integer timeout,
      @JsonProperty(value = "default_max_retry") Integer defaultMaxRetry,
      @JsonProperty(value = "default_temperature") Double defaultTemperature,
      @JsonProperty(value = "default_max_tokens") Integer defaultMaxTokens
    ) {
    }

    public record Model(
      @JsonProperty(value = "model_identifier", required = true) String modelIdentifier,
      @JsonProperty(value = "name", required = true) String name,
      @JsonProperty(value = "api_provider", required = true) String apiProvider,
      @JsonProperty(value = "price_in") Double priceIn,
      @JsonProperty(value = "price_out") Double priceOut,
      @JsonProperty(value = "max_retry") Integer maxRetry,
      @JsonProperty(value = "temperature") Double temperature,
      @JsonProperty(value = "max_tokens") Integer maxTokens,
      @JsonProperty(value = "force_stream_mode") Boolean forceStreamMode,
      @JsonProperty(value = "enable_thinking") Boolean enableThinking
    ) {
    }
}
