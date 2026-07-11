package com.myproject.questservice.domain;

public record HasItemCondition(
        String item
) implements Condition {

    @Override
    public boolean matches(GameState state) {
        return state.getInventory().contains(item);
    }
}
