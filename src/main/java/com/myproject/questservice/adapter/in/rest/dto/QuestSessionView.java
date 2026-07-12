package com.myproject.questservice.adapter.in.rest.dto;

public record QuestSessionView(
        String sessionId,
        String questId,
        String questTitle,
        String status,
        GameStateView gameState
) {
}
