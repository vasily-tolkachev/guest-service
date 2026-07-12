package com.myproject.questservice.application.port.out;

import com.myproject.questservice.domain.QuestSession;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestSessionRepositoryPort {

    QuestSession create(UUID userId, String questId, String startNodeId);

    Optional<QuestSession> findById(UUID sessionId);

    Optional<QuestSession> findActive(UUID userId, String questId);

    List<QuestSession> findAllByUser(UUID userId);

    void save(QuestSession session);

    void delete(UUID sessionId);
}
