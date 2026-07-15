package com.myproject.questservice.application.port.in.generator;

import com.myproject.questservice.adapter.in.rest.dto.generator.QuestProjectView;
import com.myproject.questservice.domain.generator.StageType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public interface QuestGeneratorUseCase {

    QuestProjectView createProject(String name, String questStyle);

    QuestProjectView getProject(UUID id);

    List<QuestProjectView> listProjects();

    QuestProjectView generateStage(UUID projectId, StageType stageType);
    QuestProjectView generateStageStep(UUID projectId, StageType stageType, String step);

    QuestProjectView approveStage(UUID projectId, StageType stageType);

    Object exportProjectJson(UUID projectId);

    QuestProjectView importProjectJson(UUID projectId, JsonNode snapshotJson);

    String exportDsl(UUID projectId);

    String convertDsl(String projectName, JsonNode questGraphJson);
}
