package com.myproject.questservice.domain.generator;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record StageRevision(
        int revisionNumber,
        JsonNode outputJson,
        Instant createdAt
) {
}

