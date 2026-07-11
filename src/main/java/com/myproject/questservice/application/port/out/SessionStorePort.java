package com.myproject.questservice.application.port.out;

import com.myproject.questservice.domain.GameState;

import java.util.Optional;

public interface SessionStorePort {

    String create(String questId, String startNodeId);

    Optional<GameState> findById(String sessionId);
}
