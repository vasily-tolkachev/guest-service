package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

import java.util.List;

public record PipelineStageView(
        String id,
        String name,
        boolean enabled,
        String systemPromptTemplate,
        String userPromptTemplate,
        Object args,
        String memoryMode,
        List<String> memorySources,
        List<PipelineStageDependencyRequest> dependencies,
        String status,
        boolean approved,
        PipelineStageRevisionView currentRevision
) {
}
