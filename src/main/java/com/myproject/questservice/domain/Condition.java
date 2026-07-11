package com.myproject.questservice.domain;

public interface Condition {

    boolean matches(GameState state);
}
