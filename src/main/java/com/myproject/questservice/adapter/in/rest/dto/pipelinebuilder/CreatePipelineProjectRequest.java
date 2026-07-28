package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

import jakarta.validation.constraints.NotBlank;

public record CreatePipelineProjectRequest(
        @NotBlank String name
) {
}
