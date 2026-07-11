package com.myproject.questservice.domain;

public record AddFactEffect(
        String fact
) implements Effect {

    @Override
    public void apply(GameState state) {
        state.getFacts().add(fact);
    }
}
