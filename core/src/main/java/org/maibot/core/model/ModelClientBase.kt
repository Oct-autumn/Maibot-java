package org.maibot.core.model;


import org.maibot.sdk.config.ModelApiConfig;

public abstract class ModelClientBase {
    private final ModelApiConfig.ApiProvider apiProvider;

    protected ModelClientBase(ModelApiConfig.ApiProvider apiProvider) {
        this.apiProvider = apiProvider;
    }
}
