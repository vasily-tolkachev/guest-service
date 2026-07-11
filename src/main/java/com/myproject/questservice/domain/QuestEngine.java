package com.myproject.questservice.domain;

public class QuestEngine {

    private final Quest quest;
    private final GameState gameState;

    public QuestEngine(Quest quest, GameState gameState) {
        this.quest = quest;
        this.gameState = gameState;
    }

    public Node start() {
        gameState.setCurrentNodeId(quest.startNodeId());
        return requireNode(gameState.getCurrentNodeId());
    }

    public Node choose(String optionId) {
        Node currentNode = requireNode(gameState.getCurrentNodeId());
        Option selected = currentNode.options()
                .stream()
                .filter(option -> option.id().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        gameState.setCurrentNodeId(selected.transition().targetNodeId());
        return requireNode(gameState.getCurrentNodeId());
    }

    private Node requireNode(String nodeId) {
        Node node = quest.nodes().get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Node not found: " + nodeId);
        }
        return node;
    }

    public boolean isFinished(Node node) {
        return node.options().isEmpty();
    }
}
