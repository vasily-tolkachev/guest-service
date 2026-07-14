package com.myproject.questservice.domain.generator;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestStage {
    private final StageType type;
    private StageStatus status;
    private boolean approved;
    private StageRevision currentRevision;

    public QuestStage(StageType type, StageStatus status, boolean approved, StageRevision currentRevision) {
        this.type = type;
        this.status = status;
        this.approved = approved;
        this.currentRevision = currentRevision;
    }
}

