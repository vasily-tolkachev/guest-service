package com.myproject.questservice.application.port.in.nodegenerator;

import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.NodeGeneratorProjectView;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.FirstSceneIdeasResponse;
import com.myproject.questservice.application.service.generator.stage.StagePromptPreview;

import java.util.List;
import java.util.UUID;

public interface NodeGeneratorUseCase {
    NodeGeneratorProjectView createProject(String name, String questStyle);

    List<NodeGeneratorProjectView> listProjects();

    NodeGeneratorProjectView getProject(UUID id);

    NodeGeneratorProjectView renameProject(UUID id, String name);

    void deleteProject(UUID id);

    NodeGeneratorProjectView createWorkspaceNode(UUID projectId, String sourceNodeId, String sourceActionId);

    NodeGeneratorProjectView deleteWorkspaceNode(UUID projectId, String nodeId);

    NodeGeneratorProjectView updateWorkspaceNodeDescription(UUID projectId, String nodeId, String actionDescription, String stateDescription);

    NodeGeneratorProjectView addWorkspaceNodeAction(UUID projectId, String nodeId, String text);

    NodeGeneratorProjectView updateWorkspaceNodeAction(UUID projectId, String nodeId, String actionId, String text);

    NodeGeneratorProjectView createNextWorkspaceNode(UUID projectId, String nodeId, String actionId);

    StagePromptPreview previewWorkspaceNodeDescriptionPrompt(UUID projectId, String nodeId);

    NodeGeneratorProjectView generateWorkspaceNodeDescription(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride);

    StagePromptPreview previewWorkspaceNodeKnowledgePrompt(UUID projectId, String nodeId);

    NodeGeneratorProjectView extractWorkspaceNodeKnowledge(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride);

    StagePromptPreview previewWorkspaceNodeActionsPrompt(UUID projectId, String nodeId);

    NodeGeneratorProjectView generateWorkspaceNodeActions(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride);

    NodeGeneratorProjectView addWorkspaceGlobalKnowledge(UUID projectId, String text);

    NodeGeneratorProjectView removeWorkspaceGlobalKnowledge(UUID projectId, String text);

    NodeGeneratorProjectView addNodeKnowledgeToGlobal(UUID projectId, String nodeId, String text);

    NodeGeneratorProjectView runWorkspaceExpansion(UUID projectId, List<String> knowledge);

    NodeGeneratorProjectView acceptWorkspaceExpansionSuggestion(UUID projectId, String suggestionId);

    NodeGeneratorProjectView dismissWorkspaceExpansionSuggestion(UUID projectId, String suggestionId);

    Object exportProjectJson(UUID projectId);

    NodeGeneratorProjectView importProjectJson(UUID projectId, Object snapshotJson);

    NodeGeneratorProjectView importProjectJson(Object snapshotJson);

    UploadQuestResponse createQuestFromProject(UUID projectId);

    FirstSceneIdeasResponse generateFirstSceneIdeas(String prompt);
}
