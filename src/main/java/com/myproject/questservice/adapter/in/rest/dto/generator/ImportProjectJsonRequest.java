package com.myproject.questservice.adapter.in.rest.dto.generator;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ImportProjectJsonRequest(
        @NotNull(message = "snapshotJson is required")
        JsonNode snapshotJson
) {
}
