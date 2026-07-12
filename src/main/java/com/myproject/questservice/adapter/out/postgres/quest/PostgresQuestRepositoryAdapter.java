package com.myproject.questservice.adapter.out.postgres.quest;

import com.myproject.questservice.application.port.out.QuestRepositoryPort;
import com.myproject.questservice.domain.QuestDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@Profile("!inmemory")
@RequiredArgsConstructor
public class PostgresQuestRepositoryAdapter implements QuestRepositoryPort {

    private final QuestDefinitionJpaRepository repository;

    @Override
    public List<QuestDefinition> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public java.util.Optional<QuestDefinition> findByQuestId(String questId) {
        return repository.findByQuestId(questId).map(this::toDomain);
    }

    @Override
    public void save(QuestDefinition questDefinition) {
        Instant now = Instant.now();
        QuestDefinitionEntity entity = repository.findByQuestId(questDefinition.questId())
                .orElseGet(QuestDefinitionEntity::new);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
            entity.setCreatedAt(now);
        }
        entity.setQuestId(questDefinition.questId());
        entity.setTitle(questDefinition.title());
        entity.setDsl(questDefinition.dsl());
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    private QuestDefinition toDomain(QuestDefinitionEntity entity) {
        return new QuestDefinition(entity.getQuestId(), entity.getTitle(), entity.getDsl());
    }
}
