package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

public record RunPipelineStageRequest(
        String systemPrompt,
        String userPrompt,
        Object args
) {
}
