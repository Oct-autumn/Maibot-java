package org.maibot.core.thinking;

import lombok.Setter;
import org.maibot.core.cdi.annotation.Component;

@Component(singleton = false)
public class ThinkingFlow {
    public enum ThinkingState {
        CREATED,
        ACTIVE,
        FOCUSED,
        SLEEPING
    }

    private final Long id;

    @Setter
    private ThinkingState state = ThinkingState.CREATED;

    public ThinkingFlow(Long id) {
        this.id = id;
    }
}
