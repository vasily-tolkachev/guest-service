package com.myproject.questservice.application.port.out.generator;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiClient {

    JsonNode generate(String systemPrompt, String userPrompt);

    default JsonNode generate(String systemPrompt, String userPrompt, String modelOverride) {
        return generate(systemPrompt, userPrompt);
    }
}
