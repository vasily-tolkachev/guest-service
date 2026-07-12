package com.myproject.questservice.application.port.out;

import com.myproject.questservice.domain.QuestSession;

import java.util.Optional;
import java.util.UUID;

public interface QuestSessionRepositoryPort {

    QuestSession create(UUID userId, String questId, String startNodeId);

    Optional<QuestSession> findById(UUID sessionId);

    Optional<QuestSession> findActive(UUID userId, String questId);

    void save(QuestSession session);
}
