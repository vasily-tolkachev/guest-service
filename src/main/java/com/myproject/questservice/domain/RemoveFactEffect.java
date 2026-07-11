package com.myproject.questservice.domain;

public record RemoveFactEffect(
        String fact
) implements Effect {

    @Override
    public void apply(GameState state) {
        state.getFacts().remove(fact);
    }
}
