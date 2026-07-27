package com.myproject.questservice.adapter.in.rest.dto.generator;

public record PromptOverrideRequest(
        String systemPrompt,
        String userPrompt
) {
}
