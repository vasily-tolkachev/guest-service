package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public interface KnowledgeChainWayStageRunner extends StageRunner {

    JsonNode generateKnowledgeChain(UUID projectId, String wayId, JsonNode currentOutput);
}
