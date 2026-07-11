package com.myproject.questservice.domain;

import java.util.List;

public record Node(
        String id,
        String text,
        List<Option> options
) {
}
