package com.myproject.questservice.textruntime.domain.model;

import java.util.List;

public record Dialogue(
        String id,
        String startNodeId,
        List<Node> nodes
) {
    public record Node(
            String id,
            String text,
            List<Option> options,
            World.Effect effect
    ) {
    }

    public record Option(
            String text,
            String nextNodeId,
            World.Condition condition
    ) {
    }
}
