package com.myproject.questservice.adapter.in.rest.dto.generator;

import java.time.Instant;

public record StageRevisionView(
        int revisionNumber,
        Object outputJson,
        Instant createdAt
) {
}

