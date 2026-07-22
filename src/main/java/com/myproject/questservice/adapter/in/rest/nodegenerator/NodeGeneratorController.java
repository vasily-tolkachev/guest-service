package com.myproject.questservice.adapter.in.rest.nodegenerator;

import com.myproject.questservice.adapter.in.rest.dto.generator.AddGlobalKnowledgeRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.CreateWorkspaceNodeRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.RemoveGlobalKnowledgeRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.RunExpansionRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.UpdateWorkspaceNodeDescriptionRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.UpsertWorkspaceActionRequest;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.CreateNodeGeneratorProjectRequest;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.ImportNodeGeneratorJsonRequest;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.NodeGeneratorProjectView;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.PromptOverrideRequest;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.RenameNodeGeneratorProjectRequest;
import com.myproject.questservice.application.port.in.nodegenerator.NodeGeneratorUseCase;
import com.myproject.questservice.application.service.generator.stage.StagePromptPreview;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/node-generator/projects")
public class NodeGeneratorController {
    private final NodeGeneratorUseCase nodeGeneratorUseCase;

    public NodeGeneratorController(NodeGeneratorUseCase nodeGeneratorUseCase) {
        this.nodeGeneratorUseCase = nodeGeneratorUseCase;
    }

    @PostMapping
    public NodeGeneratorProjectView createProject(@Valid @RequestBody CreateNodeGeneratorProjectRequest request) {
        return nodeGeneratorUseCase.createProject(request.name(), request.questStyle());
    }

    @GetMapping
    public List<NodeGeneratorProjectView> listProjects() {
        return nodeGeneratorUseCase.listProjects();
    }

    @GetMapping("/{id}")
    public NodeGeneratorProjectView getProject(@PathVariable UUID id) {
        return nodeGeneratorUseCase.getProject(id);
    }

    @PutMapping("/{id}")
    public NodeGeneratorProjectView renameProject(@PathVariable UUID id, @Valid @RequestBody RenameNodeGeneratorProjectRequest request) {
        return nodeGeneratorUseCase.renameProject(id, request.name());
    }

    @DeleteMapping("/{id}")
    public void deleteProject(@PathVariable UUID id) {
        nodeGeneratorUseCase.deleteProject(id);
    }

    @PostMapping("/{id}/nodes")
    public NodeGeneratorProjectView createWorkspaceNode(@PathVariable UUID id, @RequestBody(required = false) CreateWorkspaceNodeRequest request) {
        String sourceNodeId = request == null ? null : request.sourceNodeId();
        String sourceActionId = request == null ? null : request.sourceActionId();
        return nodeGeneratorUseCase.createWorkspaceNode(id, sourceNodeId, sourceActionId);
    }

    @DeleteMapping("/{id}/nodes/{nodeId}")
    public NodeGeneratorProjectView deleteWorkspaceNode(@PathVariable UUID id, @PathVariable String nodeId) {
        return nodeGeneratorUseCase.deleteWorkspaceNode(id, nodeId);
    }

    @PutMapping("/{id}/nodes/{nodeId}/description")
    public NodeGeneratorProjectView updateWorkspaceNodeDescription(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @RequestBody(required = false) UpdateWorkspaceNodeDescriptionRequest request
    ) {
        String actionDescription = request == null ? "" : request.actionDescription();
        String stateDescription = request == null ? "" : request.stateDescription();
        return nodeGeneratorUseCase.updateWorkspaceNodeDescription(id, nodeId, actionDescription, stateDescription);
    }

    @PostMapping("/{id}/nodes/{nodeId}/actions")
    public NodeGeneratorProjectView addWorkspaceNodeAction(@PathVariable UUID id, @PathVariable String nodeId, @RequestBody(required = false) UpsertWorkspaceActionRequest request) {
        String text = request == null ? "" : request.text();
        return nodeGeneratorUseCase.addWorkspaceNodeAction(id, nodeId, text);
    }

    @PutMapping("/{id}/nodes/{nodeId}/actions/{actionId}")
    public NodeGeneratorProjectView updateWorkspaceNodeAction(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @PathVariable String actionId,
            @RequestBody(required = false) UpsertWorkspaceActionRequest request
    ) {
        String text = request == null ? "" : request.text();
        return nodeGeneratorUseCase.updateWorkspaceNodeAction(id, nodeId, actionId, text);
    }

    @PostMapping("/{id}/nodes/{nodeId}/actions/{actionId}/create-next-node")
    public NodeGeneratorProjectView createNextWorkspaceNode(@PathVariable UUID id, @PathVariable String nodeId, @PathVariable String actionId) {
        return nodeGeneratorUseCase.createNextWorkspaceNode(id, nodeId, actionId);
    }

    @PostMapping("/{id}/nodes/{nodeId}/generate-description/preview")
    public StagePromptPreview previewWorkspaceNodeDescriptionPrompt(@PathVariable UUID id, @PathVariable String nodeId) {
        return nodeGeneratorUseCase.previewWorkspaceNodeDescriptionPrompt(id, nodeId);
    }

    @PostMapping("/{id}/nodes/{nodeId}/generate-description")
    public NodeGeneratorProjectView generateWorkspaceNodeDescription(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @RequestBody(required = false) PromptOverrideRequest request
    ) {
        String systemPrompt = request == null ? null : request.systemPrompt();
        String userPrompt = request == null ? null : request.userPrompt();
        return nodeGeneratorUseCase.generateWorkspaceNodeDescription(id, nodeId, systemPrompt, userPrompt);
    }

    @PostMapping("/{id}/nodes/{nodeId}/extract-knowledge/preview")
    public StagePromptPreview previewWorkspaceNodeKnowledgePrompt(@PathVariable UUID id, @PathVariable String nodeId) {
        return nodeGeneratorUseCase.previewWorkspaceNodeKnowledgePrompt(id, nodeId);
    }

    @PostMapping("/{id}/nodes/{nodeId}/extract-knowledge")
    public NodeGeneratorProjectView extractWorkspaceNodeKnowledge(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @RequestBody(required = false) PromptOverrideRequest request
    ) {
        String systemPrompt = request == null ? null : request.systemPrompt();
        String userPrompt = request == null ? null : request.userPrompt();
        return nodeGeneratorUseCase.extractWorkspaceNodeKnowledge(id, nodeId, systemPrompt, userPrompt);
    }

    @PostMapping("/{id}/nodes/{nodeId}/generate-actions/preview")
    public StagePromptPreview previewWorkspaceNodeActionsPrompt(@PathVariable UUID id, @PathVariable String nodeId) {
        return nodeGeneratorUseCase.previewWorkspaceNodeActionsPrompt(id, nodeId);
    }

    @PostMapping("/{id}/nodes/{nodeId}/generate-actions")
    public NodeGeneratorProjectView generateWorkspaceNodeActions(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @RequestBody(required = false) PromptOverrideRequest request
    ) {
        String systemPrompt = request == null ? null : request.systemPrompt();
        String userPrompt = request == null ? null : request.userPrompt();
        return nodeGeneratorUseCase.generateWorkspaceNodeActions(id, nodeId, systemPrompt, userPrompt);
    }

    @PostMapping("/{id}/knowledge")
    public NodeGeneratorProjectView addWorkspaceGlobalKnowledge(@PathVariable UUID id, @RequestBody(required = false) AddGlobalKnowledgeRequest request) {
        String text = request == null ? "" : request.text();
        return nodeGeneratorUseCase.addWorkspaceGlobalKnowledge(id, text);
    }

    @PostMapping("/{id}/knowledge/remove")
    public NodeGeneratorProjectView removeWorkspaceGlobalKnowledge(@PathVariable UUID id, @RequestBody(required = false) RemoveGlobalKnowledgeRequest request) {
        String text = request == null ? "" : request.text();
        return nodeGeneratorUseCase.removeWorkspaceGlobalKnowledge(id, text);
    }

    @PostMapping("/{id}/nodes/{nodeId}/knowledge/add-to-global")
    public NodeGeneratorProjectView addNodeKnowledgeToGlobal(
            @PathVariable UUID id,
            @PathVariable String nodeId,
            @RequestBody(required = false) AddGlobalKnowledgeRequest request
    ) {
        String text = request == null ? "" : request.text();
        return nodeGeneratorUseCase.addNodeKnowledgeToGlobal(id, nodeId, text);
    }

    @PostMapping("/{id}/run-expansion")
    public NodeGeneratorProjectView runWorkspaceExpansion(@PathVariable UUID id, @RequestBody(required = false) RunExpansionRequest request) {
        return nodeGeneratorUseCase.runWorkspaceExpansion(id, request == null ? null : request.knowledge());
    }

    @PostMapping("/{id}/expansion/{suggestionId}/accept")
    public NodeGeneratorProjectView acceptWorkspaceExpansionSuggestion(@PathVariable UUID id, @PathVariable String suggestionId) {
        return nodeGeneratorUseCase.acceptWorkspaceExpansionSuggestion(id, suggestionId);
    }

    @PostMapping("/{id}/expansion/{suggestionId}/dismiss")
    public NodeGeneratorProjectView dismissWorkspaceExpansionSuggestion(@PathVariable UUID id, @PathVariable String suggestionId) {
        return nodeGeneratorUseCase.dismissWorkspaceExpansionSuggestion(id, suggestionId);
    }

    @GetMapping("/{id}/export-json")
    public Object exportProjectJson(@PathVariable UUID id) {
        return nodeGeneratorUseCase.exportProjectJson(id);
    }

    @PostMapping("/{id}/import-json")
    public NodeGeneratorProjectView importProjectJson(@PathVariable UUID id, @Valid @RequestBody ImportNodeGeneratorJsonRequest request) {
        return nodeGeneratorUseCase.importProjectJson(id, request.snapshotJson());
    }

    @PostMapping("/import-json")
    public NodeGeneratorProjectView importProjectJson(@Valid @RequestBody ImportNodeGeneratorJsonRequest request) {
        return nodeGeneratorUseCase.importProjectJson(request.snapshotJson());
    }
}
