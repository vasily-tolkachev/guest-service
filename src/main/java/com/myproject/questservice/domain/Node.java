package com.myproject.questservice.domain;

import java.util.List;

public record Node(
        String id,
        String title,
        String text,
        List<Option> options
) {

    public Node(String id, String text, List<Option> options) {
        this(id, id, text, options);
    }
}
