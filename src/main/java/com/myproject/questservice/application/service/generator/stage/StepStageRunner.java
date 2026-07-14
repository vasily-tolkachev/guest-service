package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public interface StepStageRunner extends StageRunner {

    List<String> steps();

    JsonNode generateStep(UUID projectId, String step, JsonNode currentOutput);
}

