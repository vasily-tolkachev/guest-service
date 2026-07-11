package com.myproject.questservice.domain;

public record VariableEqualsCondition(
        String variableName,
        String expectedValue
) implements Condition {

    @Override
    public boolean matches(GameState state) {
        return expectedValue.equals(state.getVariables().get(variableName));
    }
}
