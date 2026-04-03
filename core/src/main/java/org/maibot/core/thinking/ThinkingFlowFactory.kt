package org.maibot.core.thinking

import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.ObjectFactory
import org.maibot.sdk.ioc.Value

@ObjectFactory
class ThinkingFlowFactory @AutoInject private constructor(
    @param:Value($$"${thinking.observation_window_size}") private val observationWindowSize: Int
) {
    private var flowId: String? = null

    fun setFlowId(flowId: String): ThinkingFlowFactory {
        this.flowId = flowId
        return this
    }


    fun build(): ThinkingFlow {
        return ThinkingFlow(this.observationWindowSize, this.flowId)
    }
}
