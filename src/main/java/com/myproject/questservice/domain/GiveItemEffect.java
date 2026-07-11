package com.myproject.questservice.domain;

public record GiveItemEffect(
        String item
) implements Effect {

    @Override
    public void apply(GameState state) {
        state.getInventory().add(item);
    }
}
