package com.myproject.questservice.application.service.nodegenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.NodeGeneratorProjectView;
import com.myproject.questservice.application.port.in.generator.QuestGeneratorUseCase;
import com.myproject.questservice.application.port.in.nodegenerator.NodeGeneratorUseCase;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.generator.ProjectRepository;
import com.myproject.questservice.application.service.generator.stage.StagePromptPreview;
import com.myproject.questservice.domain.generator.NodeWorkspace;
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.QuestProjectStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NodeGeneratorApplicationService implements NodeGeneratorUseCase {
    private final ProjectRepository projectRepository;
    private final QuestGeneratorUseCase questGeneratorUseCase;
    private final ObjectMapper objectMapper;

    public NodeGeneratorApplicationService(
            ProjectRepository projectRepository,
            QuestGeneratorUseCase questGeneratorUseCase,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.questGeneratorUseCase = questGeneratorUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    public NodeGeneratorProjectView createProject(String name, String questStyle) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new BadRequestException("Project name is required");
        }
        String normalizedQuestStyle = questStyle == null || questStyle.trim().isBlank()
                ? "classic-adventure"
                : questStyle.trim();
        QuestProject created = projectRepository.save(QuestProject.create(normalizedName, normalizedQuestStyle));
        return toView(created);
    }

    @Override
    public List<NodeGeneratorProjectView> listProjects() {
        return projectRepository.findAll().stream().map(this::toView).toList();
    }

    @Override
    public NodeGeneratorProjectView getProject(UUID id) {
        return toView(getRequiredProject(id));
    }

    @Override
    public NodeGeneratorProjectView renameProject(UUID id, String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new BadRequestException("Project name is required");
        }
        QuestProject project = getRequiredProject(id);
        project.setName(normalizedName);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public void deleteProject(UUID id) {
        getRequiredProject(id);
        projectRepository.deleteById(id);
    }

    @Override
    public NodeGeneratorProjectView createWorkspaceNode(UUID projectId, String sourceNodeId, String sourceActionId) {
        questGeneratorUseCase.createWorkspaceNode(projectId, sourceNodeId, sourceActionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView deleteWorkspaceNode(UUID projectId, String nodeId) {
        questGeneratorUseCase.deleteWorkspaceNode(projectId, nodeId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView updateWorkspaceNodeDescription(UUID projectId, String nodeId, String actionDescription, String stateDescription) {
        questGeneratorUseCase.updateWorkspaceNodeDescription(projectId, nodeId, actionDescription, stateDescription);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView addWorkspaceNodeAction(UUID projectId, String nodeId, String text) {
        questGeneratorUseCase.addWorkspaceNodeAction(projectId, nodeId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView updateWorkspaceNodeAction(UUID projectId, String nodeId, String actionId, String text) {
        questGeneratorUseCase.updateWorkspaceNodeAction(projectId, nodeId, actionId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView createNextWorkspaceNode(UUID projectId, String nodeId, String actionId) {
        questGeneratorUseCase.createNextWorkspaceNode(projectId, nodeId, actionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeDescriptionPrompt(UUID projectId, String nodeId) {
        return questGeneratorUseCase.previewWorkspaceNodeDescriptionPrompt(projectId, nodeId);
    }

    @Override
    public NodeGeneratorProjectView generateWorkspaceNodeDescription(UUID projectId, String nodeId) {
        questGeneratorUseCase.generateWorkspaceNodeDescription(projectId, nodeId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeKnowledgePrompt(UUID projectId, String nodeId) {
        return questGeneratorUseCase.previewWorkspaceNodeKnowledgePrompt(projectId, nodeId);
    }

    @Override
    public NodeGeneratorProjectView extractWorkspaceNodeKnowledge(UUID projectId, String nodeId) {
        questGeneratorUseCase.extractWorkspaceNodeKnowledge(projectId, nodeId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeActionsPrompt(UUID projectId, String nodeId) {
        return questGeneratorUseCase.previewWorkspaceNodeActionsPrompt(projectId, nodeId);
    }

    @Override
    public NodeGeneratorProjectView generateWorkspaceNodeActions(UUID projectId, String nodeId) {
        questGeneratorUseCase.generateWorkspaceNodeActions(projectId, nodeId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView addWorkspaceGlobalKnowledge(UUID projectId, String text) {
        questGeneratorUseCase.addWorkspaceGlobalKnowledge(projectId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView removeWorkspaceGlobalKnowledge(UUID projectId, String text) {
        questGeneratorUseCase.removeWorkspaceGlobalKnowledge(projectId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView addNodeKnowledgeToGlobal(UUID projectId, String nodeId, String text) {
        questGeneratorUseCase.addNodeKnowledgeToGlobal(projectId, nodeId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView runWorkspaceExpansion(UUID projectId, List<String> knowledge) {
        questGeneratorUseCase.runWorkspaceExpansion(projectId, knowledge);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView acceptWorkspaceExpansionSuggestion(UUID projectId, String suggestionId) {
        questGeneratorUseCase.acceptWorkspaceExpansionSuggestion(projectId, suggestionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView dismissWorkspaceExpansionSuggestion(UUID projectId, String suggestionId) {
        questGeneratorUseCase.dismissWorkspaceExpansionSuggestion(projectId, suggestionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public Object exportProjectJson(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        var root = objectMapper.createObjectNode();
        root.put("name", project.getName());
        root.put("questStyle", project.getQuestStyle());
        root.put("status", project.getStatus().name());
        root.set("workspace", objectMapper.valueToTree(requiredWorkspace(project)));
        return objectMapper.convertValue(root, Object.class);
    }

    @Override
    public NodeGeneratorProjectView importProjectJson(UUID projectId, Object snapshotJson) {
        if (snapshotJson == null) {
            throw new BadRequestException("snapshotJson is required");
        }
        JsonNode snapshotNode = objectMapper.valueToTree(snapshotJson);
        if (snapshotNode == null || snapshotNode.isNull()) {
            throw new BadRequestException("snapshotJson is required");
        }

        QuestProject project = getRequiredProject(projectId);
        String importedName = snapshotNode.path("name").asText("").trim();
        if (!importedName.isBlank()) {
            project.setName(importedName);
        }
        String importedQuestStyle = snapshotNode.path("questStyle").asText("").trim();
        if (!importedQuestStyle.isBlank()) {
            project.setQuestStyle(importedQuestStyle);
        }
        String importedProjectStatus = snapshotNode.path("status").asText("").trim();
        if (!importedProjectStatus.isBlank()) {
            try {
                project.setStatus(QuestProjectStatus.valueOf(importedProjectStatus));
            } catch (IllegalArgumentException ignored) {
            }
        }

        JsonNode workspaceNode = snapshotNode.path("workspace");
        if (workspaceNode == null || workspaceNode.isMissingNode() || workspaceNode.isNull()) {
            throw new BadRequestException("snapshotJson.workspace is required");
        }
        NodeWorkspace workspace = objectMapper.convertValue(workspaceNode, NodeWorkspace.class);
        project.setNodeWorkspace(workspace == null ? NodeWorkspace.createEmpty() : workspace);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public NodeGeneratorProjectView importProjectJson(Object snapshotJson) {
        if (snapshotJson == null) {
            throw new BadRequestException("snapshotJson is required");
        }
        JsonNode snapshotNode = objectMapper.valueToTree(snapshotJson);
        if (snapshotNode == null || snapshotNode.isNull()) {
            throw new BadRequestException("snapshotJson is required");
        }

        String importedName = snapshotNode.path("name").asText("").trim();
        if (importedName.isBlank()) {
            throw new BadRequestException("snapshotJson.name is required");
        }

        QuestProject project = projectRepository.findByName(importedName)
                .orElseGet(() -> {
                    String questStyle = snapshotNode.path("questStyle").asText("").trim();
                    if (questStyle.isBlank()) {
                        questStyle = "classic-adventure";
                    }
                    return QuestProject.create(importedName, questStyle);
                });
        project.setName(importedName);

        String importedQuestStyle = snapshotNode.path("questStyle").asText("").trim();
        if (!importedQuestStyle.isBlank()) {
            project.setQuestStyle(importedQuestStyle);
        }
        String importedProjectStatus = snapshotNode.path("status").asText("").trim();
        if (!importedProjectStatus.isBlank()) {
            try {
                project.setStatus(QuestProjectStatus.valueOf(importedProjectStatus));
            } catch (IllegalArgumentException ignored) {
            }
        }

        JsonNode workspaceNode = snapshotNode.path("workspace");
        if (workspaceNode == null || workspaceNode.isMissingNode() || workspaceNode.isNull()) {
            throw new BadRequestException("snapshotJson.workspace is required");
        }
        NodeWorkspace workspace = objectMapper.convertValue(workspaceNode, NodeWorkspace.class);
        project.setNodeWorkspace(workspace == null ? NodeWorkspace.createEmpty() : workspace);
        projectRepository.save(project);
        return toView(project);
    }

    private QuestProject getRequiredProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    private NodeWorkspace requiredWorkspace(QuestProject project) {
        if (project.getNodeWorkspace() == null) {
            project.setNodeWorkspace(NodeWorkspace.createEmpty());
        }
        return project.getNodeWorkspace();
    }

    private NodeGeneratorProjectView toView(QuestProject project) {
        return new NodeGeneratorProjectView(
                project.getId().toString(),
                project.getName(),
                project.getQuestStyle(),
                project.getStatus().name(),
                objectMapper.convertValue(requiredWorkspace(project), Object.class)
        );
    }
}
