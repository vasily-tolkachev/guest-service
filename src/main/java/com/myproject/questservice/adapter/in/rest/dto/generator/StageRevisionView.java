package com.myproject.questservice.adapter.in.rest.dto.generator;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record StageRevisionView(
        int revisionNumber,
        JsonNode outputJson,
        Instant createdAt
) {
}

