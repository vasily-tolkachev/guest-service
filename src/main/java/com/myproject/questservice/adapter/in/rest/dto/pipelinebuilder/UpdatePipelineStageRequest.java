package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

import java.util.List;

public record UpdatePipelineStageRequest(
        String name,
        Boolean enabled,
        String systemPromptTemplate,
        String userPromptTemplate,
        Object args,
        String memoryMode,
        List<String> memorySources,
        List<PipelineStageDependencyRequest> dependencies
) {
}
