package com.myproject.questservice.adapter.out.inmemory;

import com.myproject.questservice.application.port.out.QuestSessionRepositoryPort;
import com.myproject.questservice.domain.GameState;
import com.myproject.questservice.domain.QuestSession;
import com.myproject.questservice.domain.QuestSessionStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("inmemory")
public class InMemorySessionStoreAdapter implements QuestSessionRepositoryPort {

    private final Map<UUID, QuestSession> sessions = new ConcurrentHashMap<>();

    @Override
    public QuestSession create(UUID userId, String questId, String startNodeId) {
        QuestSession session = new QuestSession(
                UUID.randomUUID(),
                userId,
                questId,
                QuestSessionStatus.ACTIVE,
                new GameState(startNodeId)
        );
        sessions.put(session.getId(), session);
        return session;
    }

    @Override
    public Optional<QuestSession> findById(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public Optional<QuestSession> findActive(UUID userId, String questId) {
        return sessions.values().stream()
                .filter(session -> session.getUserId().equals(userId))
                .filter(session -> session.getQuestId().equals(questId))
                .filter(session -> session.getStatus() == QuestSessionStatus.ACTIVE)
                .findFirst();
    }

    @Override
    public void save(QuestSession session) {
        sessions.put(session.getId(), session);
    }
}
