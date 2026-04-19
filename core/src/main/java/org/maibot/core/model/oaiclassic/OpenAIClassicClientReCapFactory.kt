package org.maibot.core.model.oaiclassic

import org.maibot.sdk.ioc.ObjectFactory
import org.maibot.sdk.ioc.Specify
import org.maibot.sdk.model.ModelClientBase
import org.maibot.sdk.model.ModelClientFactory

@ObjectFactory
@Specify("OpenAIClassic")
class OpenAIClassicClientReCapFactory : ModelClientFactory {
    var baseURL: String = ""
        private set
    var apiKey: String = ""
        private set
    var connectTimeout: Long = 0
        private set

    override fun baseUrl(baseURL: String): OpenAIClassicClientReCapFactory {
        this.baseURL = baseURL
        return this
    }

    override fun apiKey(apiKey: String): OpenAIClassicClientReCapFactory {
        this.apiKey = apiKey
        return this
    }

    override fun connectTimeout(timeout: Long): OpenAIClassicClientReCapFactory {
        this.connectTimeout = timeout
        return this
    }

    override fun build(): ModelClientBase {
        return OpenAIClassicClientReCap(
            this.baseURL,
            this.apiKey,
            this.connectTimeout
        )
    }
}