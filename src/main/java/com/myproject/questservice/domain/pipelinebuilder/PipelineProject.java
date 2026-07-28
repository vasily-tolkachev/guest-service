package com.myproject.questservice.domain.pipelinebuilder;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
public class PipelineProject {
    private UUID id;
    private String name;
    private Instant createdAt;
    private List<PipelineStage> stages;

    public PipelineProject(UUID id, String name, Instant createdAt, List<PipelineStage> stages) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.stages = stages == null ? new ArrayList<>() : stages;
    }

    public static PipelineProject create(String name) {
        return new PipelineProject(UUID.randomUUID(), name, Instant.now(), new ArrayList<>());
    }

    public Optional<PipelineStage> findStage(String stageId) {
        if (stageId == null) {
            return Optional.empty();
        }
        return stages.stream().filter(stage -> stageId.equalsIgnoreCase(stage.getId())).findFirst();
    }
}
