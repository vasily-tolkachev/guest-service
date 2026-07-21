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
    private List<String> expansionSuggestions;
    private int nextNodeIndex;
    private int nextActionIndex;

    public NodeWorkspace() {
        this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 1, 1);
    }

    public NodeWorkspace(
            List<WorkspaceNode> nodes,
            List<String> globalKnowledge,
            List<String> expansionSuggestions,
            int nextNodeIndex,
            int nextActionIndex
    ) {
        this.nodes = nodes;
        this.globalKnowledge = globalKnowledge;
        this.expansionSuggestions = expansionSuggestions;
        this.nextNodeIndex = nextNodeIndex;
        this.nextActionIndex = nextActionIndex;
    }

    public static NodeWorkspace createEmpty() {
        return new NodeWorkspace(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 1, 1);
    }
}
