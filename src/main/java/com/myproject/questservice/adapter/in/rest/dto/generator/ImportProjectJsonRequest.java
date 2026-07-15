package com.myproject.questservice.adapter.in.rest.dto.generator;

import jakarta.validation.constraints.NotNull;

public record ImportProjectJsonRequest(
        @NotNull(message = "snapshotJson is required")
        Object snapshotJson
) {
}
