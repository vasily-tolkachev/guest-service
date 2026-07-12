package com.myproject.questservice.domain;

public record QuestDefinition(
        String questId,
        String title,
        String dsl
) {
}
