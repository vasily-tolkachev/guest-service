package com.myproject.questservice.adapter.in.rest.dto.nodegenerator;

import jakarta.validation.constraints.NotBlank;

public record RenameNodeGeneratorProjectRequest(
        @NotBlank(message = "Project name must not be blank")
        String name
) {
}
