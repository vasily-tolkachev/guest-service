package com.myproject.questservice.domain;

import java.util.List;

public record AndCondition(
        List<Condition> conditions
) implements Condition {

    @Override
    public boolean matches(GameState state) {
        return conditions.stream().allMatch(condition -> condition.matches(state));
    }
}
