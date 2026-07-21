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
    private String actionDescription;
    private String stateDescription;
    private List<WorkspaceAction> actions;
    private String sourceNodeId;
    private String sourceActionId;
    private Instant updatedAt;
    private String generatedDescriptionDraft;
    private String generatedActionDescriptionDraft;
    private String generatedStateDescriptionDraft;
    private List<String> extractedKnowledgeDraft;
    private List<String> generatedActionsDraft;

    public WorkspaceNode() {
        this("", "", "", "", new ArrayList<>(), null, null, Instant.now(), "", "", "", new ArrayList<>(), new ArrayList<>());
    }

    public WorkspaceNode(
            String id,
            String description,
            String actionDescription,
            String stateDescription,
            List<WorkspaceAction> actions,
            String sourceNodeId,
            String sourceActionId,
            Instant updatedAt,
            String generatedDescriptionDraft,
            String generatedActionDescriptionDraft,
            String generatedStateDescriptionDraft,
            List<String> extractedKnowledgeDraft,
            List<String> generatedActionsDraft
    ) {
        this.id = id;
        this.description = description;
        this.actionDescription = actionDescription;
        this.stateDescription = stateDescription;
        this.actions = actions;
        this.sourceNodeId = sourceNodeId;
        this.sourceActionId = sourceActionId;
        this.updatedAt = updatedAt;
        this.generatedDescriptionDraft = generatedDescriptionDraft;
        this.generatedActionDescriptionDraft = generatedActionDescriptionDraft;
        this.generatedStateDescriptionDraft = generatedStateDescriptionDraft;
        this.extractedKnowledgeDraft = extractedKnowledgeDraft;
        this.generatedActionsDraft = generatedActionsDraft;
    }

    public static WorkspaceNode create(String id, String sourceNodeId, String sourceActionId) {
        return new WorkspaceNode(
                id,
                "",
                "",
                "",
                new ArrayList<>(),
                sourceNodeId,
                sourceActionId,
                Instant.now(),
                "",
                "",
                "",
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}
