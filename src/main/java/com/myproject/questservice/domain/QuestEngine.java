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
        Node startNode = requireNode(gameState.getCurrentNodeId());
        ensureNodeEnterable(startNode);
        applyNodeEntryEffects(startNode);
        markVisited(gameState.getCurrentNodeId());
        return startNode;
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
        if (selected.transition().isEnd()) {
            gameState.setCurrentNodeId(currentNode.id());
            return currentNode;
        }
        gameState.setCurrentNodeId(selected.transition().targetNodeId());
        Node nextNode = requireNode(gameState.getCurrentNodeId());
        ensureNodeEnterable(nextNode);
        applyNodeEntryEffects(nextNode);
        markVisited(gameState.getCurrentNodeId());
        return nextNode;
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
        List<Option> available = availableOptions(node);
        return available.isEmpty() || available.stream().allMatch(option -> option.transition().isEnd());
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
        if (option.transition().conditions().stream().anyMatch(condition -> !condition.matches(gameState))) {
            return false;
        }
        if (option.transition().isEnd()) {
            return true;
        }
        Node targetNode = quest.nodes().get(option.transition().targetNodeId());
        if (targetNode == null) {
            return false;
        }
        return targetNode.entryConditions().stream().allMatch(condition -> condition.matches(gameState));
    }

    private void ensureNodeEnterable(Node node) {
        boolean canEnter = node.entryConditions().stream().allMatch(condition -> condition.matches(gameState));
        if (!canEnter) {
            throw new IllegalStateException("Cannot enter node due to unmet entry conditions: " + node.id());
        }
    }

    private void applyNodeEntryEffects(Node node) {
        node.entryEffects().forEach(effect -> effect.apply(gameState));
    }
}
