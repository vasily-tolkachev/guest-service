package com.myproject.questservice.adapter.in.rest.generator;

import com.myproject.questservice.adapter.in.rest.dto.generator.ConvertDslRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.CreateProjectRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.CreateWorkspaceNodeRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.ImportProjectJsonRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.QuestProjectView;
import com.myproject.questservice.adapter.in.rest.dto.generator.UpdateWorkspaceNodeDescriptionRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.UpsertWorkspaceActionRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.application.port.in.generator.QuestGeneratorUseCase;
import com.myproject.questservice.application.service.generator.stage.StagePromptPreview;
import com.myproject.questservice.domain.generator.StageType;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/generator/projects")
public class QuestGeneratorController {

    private final QuestGeneratorUseCase questGeneratorUseCase;

    public QuestGeneratorController(QuestGeneratorUseCase questGeneratorUseCase) {
        this.questGeneratorUseCase = questGeneratorUseCase;
    }

    @PostMapping
    public QuestProjectView createProject(@Valid @RequestBody CreateProjectRequest request) {
        return questGeneratorUseCase.createProject(request.name(), request.questStyle());
    }

    @GetMapping
    public List<QuestProjectView> listProjects() {
        return questGeneratorUseCase.listProjects();
    }

    @GetMapping("/{id}")
    public QuestProjectView getProject(@PathVariable UUID id) {
        return questGeneratorUseCase.getProject(id);
    }

    @PostMapping("/{id}/stages/{type}/generate")
    public QuestProjectView generateStage(@PathVariable UUID id, @PathVariable StageType type) {
        return questGeneratorUseCase.generateStage(id, type);
    }

    @PostMapping("/{id}/stages/{type}/preview")
    public StagePromptPreview previewStagePrompt(@PathVariable UUID id, @PathVariable StageType type) {
        return questGeneratorUseCase.previewStagePrompt(id, type);
    }

    @PostMapping("/{id}/stages/{type}/steps/{step}/generate")
    public QuestProjectView generateStageStep(@PathVariable UUID id, @PathVariable StageType type, @PathVariable String step) {
        return questGeneratorUseCase.generateStageStep(id, type, step);
    }

    @PostMapping("/{id}/stages/{type}/approve")
    public QuestProjectView approveStage(@PathVariable UUID id, @PathVariable StageType type) {
        return questGeneratorUseCase.approveStage(id, type);
    }

    @PostMapping("/{id}/stages/CHAPTERS/chapters/{chapterId}/generate")
    public QuestProjectView generateChapter(@PathVariable UUID id, @PathVariable String chapterId) {
        return questGeneratorUseCase.generateChapter(id, chapterId);
    }

    @PostMapping("/{id}/stages/CHAPTERS/chapters/{chapterId}/approve")
    public QuestProjectView approveChapter(@PathVariable UUID id, @PathVariable String chapterId) {
        return questGeneratorUseCase.approveChapter(id, chapterId);
    }

    @PostMapping("/{id}/stages/SCENES/scenes/{sceneId}/generate")
    public QuestProjectView generateScene(@PathVariable UUID id, @PathVariable String sceneId) {
        return questGeneratorUseCase.generateScene(id, sceneId);
    }

    @PostMapping("/{id}/stages/SCENES/scenes/{sceneId}/approve")
    public QuestProjectView approveScene(@PathVariable UUID id, @PathVariable String sceneId) {
        return questGeneratorUseCase.approveScene(id, sceneId);
    }

    @PostMapping("/{id}/stages/ACHIEVEMENT_SCENES/ways/{wayId}/generate")
    public QuestProjectView generateAchievementScene(@PathVariable UUID id, @PathVariable String wayId) {
        return questGeneratorUseCase.generateAchievementScene(id, wayId);
    }

    @PostMapping("/{id}/stages/ACHIEVEMENT_SCENES/ways/{wayId}/preview")
    public StagePromptPreview previewAchievementScene(@PathVariable UUID id, @PathVariable String wayId) {
        return questGeneratorUseCase.previewAchievementScenePrompt(id, wayId);
    }

    @PostMapping("/{id}/stages/ACHIEVEMENT_SCENES/ways/{wayId}/approve")
    public QuestProjectView approveAchievementScene(@PathVariable UUID id, @PathVariable String wayId) {
        return questGeneratorUseCase.approveAchievementScene(id, wayId);
    }

    @PostMapping("/{id}/stages/KNOWLEDGE_CHAIN/ways/{wayId}/generate")
    public QuestProjectView generateKnowledgeChain(@PathVariable UUID id, @PathVariable String wayId) {
        return questGeneratorUseCase.generateKnowledgeChain(id, wayId);
    }

    @PostMapping("/{id}/stages/KNOWLEDGE_CHAIN/ways/{wayId}/preview")
    public StagePromptPreview previewKnowledgeChain(@PathVariable UUID id, @PathVariable String wayId) {
        return questGeneratorUseCase.previewKnowledgeChainPrompt(id, wayId);
    }

    @PostMapping("/{id}/stages/KNOWLEDGE_CHAIN/ways/{wayId}/approve")
    public QuestProjectView approveKnowledgeChain(@PathVariable UUID id, @PathVariable String wayId) {
        return questGeneratorUseCase.approveKnowledgeChain(id, wayId);
    }

    @PostMapping("/{id}/stages/ACTION_QUESTS/ways/{wayId}/preview")
    public StagePromptPreview previewActionQuest(@PathVariable UUID id, @PathVariable String wayId) {
        return questGeneratorUseCase.previewActionQuestPrompt(id, wayId);
    }

    @PostMapping("/{id}/stages/ACTION_QUESTS/ways/{wayId}/scenes/{sceneId}/actions/{actionId}/preview")
    public StagePromptPreview previewActionResolution(
            @PathVariable UUID id,
            @PathVariable String wayId,
            @PathVariable String sceneId,
            @PathVariable String actionId
    ) {
        return questGeneratorUseCase.previewActionResolutionPrompt(id, wayId, sceneId, actionId);
    }

    @PostMapping("/{id}/stages/ACTION_QUESTS/ways/{wayId}/scenes/{sceneId}/actions/{actionId}/generate")
    public QuestProjectView generateActionResolution(
            @PathVariable UUID id,
            @PathVariable String wayId,
            @PathVariable String sceneId,
            @PathVariable String actionId
    ) {
        return questGeneratorUseCase.generateActionResolution(id, wayId, sceneId, actionId);
    }

    @PostMapping("/{id}/stages/ACTION_QUESTS/ways/{wayId}/scenes/{sceneId}/actions/{actionId}/approve")
    public QuestProjectView approveActionResolution(
            @PathVariable UUID id,
            @PathVariable String wayId,
            @PathVariable String sceneId,
            @PathVariable String actionId
    ) {
        return questGeneratorUseCase.approveActionResolution(id, wayId, sceneId, actionId);
    }

    @GetMapping("/{id}/export-json")
    public Object exportProjectJson(@PathVariable UUID id) {
        return questGeneratorUseCase.exportProjectJson(id);
    }

    @PostMapping("/{id}/import-json")
    public QuestProjectView importProjectJson(@PathVariable UUID id, @Valid @RequestBody ImportProjectJsonRequest request) {
        return questGeneratorUseCase.importProjectJson(id, request.snapshotJson());
    }

    @PostMapping(value = "/{id}/export-dsl", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportDsl(@PathVariable UUID id) {
        return questGeneratorUseCase.exportDsl(id);
    }

    @PostMapping(value = "/convert-dsl", produces = MediaType.TEXT_PLAIN_VALUE)
    public String convertDsl(@RequestBody ConvertDslRequest request) {
        return questGeneratorUseCase.convertDsl(request.projectName(), request.questGraphJson());
    }

    @GetMapping("/{id}/node-workspace/nodes")
    public QuestProjectView listWorkspaceNodes(@PathVariable UUID id) {
        return questGeneratorUseCase.listWorkspaceNodes(id);
    }

    @PostMapping("/{id}/node-workspace/nodes")
    public QuestProjectView createWorkspaceNode(@PathVariable UUID id, @RequestBody(required = false) CreateWorkspaceNodeRequest request) {
        String sourceNodeId = request == null ? null : request.sourceNodeId();
        String sourceActionId = request == null ? null : request.sourceActionId();
        return questGeneratorUseCase.createWorkspaceNode(id, sourceNodeId, sourceActionId);
    }

    @GetMapping("/{id}/node-workspace/nodes/{nodeId}")
    public QuestProjectView getWorkspaceNode(@PathVariable UUID id, @PathVariable String nodeId) {
        return questGeneratorUseCase.getWorkspaceNode(id, nodeId);
    }

    @PutMapping("/{id}/node-workspace/nodes/{nodeId}/description")
    public QuestProjectView updateWorkspaceNodeDescription(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @RequestBody(required = false) UpdateWorkspaceNodeDescriptionRequest request
    ) {
        String description = request == null ? "" : request.description();
        return questGeneratorUseCase.updateWorkspaceNodeDescription(id, nodeId, description);
    }

    @PostMapping("/{id}/node-workspace/nodes/{nodeId}/actions")
    public QuestProjectView addWorkspaceNodeAction(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @RequestBody(required = false) UpsertWorkspaceActionRequest request
    ) {
        String text = request == null ? "" : request.text();
        return questGeneratorUseCase.addWorkspaceNodeAction(id, nodeId, text);
    }

    @PutMapping("/{id}/node-workspace/nodes/{nodeId}/actions/{actionId}")
    public QuestProjectView updateWorkspaceNodeAction(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @PathVariable String actionId,
            @RequestBody(required = false) UpsertWorkspaceActionRequest request
    ) {
        String text = request == null ? "" : request.text();
        return questGeneratorUseCase.updateWorkspaceNodeAction(id, nodeId, actionId, text);
    }

    @PostMapping("/{id}/node-workspace/nodes/{nodeId}/actions/{actionId}/create-next-node")
    public QuestProjectView createNextWorkspaceNode(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @PathVariable String actionId
    ) {
        return questGeneratorUseCase.createNextWorkspaceNode(id, nodeId, actionId);
    }

    @PostMapping("/{id}/node-workspace/nodes/{nodeId}/generate-description")
    public QuestProjectView generateWorkspaceNodeDescription(@PathVariable UUID id, @PathVariable String nodeId) {
        return questGeneratorUseCase.generateWorkspaceNodeDescription(id, nodeId);
    }

    @PostMapping("/{id}/node-workspace/nodes/{nodeId}/extract-knowledge")
    public QuestProjectView extractWorkspaceNodeKnowledge(@PathVariable UUID id, @PathVariable String nodeId) {
        return questGeneratorUseCase.extractWorkspaceNodeKnowledge(id, nodeId);
    }

    @PostMapping("/{id}/node-workspace/nodes/{nodeId}/generate-actions")
    public QuestProjectView generateWorkspaceNodeActions(@PathVariable UUID id, @PathVariable String nodeId) {
        return questGeneratorUseCase.generateWorkspaceNodeActions(id, nodeId);
    }
}
