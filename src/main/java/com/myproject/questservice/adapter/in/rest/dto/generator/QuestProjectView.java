package com.myproject.questservice.adapter.in.rest.dto.generator;

import java.util.List;

public record QuestProjectView(
        String id,
        String name,
        String status,
        List<QuestStageView> stages
) {
}

