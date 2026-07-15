package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public interface SceneStageRunner extends StageRunner {

    JsonNode generateScene(UUID projectId, String sceneId, JsonNode currentOutput);
}
