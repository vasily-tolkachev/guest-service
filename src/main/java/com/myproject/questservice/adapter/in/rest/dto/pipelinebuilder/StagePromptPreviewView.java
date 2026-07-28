package com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder;

public record StagePromptPreviewView(
        String systemPrompt,
        String userPrompt,
        Object args,
        String memory
) {
}
