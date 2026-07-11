package com.myproject.questservice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
public class GameState {

    private String sessionId;
    private String questId;
    private String currentNodeId;
    private Set<String> facts;
    private Map<String, String> variables;
    private Set<String> inventory;

    public GameState(String sessionId, String questId, String currentNodeId) {
        this(sessionId, questId, currentNodeId, new HashSet<>(), new HashMap<>(), new HashSet<>());
    }
}
