package com.myproject.questservice.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class GameState {

    private String sessionId;
    private String questId;
    private String currentNodeId;
}
