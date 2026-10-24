package org.maibot.core.util

import org.maibot.sdk.model.APIResponse
import org.maibot.sdk.model.ModelConfig

/**
 * 模型调用成本计算（价格单位：元 / M-token）
 */
object ModelCostCalculator {
    fun calculate(model: ModelConfig, tokens: APIResponse.TokenStatistics?): Double {
        if (tokens == null) {
            return 0.0
        }
        return (tokens.inputTokens * model.priceIn + tokens.outputTokens * model.priceOut) / 1_000_000.0
    }
}
