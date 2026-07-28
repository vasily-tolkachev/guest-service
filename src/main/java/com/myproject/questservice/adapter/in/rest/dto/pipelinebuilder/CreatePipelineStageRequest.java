package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

import java.util.List;

public record CreatePipelineStageRequest(
        String stageId,
        String name,
        String systemPromptTemplate,
        String userPromptTemplate,
        Object args,
        String memoryMode,
        List<String> memorySources,
        List<PipelineStageDependencyRequest> dependencies
) {
}
