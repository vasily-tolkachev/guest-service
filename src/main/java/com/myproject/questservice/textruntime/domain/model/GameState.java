package com.myproject.questservice.textruntime.domain.model;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class GameState {
    private String currentLocation;
    private final Player player;
    private final Set<String> worldChanges = new LinkedHashSet<>();
    private final Set<String> knownFacts = new LinkedHashSet<>();
    private final Set<String> progressFlags = new LinkedHashSet<>();
    private final Set<String> performedActions = new LinkedHashSet<>();
    private final Map<String, String> objectStates = new LinkedHashMap<>();
    private final Map<String, String> characterStates = new LinkedHashMap<>();
    private final Map<String, String> dialogueNodeByNpc = new LinkedHashMap<>();
    private final Set<String> removedWorldItems = new HashSet<>();

    public GameState(String currentLocation, Player player) {
        this.currentLocation = currentLocation;
        this.player = player;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public Player getPlayer() {
        return player;
    }

    public Set<String> getWorldChanges() {
        return worldChanges;
    }

    public Set<String> getKnownFacts() {
        return knownFacts;
    }

    public Set<String> getProgressFlags() {
        return progressFlags;
    }

    public Set<String> getPerformedActions() {
        return performedActions;
    }

    public Map<String, String> getObjectStates() {
        return objectStates;
    }

    public Map<String, String> getCharacterStates() {
        return characterStates;
    }

    public Map<String, String> getDialogueNodeByNpc() {
        return dialogueNodeByNpc;
    }

    public Set<String> getRemovedWorldItems() {
        return removedWorldItems;
    }
}
