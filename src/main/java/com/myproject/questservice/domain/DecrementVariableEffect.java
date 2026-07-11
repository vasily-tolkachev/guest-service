package com.myproject.questservice.domain;

public record DecrementVariableEffect(
        String variableName,
        int delta
) implements Effect {

    @Override
    public void apply(GameState state) {
        String currentValue = state.getVariables().getOrDefault(variableName, "0");
        int nextValue = Integer.parseInt(currentValue) - delta;
        state.getVariables().put(variableName, String.valueOf(nextValue));
    }
}
