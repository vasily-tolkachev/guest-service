package com.myproject.questservice.domain;

public record HasFactCondition(
        String fact
) implements Condition {

    @Override
    public boolean matches(GameState state) {
        return state.getFacts().contains(fact);
    }
}
