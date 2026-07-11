package com.myproject.questservice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
public class GameState {

    private String sessionId;
    private String questId;
    private String currentNodeId;
    private Set<String> facts;

    public GameState(String sessionId, String questId, String currentNodeId) {
        this(sessionId, questId, currentNodeId, new HashSet<>());
    }
}
