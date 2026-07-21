package com.myproject.questservice.domain.generator;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class WorkspaceNode {
    private String id;
    private String description;
    private List<WorkspaceAction> actions;
    private String sourceNodeId;
    private String sourceActionId;
    private Instant updatedAt;
    private String generatedDescriptionDraft;
    private List<String> extractedKnowledgeDraft;
    private List<String> generatedActionsDraft;

    public WorkspaceNode() {
        this("", "", new ArrayList<>(), null, null, Instant.now(), "", new ArrayList<>(), new ArrayList<>());
    }

    public WorkspaceNode(
            String id,
            String description,
            List<WorkspaceAction> actions,
            String sourceNodeId,
            String sourceActionId,
            Instant updatedAt,
            String generatedDescriptionDraft,
            List<String> extractedKnowledgeDraft,
            List<String> generatedActionsDraft
    ) {
        this.id = id;
        this.description = description;
        this.actions = actions;
        this.sourceNodeId = sourceNodeId;
        this.sourceActionId = sourceActionId;
        this.updatedAt = updatedAt;
        this.generatedDescriptionDraft = generatedDescriptionDraft;
        this.extractedKnowledgeDraft = extractedKnowledgeDraft;
        this.generatedActionsDraft = generatedActionsDraft;
    }

    public static WorkspaceNode create(String id, String sourceNodeId, String sourceActionId) {
        return new WorkspaceNode(
                id,
                "",
                new ArrayList<>(),
                sourceNodeId,
                sourceActionId,
                Instant.now(),
                "",
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
