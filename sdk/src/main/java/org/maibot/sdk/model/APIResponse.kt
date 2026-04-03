package org.maibot.sdk.model;

import com.openai.models.responses.Response;

public record APIResponse(
  Response response,
  TokenStatistics tokenStatistics
) {
    public record TokenStatistics(
      long inputTokens,
      long outputTokens,
      long totalTokens
    ) {
    }
}
