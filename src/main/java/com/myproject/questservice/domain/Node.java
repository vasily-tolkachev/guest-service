package com.myproject.questservice.domain;

import java.util.List;

public record Node(
        String id,
        String title,
        String location,
        List<String> participants,
        List<Condition> entryConditions,
        List<Effect> entryEffects,
        String text,
        List<Option> options
) {

    public Node(String id, String text, List<Option> options) {
        this(id, id, null, List.of(), List.of(), List.of(), text, options);
    }
}
