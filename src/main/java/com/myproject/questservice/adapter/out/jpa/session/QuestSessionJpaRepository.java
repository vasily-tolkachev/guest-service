package com.myproject.questservice.adapter.out.jpa.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuestSessionJpaRepository extends JpaRepository<QuestSessionEntity, UUID> {
    Optional<QuestSessionEntity> findFirstByUserIdAndQuestIdAndStatus(UUID userId, String questId, String status);

    java.util.List<QuestSessionEntity> findAllByUserId(UUID userId);
}
