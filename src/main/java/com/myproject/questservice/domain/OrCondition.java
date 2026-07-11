package com.myproject.questservice.domain;

import java.util.List;

public record OrCondition(
        List<Condition> conditions
) implements Condition {

    @Override
    public boolean matches(GameState state) {
        return conditions.stream().anyMatch(condition -> condition.matches(state));
    }
}
