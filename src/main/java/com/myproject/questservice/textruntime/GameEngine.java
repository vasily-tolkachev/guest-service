package com.myproject.questservice.textruntime;

import java.util.ArrayList;
import java.util.List;

public class GameEngine {
    private final World world;
    private final GameState state;

    public GameEngine(World world, GameState state) {
        this.world = world;
        this.state = state;
    }

    public InspectResult inspect() {
        Location location = requireCurrentLocation();
        List<Item> items = new ArrayList<>();
        for (Item item : location.getItems()) {
            if (!state.getTakenItemIds().contains(item.getId().toUpperCase())) {
                items.add(item);
            }
        }
        return new InspectResult(location, items, location.getExits(), state.getPlayer().getInventory());
    }

    public void move(String locationId) {
        String target = normalize(locationId);
        Location current = requireCurrentLocation();
        boolean connected = current.getExits().stream()
                .anyMatch(exit -> normalize(exit.targetLocationId()).equals(target));
        if (!connected) {
            throw new IllegalArgumentException("Location is not reachable from current scene");
        }
        if (!world.getLocations().containsKey(target)) {
            throw new IllegalArgumentException("Location does not exist");
        }
        state.setCurrentLocationId(target);
    }

    public void take(String itemId) {
        String target = normalize(itemId);
        Location current = requireCurrentLocation();
        Item found = current.getItems().stream()
                .filter(item -> normalize(item.getId()).equals(target))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item does not exist in current location"));
        if (state.getTakenItemIds().contains(normalize(found.getId()))) {
            throw new IllegalArgumentException("Item already taken");
        }
        state.getTakenItemIds().add(normalize(found.getId()));
        state.getPlayer().addItem(found);
    }

    public String use(String itemId, String targetId) {
        if (!state.getPlayer().hasItem(itemId)) {
            throw new IllegalArgumentException("Item is not in inventory");
        }
        return "use(" + itemId + ", " + targetId + ")";
    }

    public GameState getState() {
        return state;
    }

    private Location requireCurrentLocation() {
        Location location = world.getLocations().get(normalize(state.getCurrentLocationId()));
        if (location == null) {
            throw new IllegalStateException("Current location does not exist");
        }
        return location;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    public record InspectResult(
            Location location,
            List<Item> visibleItems,
            List<Location.Exit> exits,
            List<Item> inventory
    ) {
    }
}

