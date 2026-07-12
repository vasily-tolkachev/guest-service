package com.myproject.questservice.adapter.out.postgres.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "quest_session")
public class QuestSessionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "quest_id", nullable = false)
    private String questId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "game_state_json", nullable = false, columnDefinition = "text")
    private String gameStateJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
