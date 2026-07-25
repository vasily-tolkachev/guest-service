package com.myproject.questservice.textruntime;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class GameState {
    private String currentLocationId;
    private final Player player;
    private final Set<String> worldChanges;
    private final Set<String> knownFacts;
    private final Set<String> progressFlags;
    private final Set<String> performedActions;
    private final Map<String, String> objectStates;
    private final Map<String, String> characterStates;
    private final Set<String> removedWorldItems;

    public GameState(String currentLocationId) {
        this.currentLocationId = currentLocationId;
        this.player = new Player();
        this.worldChanges = new LinkedHashSet<>();
        this.knownFacts = new LinkedHashSet<>();
        this.progressFlags = new LinkedHashSet<>();
        this.performedActions = new LinkedHashSet<>();
        this.objectStates = new LinkedHashMap<>();
        this.characterStates = new LinkedHashMap<>();
        this.removedWorldItems = new HashSet<>();
    }

    public String getCurrentLocationId() {
        return currentLocationId;
    }

    public void setCurrentLocationId(String currentLocationId) {
        this.currentLocationId = currentLocationId;
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

    public Set<String> getRemovedWorldItems() {
        return removedWorldItems;
    }
}
