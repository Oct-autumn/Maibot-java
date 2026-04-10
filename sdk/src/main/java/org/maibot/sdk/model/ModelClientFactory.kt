package org.maibot.sdk.model

interface ModelClientFactory {
    fun baseUrl(baseURL: String): ModelClientFactory

    fun apiKey(apiKey: String): ModelClientFactory

    fun connectTimeout(timeout: Long): ModelClientFactory

    fun build(): ModelClientBase
}