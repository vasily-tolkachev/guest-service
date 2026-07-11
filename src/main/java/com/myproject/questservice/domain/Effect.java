package com.myproject.questservice.domain;

public interface Effect {

    void apply(GameState state);
}
