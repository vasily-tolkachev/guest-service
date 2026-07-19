package com.myproject.questservice.application.port.in.generator;

import com.myproject.questservice.adapter.in.rest.dto.generator.QuestProjectView;
import com.myproject.questservice.application.service.generator.stage.StagePromptPreview;
import com.myproject.questservice.domain.generator.StageType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public interface QuestGeneratorUseCase {

    QuestProjectView createProject(String name, String questStyle);

    QuestProjectView getProject(UUID id);

    List<QuestProjectView> listProjects();

    QuestProjectView generateStage(UUID projectId, StageType stageType);
    StagePromptPreview previewStagePrompt(UUID projectId, StageType stageType);
    QuestProjectView generateStageStep(UUID projectId, StageType stageType, String step);

    QuestProjectView approveStage(UUID projectId, StageType stageType);
    QuestProjectView generateChapter(UUID projectId, String chapterId);
    QuestProjectView approveChapter(UUID projectId, String chapterId);
    QuestProjectView generateScene(UUID projectId, String sceneId);
    QuestProjectView approveScene(UUID projectId, String sceneId);
    QuestProjectView generateAchievementScene(UUID projectId, String wayId);
    QuestProjectView approveAchievementScene(UUID projectId, String wayId);
    QuestProjectView generateKnowledgeChain(UUID projectId, String wayId);
    QuestProjectView approveKnowledgeChain(UUID projectId, String wayId);
    QuestProjectView generateActionQuest(UUID projectId, String wayId);
    QuestProjectView approveActionQuest(UUID projectId, String wayId);
    StagePromptPreview previewKnowledgeChainPrompt(UUID projectId, String wayId);
    StagePromptPreview previewAchievementScenePrompt(UUID projectId, String wayId);
    StagePromptPreview previewActionQuestPrompt(UUID projectId, String wayId);

    Object exportProjectJson(UUID projectId);

    QuestProjectView importProjectJson(UUID projectId, Object snapshotJson);

    String exportDsl(UUID projectId);

    String convertDsl(String projectName, JsonNode questGraphJson);
}
