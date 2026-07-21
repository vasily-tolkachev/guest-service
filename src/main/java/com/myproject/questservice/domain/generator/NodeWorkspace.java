package com.myproject.questservice.domain.generator;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NodeWorkspace {
    private List<WorkspaceNode> nodes;
    private List<String> globalKnowledge;
    private List<WorkspaceExpansionSuggestion> expansionSuggestions;
    private List<WorkspaceAiRequestLog> aiRequests;
    private int nextNodeIndex;
    private int nextActionIndex;
    private int nextSuggestionIndex;
    private int nextAiRequestIndex;

    public NodeWorkspace() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 1, 1, 1, 1);
    }

    public NodeWorkspace(
            List<WorkspaceNode> nodes,
            List<String> globalKnowledge,
            List<WorkspaceExpansionSuggestion> expansionSuggestions,
            List<WorkspaceAiRequestLog> aiRequests,
            int nextNodeIndex,
            int nextActionIndex,
            int nextSuggestionIndex,
            int nextAiRequestIndex
    ) {
        this.nodes = nodes;
        this.globalKnowledge = globalKnowledge;
        this.expansionSuggestions = expansionSuggestions;
        this.aiRequests = aiRequests;
        this.nextNodeIndex = nextNodeIndex;
        this.nextActionIndex = nextActionIndex;
        this.nextSuggestionIndex = nextSuggestionIndex;
        this.nextAiRequestIndex = nextAiRequestIndex;
    }

    public static NodeWorkspace createEmpty() {
        return new NodeWorkspace(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 1, 1, 1, 1);
    }
}
