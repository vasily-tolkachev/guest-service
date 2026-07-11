package com.myproject.questservice.domain;

public record Option(
        String id,
        String text,
        Transition transition
) {
}
