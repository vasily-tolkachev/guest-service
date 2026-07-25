package com.myproject.questservice.textruntime.adapter.out.inmemory;

import com.myproject.questservice.textruntime.application.port.out.RuntimeSessionStorePort;
import com.myproject.questservice.textruntime.domain.service.GameEngine;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRuntimeSessionStoreAdapter implements RuntimeSessionStorePort {
    private final Map<UUID, GameEngine> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(UUID sessionId, GameEngine engine) {
        sessions.put(sessionId, engine);
    }

    @Override
    public Optional<GameEngine> find(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }
}
