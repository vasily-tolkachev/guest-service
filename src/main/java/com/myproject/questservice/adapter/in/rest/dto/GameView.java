package com.myproject.questservice.adapter.in.rest.dto;

import java.util.List;

public record GameView(
        String title,
        String text,
        List<OptionView> options,
        boolean finished
) {
}
