package org.maibot.core.thinking;

import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.ObjectFactory;
import org.maibot.core.cdi.annotation.Value;

@ObjectFactory
public class ThinkingFlowFactory {
    private final int observationWindowSize;

    private String flowId;

    @AutoInject
    private ThinkingFlowFactory(
            @Value("thinking.observation_window_size") int observationWindowSize
    ) {
        this.observationWindowSize = observationWindowSize;
    }

    public ThinkingFlowFactory setFlowId(String flowId) {
        this.flowId = flowId;
        return this;
    }


    public ThinkingFlow build() {
        return new ThinkingFlow(
                this.observationWindowSize,
                this.flowId
        );
    }
}
