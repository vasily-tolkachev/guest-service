package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PipelineProjectView(
        UUID id,
        String name,
        Instant createdAt,
        List<PipelineStageView> stages
) {
}
