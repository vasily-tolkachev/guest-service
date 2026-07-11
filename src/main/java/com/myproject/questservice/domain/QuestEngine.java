package com.myproject.questservice.domain;

import java.util.List;

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
        Option selected = availableOptions(currentNode)
                .stream()
                .filter(option -> option.id().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        selected.transition().effects().forEach(effect -> effect.apply(gameState));
        gameState.setCurrentNodeId(selected.transition().targetNodeId());
        return requireNode(gameState.getCurrentNodeId());
    }

    public List<Option> availableOptions(Node node) {
        return node.options().stream()
                .filter(this::isTransitionAvailable)
                .toList();
    }

    private Node requireNode(String nodeId) {
        Node node = quest.nodes().get(nodeId);
        if (node == null) {
            throw new IllegalStateException("Node not found: " + nodeId);
        }
        return node;
    }

    public boolean isFinished(Node node) {
        return availableOptions(node).isEmpty();
    }

    public GameState gameState() {
        return gameState;
    }

    private boolean isTransitionAvailable(Option option) {
        return option.transition().conditions().stream()
                .allMatch(condition -> condition.matches(gameState));
    }
}
