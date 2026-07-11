package com.myproject.questservice.domain;

public record RemoveItemEffect(
        String item
) implements Effect {

    @Override
    public void apply(GameState state) {
        state.getInventory().remove(item);
    }
}
