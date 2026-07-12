package com.myproject.questservice.adapter.in.rest.dto;

import java.util.List;
import java.util.Map;

public record GameStateView(
        String currentNodeId,
        List<String> facts,
        Map<String, String> variables,
        List<String> inventory,
        List<String> visitedNodes,
        List<String> navigationHistory
) {
}
