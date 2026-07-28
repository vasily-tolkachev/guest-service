package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

import java.time.Instant;

public record PipelineStageRevisionView(
        int revisionNumber,
        Object outputJson,
        Instant createdAt,
        String systemPromptUsed,
        String userPromptUsed
) {
}
