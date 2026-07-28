package com.myproject.questservice.domain.pipelinebuilder;

public record PipelineStageDependency(
        String stageId,
        PipelineStageStatus requiredStatus
) {
}
