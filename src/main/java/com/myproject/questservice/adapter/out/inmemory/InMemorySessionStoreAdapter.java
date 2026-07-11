package com.myproject.questservice.adapter.out.inmemory;

import com.myproject.questservice.application.port.out.SessionStorePort;
import com.myproject.questservice.domain.GameState;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemorySessionStoreAdapter implements SessionStorePort {

    private final Map<String, GameState> sessions = new ConcurrentHashMap<>();

    @Override
    public String create(String questId, String startNodeId) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new GameState(sessionId, questId, startNodeId));
        return sessionId;
    }

    @Override
    public Optional<GameState> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }
}
