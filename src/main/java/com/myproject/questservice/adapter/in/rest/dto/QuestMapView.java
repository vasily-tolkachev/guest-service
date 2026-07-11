package com.myproject.questservice.adapter.in.rest.dto;

import java.util.List;

public record QuestMapView(
        String currentNodeId,
        List<String> visited,
        List<String> available
) {
}
