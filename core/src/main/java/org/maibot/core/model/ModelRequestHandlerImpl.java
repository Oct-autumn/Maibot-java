package org.maibot.core.model;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.Tool;
import org.maibot.sdk.TaskExecuteService;
import org.maibot.sdk.model.APIResponse;
import org.maibot.sdk.model.ModelConfig;
import org.maibot.sdk.model.ModelRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModelRequestHandlerImpl implements ModelRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(ModelRequestHandlerImpl.class);

    private final TaskExecuteService taskExecuteService;

    private final Map<String, OpenAIClient> providers;
    private final List<ModelConfig>         choosableModels;

    public ModelRequestHandlerImpl(
      TaskExecuteService taskExecuteService,
      Map<String, OpenAIClient> providers,
      List<ModelConfig> choosableModels
    ) {
        this.taskExecuteService = taskExecuteService;
        this.providers = providers;
        this.choosableModels = choosableModels;
    }

    public CompletableFuture<APIResponse> getResponse(
      ArrayList<ResponseInputItem> contextList,
      List<Tool> toolOptions,
      Integer maxTokens,
      Double temperature
//      RespFormat responseFormat,
//      StreamResponseHandler streamResponseHandler,
//      AsyncResponseParser asyncResponseParser,
//      InterruptFlag interruptFlag
    ) {
        // 为每次模型请求生成一个唯一ID，便于日志追踪
        var responseFuture = new CompletableFuture<APIResponse>();

        taskExecuteService.submit(
          () -> {
              MDC.put("ModelRequestId", String.valueOf(System.currentTimeMillis()));
              try {
                  for (var choosableModel : choosableModels) {
                      var client = providers.get(choosableModel.apiProvider());
                      var responseCreateParams = new ResponseCreateParams.Builder().model(choosableModel.modelIdentifier())
                        .inputOfResponse(contextList)
                        .temperature(temperature != null ? temperature : choosableModel.temperature())
                        .maxOutputTokens(maxTokens != null ? maxTokens : choosableModel.maxTokens())
                        .tools(toolOptions)
                        .build();

                      for (int retry = 0; retry <= choosableModel.maxRetry(); retry++) {
                          var resp = (Response) null;
                          try {
                              resp = client.responses().create(responseCreateParams);

                              if (resp.isValid()) {
                                  // 成功获取响应，记录日志并返回结果
                                  log.info("Model {} succeeded on attempt {}.", choosableModel.name(), retry);
                                  APIResponse.TokenStatistics tokenStats = null;
                                  if (resp.usage().isPresent()) {
                                      var usageStat = resp.usage().get();
                                      log.info(
                                        "Model {} usage - prompt tokens: {}, completion tokens: {}, total tokens: {}.",
                                        choosableModel.name(),
                                        usageStat.inputTokens(),
                                        usageStat.outputTokens(),
                                        usageStat.totalTokens()
                                      );

                                      tokenStats = new APIResponse.TokenStatistics(
                                        usageStat.inputTokens(),
                                        usageStat.outputTokens(),
                                        usageStat.totalTokens()
                                      );
                                  }
                                  responseFuture.complete(new APIResponse(resp, tokenStats));
                              } else {
                                  // 响应为null，记录警告日志并继续重试
                                  log.warn(
                                    "Model {} returned null response on attempt {}. Retrying...",
                                    choosableModel.name(),
                                    retry
                                  );
                              }
                          } catch (Exception e) {
                              if (retry == choosableModel.maxRetry()) {
                                  // 最后一次重试失败，记录日志并继续尝试下一个模型
                                  log.warn(
                                    "Model {} failed after {} attempts: {}. Moving to next model if available.",
                                    choosableModel.name(),
                                    retry,
                                    e.getMessage()
                                  );
                              } else {
                                  // 记录重试日志
                                  log.info(
                                    "Model {} attempt {} failed: {}. Retrying...",
                                    choosableModel.name(),
                                    retry,
                                    e.getMessage()
                                  );
                              }
                          }
                      }
                  }

                  responseFuture.completeExceptionally(new RuntimeException(
                    "All models failed to provide a valid response after maximum retries.")
                  );
              } finally {
                  // 无论请求成功与否，最后都要清理MDC中的请求ID，避免对后续请求造成干扰
                  MDC.remove("ModelRequestId"); // 请求结束后清除MDC中的请求ID
              }
          }, true
        );

        return responseFuture;
    }
}
