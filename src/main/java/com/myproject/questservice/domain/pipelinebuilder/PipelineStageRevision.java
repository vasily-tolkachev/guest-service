package com.myproject.questservice.domain.pipelinebuilder;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record PipelineStageRevision(
        int revisionNumber,
        JsonNode outputJson,
        Instant createdAt,
        String systemPromptUsed,
        String userPromptUsed
) {
}
