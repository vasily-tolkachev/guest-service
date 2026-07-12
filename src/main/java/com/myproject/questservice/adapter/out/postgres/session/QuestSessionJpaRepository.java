package com.myproject.questservice.adapter.out.postgres.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuestSessionJpaRepository extends JpaRepository<QuestSessionEntity, UUID> {
    Optional<QuestSessionEntity> findFirstByUserIdAndQuestIdAndStatus(UUID userId, String questId, String status);
}
