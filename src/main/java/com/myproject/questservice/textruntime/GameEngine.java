package com.myproject.questservice.textruntime;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GameEngine {
    private final World world;
    private final GameState state;

    public GameEngine(World world, GameState state) {
        this.world = world;
        this.state = state;
    }

    public InspectResult inspect() {
        Location location = requireCurrentLocation();
        List<Item> items = getVisibleItemsInCurrentLocation();
        List<Location.Exit> exits = world.getTransitionsFrom(location.getId()).stream()
                .filter(transition -> transition.condition() == null || transition.condition().test(state, world))
                .map(transition -> new Location.Exit(
                        transition.actionText() == null || transition.actionText().isBlank() ? transition.toId() : transition.actionText(),
                        transition.toId()
                ))
                .collect(Collectors.toList());
        return new InspectResult(location, items, exits, state.getPlayer().getInventory());
    }

    public void move(String locationId) {
        String target = normalize(locationId);
        if (!world.hasLocation(target)) {
            throw new IllegalArgumentException("Location does not exist");
        }
        boolean connected = world.getTransitionsFrom(state.getCurrentLocationId()).stream()
                .anyMatch(transition ->
                        normalize(transition.toId()).equals(target)
                                && (transition.condition() == null || transition.condition().test(state, world))
                );
        if (!connected) {
            throw new IllegalArgumentException("Location is not reachable from current scene");
        }
        state.setCurrentLocationId(target);
        state.getPerformedActions().add("move:" + target);
    }

    public void take(String itemId) {
        String target = normalize(itemId);
        Item found = getVisibleItemsInCurrentLocation().stream()
                .filter(item -> normalize(item.getId()).equals(target))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item does not exist in current location"));
        String marker = normalize(state.getCurrentLocationId()) + ":" + normalize(found.getId());
        state.getRemovedWorldItems().add(marker);
        state.getWorldChanges().add("item_taken:" + marker);
        state.getPerformedActions().add("take:" + normalize(found.getId()));
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
        Location location = world.getLocation(normalize(state.getCurrentLocationId()));
        if (location == null) {
            throw new IllegalStateException("Current location does not exist");
        }
        return location;
    }

    private List<Item> getVisibleItemsInCurrentLocation() {
        List<Item> result = new ArrayList<>();
        for (String itemId : world.getInitialItemsInLocation(state.getCurrentLocationId())) {
            String marker = normalize(state.getCurrentLocationId()) + ":" + normalize(itemId);
            if (state.getRemovedWorldItems().contains(marker)) {
                continue;
            }
            Item item = world.getItem(itemId);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
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
