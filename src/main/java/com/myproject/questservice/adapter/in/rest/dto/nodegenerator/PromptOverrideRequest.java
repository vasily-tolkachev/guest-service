package com.myproject.questservice.adapter.in.rest.dto.nodegenerator;

public record PromptOverrideRequest(
        String systemPrompt,
        String userPrompt
) {
}
