package com.myproject.questservice.domain;

public record VariableLessCondition(
        String variableName,
        int value
) implements Condition {

    @Override
    public boolean matches(GameState state) {
        String variableValue = state.getVariables().get(variableName);
        if (variableValue == null) {
            return false;
        }
        try {
            return Integer.parseInt(variableValue) < value;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
