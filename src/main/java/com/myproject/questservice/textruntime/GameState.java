package com.myproject.questservice.textruntime;

import java.util.HashSet;
import java.util.Set;

public class GameState {
    private String currentLocationId;
    private final Player player;
    private final Set<String> takenItemIds;

    public GameState(String currentLocationId) {
        this.currentLocationId = currentLocationId;
        this.player = new Player();
        this.takenItemIds = new HashSet<>();
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

    public Set<String> getTakenItemIds() {
        return takenItemIds;
    }
}

