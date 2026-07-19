package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public interface AchievementSceneStageRunner extends StageRunner {

    JsonNode generateAchievement(UUID projectId, String wayId, JsonNode currentOutput);

    StagePromptPreview previewAchievementPrompt(UUID projectId, String wayId);
}
