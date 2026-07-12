package com.myproject.questservice.adapter.out.postgres.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.domain.GameState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameStateJsonMapper {

    private final ObjectMapper objectMapper;

    public String toJson(GameState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to serialize game state");
        }
    }

    public GameState toDomain(String json) {
        try {
            return objectMapper.readValue(json, GameState.class);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to deserialize game state");
        }
    }
}
