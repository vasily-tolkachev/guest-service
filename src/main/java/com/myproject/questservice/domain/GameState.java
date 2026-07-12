package com.myproject.questservice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GameState {

    private String currentNodeId;
    private Set<String> facts;
    private Map<String, String> variables;
    private Set<String> inventory;
    private Set<String> visitedNodes;
    private List<String> navigationHistory;

    public GameState(String currentNodeId) {
        this(
                currentNodeId,
                new HashSet<>(),
                new HashMap<>(),
                new HashSet<>(),
                new HashSet<>(),
                new ArrayList<>()
        );
    }
}
