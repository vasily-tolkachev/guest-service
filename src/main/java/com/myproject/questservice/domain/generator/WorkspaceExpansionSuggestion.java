package com.myproject.questservice.domain.generator;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class WorkspaceExpansionSuggestion {
    private String id;
    private String nodeId;
    private String actionText;
    private String reason;
    private String status;
    private List<String> sourceKnowledge;

    public WorkspaceExpansionSuggestion() {
        this("", "", "", "", "PENDING", new ArrayList<>());
    }

    public WorkspaceExpansionSuggestion(
            String id,
            String nodeId,
            String actionText,
            String reason,
            String status,
            List<String> sourceKnowledge
    ) {
        this.id = id;
        this.nodeId = nodeId;
        this.actionText = actionText;
        this.reason = reason;
        this.status = status;
        this.sourceKnowledge = sourceKnowledge;
    }
}
