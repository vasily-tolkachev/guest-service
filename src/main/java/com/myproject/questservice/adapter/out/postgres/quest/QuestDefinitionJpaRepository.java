package com.myproject.questservice.adapter.out.postgres.quest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuestDefinitionJpaRepository extends JpaRepository<QuestDefinitionEntity, UUID> {
    Optional<QuestDefinitionEntity> findByQuestId(String questId);
}
