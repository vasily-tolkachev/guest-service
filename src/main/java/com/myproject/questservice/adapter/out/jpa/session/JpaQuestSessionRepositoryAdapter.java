package com.myproject.questservice.adapter.out.jpa.session;

import com.myproject.questservice.application.port.out.QuestSessionRepositoryPort;
import com.myproject.questservice.domain.GameState;
import com.myproject.questservice.domain.QuestSession;
import com.myproject.questservice.domain.QuestSessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JpaQuestSessionRepositoryAdapter implements QuestSessionRepositoryPort {

    private final QuestSessionJpaRepository repository;
    private final GameStateJsonMapper mapper;

    @Override
    public QuestSession create(UUID userId, String questId, String startNodeId) {
        return new QuestSession(
                UUID.randomUUID(),
                userId,
                questId,
                QuestSessionStatus.ACTIVE,
                new GameState(startNodeId)
        );
    }

    @Override
    public Optional<QuestSession> findById(UUID sessionId) {
        return repository.findById(sessionId).map(this::toDomain);
    }

    @Override
    public Optional<QuestSession> findActive(UUID userId, String questId) {
        return repository.findFirstByUserIdAndQuestIdAndStatus(userId, questId, QuestSessionStatus.ACTIVE.name())
                .map(this::toDomain);
    }

    @Override
    public List<QuestSession> findAllByUser(UUID userId) {
        return repository.findAllByUserId(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void save(QuestSession session) {
        Instant now = Instant.now();
        QuestSessionEntity entity = repository.findById(session.getId()).orElseGet(QuestSessionEntity::new);
        if (entity.getId() == null) {
            entity.setId(session.getId());
            entity.setStartedAt(now);
        }
        entity.setUserId(session.getUserId());
        entity.setQuestId(session.getQuestId());
        entity.setStatus(session.getStatus().name());
        entity.setGameState(mapper.toJsonNode(session.getGameState()));
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    @Override
    public void delete(UUID sessionId) {
        repository.deleteById(sessionId);
    }

    private QuestSession toDomain(QuestSessionEntity entity) {
        return new QuestSession(
                entity.getId(),
                entity.getUserId(),
                entity.getQuestId(),
                QuestSessionStatus.valueOf(entity.getStatus()),
                mapper.toDomain(entity.getGameState())
        );
    }
}
