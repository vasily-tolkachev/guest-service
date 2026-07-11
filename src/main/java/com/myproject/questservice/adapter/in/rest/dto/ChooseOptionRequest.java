package com.myproject.questservice.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record ChooseOptionRequest(
        @NotBlank(message = "optionId is required")
        String optionId
) {
}
