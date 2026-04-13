package org.maibot.sdk.model

import org.maibot.sdk.config.ChoosableModel

abstract class ModelManager {
    protected abstract fun registerRequestTask(taskName: String, choosableModels: List<ChoosableModel>)

    protected abstract fun getRequestHandler(taskName: String): ModelRequestHandler?

    operator fun get(taskName: String): ModelRequestHandler? {
        return getRequestHandler(taskName)
    }

    operator fun set(taskName: String, choosableModels: List<ChoosableModel>) {
        registerRequestTask(taskName, choosableModels)
    }
}
