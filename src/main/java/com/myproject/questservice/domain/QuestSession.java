package com.myproject.questservice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
public class QuestSession {
    private UUID id;
    private UUID userId;
    private String questId;
    private QuestSessionStatus status;
    private GameState gameState;
}
