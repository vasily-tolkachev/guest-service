package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public interface ActionQuestStageRunner extends StageRunner {

    JsonNode generateActionQuest(UUID projectId, String wayId, JsonNode currentOutput);

    StagePromptPreview previewActionQuestPrompt(UUID projectId, String wayId);
}
