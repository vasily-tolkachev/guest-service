package com.myproject.questservice.adapter.in.rest.dto.nodegenerator;

import jakarta.validation.constraints.NotBlank;

public record CreateNodeGeneratorProjectRequest(
        @NotBlank String name,
        String questStyle
) {
}
