package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.domain.generator.StageType;

import java.util.UUID;

public interface StageRunner {

    StageType type();

    JsonNode generate(UUID projectId);
}

