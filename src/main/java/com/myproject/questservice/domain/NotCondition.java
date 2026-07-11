package com.myproject.questservice.domain;

public record NotCondition(
        Condition nested
) implements Condition {

    @Override
    public boolean matches(GameState state) {
        return !nested.matches(state);
    }
}
