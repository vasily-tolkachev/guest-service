package com.myproject.questservice.adapter.in.rest.dto;

public record StartQuestResponse(
        String sessionId,
        GameView game
) {
}
