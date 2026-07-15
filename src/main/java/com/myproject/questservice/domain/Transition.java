package com.myproject.questservice.domain;

import java.util.List;

public record Transition(
        String targetNodeId,
        List<Condition> conditions,
        List<Effect> effects
) {

    public Transition(String targetNodeId) {
        this(targetNodeId, List.of(), List.of());
    }

    public boolean isEnd() {
        return targetNodeId == null || targetNodeId.isBlank();
    }
}
