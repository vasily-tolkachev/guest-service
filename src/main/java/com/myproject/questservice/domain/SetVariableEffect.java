package com.myproject.questservice.domain;

public record SetVariableEffect(
        String variableName,
        String value
) implements Effect {

    @Override
    public void apply(GameState state) {
        state.getVariables().put(variableName, value);
    }
}
