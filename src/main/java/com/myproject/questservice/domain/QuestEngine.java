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
        gameState.getNavigationHistory().clear();
        markVisited(gameState.getCurrentNodeId());
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
        gameState.getNavigationHistory().add(currentNode.id());
        gameState.setCurrentNodeId(selected.transition().targetNodeId());
        markVisited(gameState.getCurrentNodeId());
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

    public boolean canGoBack() {
        return !gameState.getNavigationHistory().isEmpty();
    }

    public Node goBack() {
        if (!canGoBack()) {
            throw new IllegalArgumentException("No previous node in navigation history.");
        }
        int lastIndex = gameState.getNavigationHistory().size() - 1;
        String previousNodeId = gameState.getNavigationHistory().remove(lastIndex);
        gameState.setCurrentNodeId(previousNodeId);
        markVisited(previousNodeId);
        return requireNode(previousNodeId);
    }

    public GameState gameState() {
        return gameState;
    }

    private void markVisited(String nodeId) {
        gameState.getVisitedNodes().add(nodeId);
    }

    private boolean isTransitionAvailable(Option option) {
        return option.transition().conditions().stream()
                .allMatch(condition -> condition.matches(gameState));
    }
}
