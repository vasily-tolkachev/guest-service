package com.myproject.questservice.adapter.in.rest.dto.nodegenerator;

import jakarta.validation.constraints.NotNull;

public record ImportNodeGeneratorJsonRequest(
        @NotNull Object snapshotJson
) {
}
