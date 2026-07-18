package com.myproject.questservice.domain.generator;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
public class QuestProject {
    private UUID id;
    private String name;
    private String questStyle;
    private QuestProjectStatus status;
    private List<QuestStage> stages;

    public QuestProject(UUID id, String name, String questStyle, QuestProjectStatus status, List<QuestStage> stages) {
        this.id = id;
        this.name = name;
        this.questStyle = questStyle;
        this.status = status;
        this.stages = stages;
    }

    public static QuestProject create(String name, String questStyle) {
        List<QuestStage> stages = new ArrayList<>();
        List<StageType> orderedTypes = Arrays.asList(
                StageType.QUEST_DESCRIPTION,
                StageType.QUEST_CONSTRAINTS,
                StageType.ACHIEVEMENT_RESOURCE_ANALYSIS,
                StageType.WORLD,
                StageType.ACHIEVEMENT_REALISATION,
                StageType.ACHIEVEMENT_INFORMATION_FLOW,
                StageType.KNOWLEDGE_CHAIN,
                StageType.ACHIEVEMENT_SCENES
        );
        for (int i = 0; i < orderedTypes.size(); i++) {
            StageType type = orderedTypes.get(i);
            StageStatus status = i == 0 ? StageStatus.READY : StageStatus.NOT_STARTED;
            stages.add(new QuestStage(type, status, false, null));
        }
        return new QuestProject(UUID.randomUUID(), name, questStyle, QuestProjectStatus.ACTIVE, stages);
    }

    public Optional<QuestStage> findStage(StageType type) {
        return stages.stream().filter(stage -> stage.getType() == type).findFirst();
    }

    public Optional<QuestStage> nextStage(StageType type) {
        int idx = indexOf(type);
        if (idx < 0 || idx + 1 >= stages.size()) {
            return Optional.empty();
        }
        return Optional.of(stages.get(idx + 1));
    }

    private int indexOf(StageType type) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }
}
