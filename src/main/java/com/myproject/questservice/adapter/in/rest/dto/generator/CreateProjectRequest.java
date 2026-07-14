package com.myproject.questservice.adapter.in.rest.dto.generator;

import jakarta.validation.constraints.NotBlank;

public record CreateProjectRequest(
        @NotBlank(message = "Project name is required")
        String name
) {
}

