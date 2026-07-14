package com.myproject.questservice.adapter.in.rest.dto.generator;

import com.fasterxml.jackson.databind.JsonNode;

public record ConvertDslRequest(
        String projectName,
        JsonNode questGraphJson
) {
}

