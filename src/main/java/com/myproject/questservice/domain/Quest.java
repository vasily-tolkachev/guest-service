package com.myproject.questservice.domain;

import java.util.Map;

public record Quest(
        String id,
        String title,
        String startNodeId,
        Map<String, Node> nodes
) {
}
