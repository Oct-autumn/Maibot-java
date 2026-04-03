package org.maibot.sdk.model;

import org.maibot.sdk.config.TaskModelConfig;

public interface ModelManager {

    void registerRequestTask(TaskModelConfig taskConfig);

    ModelRequestHandler getRequestHandler(String taskName);
}
