package com.myproject.questservice.adapter.in.rest.dto;

import java.util.List;
import java.util.Map;

public record GameView(
        String title,
        String nodeId,
        String nodeTitle,
        String text,
        List<OptionView> options,
        List<String> inventory,
        Map<String, String> variables,
        List<String> visitedNodes,
        boolean canGoBack,
        boolean finished
) {
}
