package com.myproject.questservice.application.port.in.generator;

import com.myproject.questservice.adapter.in.rest.dto.generator.QuestProjectView;
import com.myproject.questservice.domain.generator.StageType;

import java.util.List;
import java.util.UUID;

public interface QuestGeneratorUseCase {

    QuestProjectView createProject(String name, String questStyle);

    QuestProjectView getProject(UUID id);

    List<QuestProjectView> listProjects();

    QuestProjectView generateStage(UUID projectId, StageType stageType);

    QuestProjectView approveStage(UUID projectId, StageType stageType);

    String exportDsl(UUID projectId);
}
