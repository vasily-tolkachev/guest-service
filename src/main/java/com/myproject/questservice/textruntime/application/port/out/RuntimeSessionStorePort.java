package com.myproject.questservice.textruntime.application.port.out;

import com.myproject.questservice.textruntime.domain.service.GameEngine;

import java.util.Optional;
import java.util.UUID;

public interface RuntimeSessionStorePort {
    void save(UUID sessionId, GameEngine engine);

    Optional<GameEngine> find(UUID sessionId);
}
