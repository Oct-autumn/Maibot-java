package org.maibot.sdk.model;

import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ModelRequestHandler {
    CompletableFuture<APIResponse> getResponse(
      ArrayList<ResponseInputItem> contextList,
      List<Tool> toolOptions,
      Integer maxTokens,
      Double temperature
//      RespFormat responseFormat,
//      StreamResponseHandler streamResponseHandler,
//      AsyncResponseParser asyncResponseParser,
//      InterruptFlag interruptFlag
    );
}
