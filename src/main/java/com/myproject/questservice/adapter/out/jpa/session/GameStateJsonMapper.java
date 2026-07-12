package com.myproject.questservice.adapter.out.jpa.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.domain.GameState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameStateJsonMapper {

    private final ObjectMapper objectMapper;

    public JsonNode toJsonNode(GameState state) {
        return objectMapper.valueToTree(state);
    }

    public GameState toDomain(JsonNode json) {
        try {
            return objectMapper.treeToValue(json, GameState.class);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to deserialize game state");
        }
    }
}
