package com.myproject.questservice.adapter.in.rest.dto.generator;

public record QuestStageView(
        String type,
        String status,
        boolean approved,
        StageRevisionView currentRevision
) {
}

