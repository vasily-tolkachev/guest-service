package com.myproject.questservice.textruntime.domain.model;

public record RuntimeQuestDefinition(
        String id,
        String name,
        String description,
        World world,
        String startLocationId
) {
}
