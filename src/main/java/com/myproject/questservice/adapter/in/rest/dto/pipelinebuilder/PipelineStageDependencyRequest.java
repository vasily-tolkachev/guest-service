package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

public record PipelineStageDependencyRequest(
        String stageId,
        String requiredStatus
) {
}
