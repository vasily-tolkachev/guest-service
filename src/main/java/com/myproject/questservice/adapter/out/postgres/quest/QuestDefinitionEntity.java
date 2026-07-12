package com.myproject.questservice.adapter.out.postgres.quest;

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
@Table(name = "quest_definition")
public class QuestDefinitionEntity {

    @Id
    private UUID id;

    @Column(name = "quest_id", nullable = false, unique = true)
    private String questId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "dsl", nullable = false, columnDefinition = "text")
    private String dsl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
