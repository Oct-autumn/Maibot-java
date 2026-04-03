package org.maibot.core.model

import org.maibot.sdk.config.ModelApiConfig.ApiProvider


abstract class ModelClientBase protected constructor(private val apiProvider: ApiProvider?)
