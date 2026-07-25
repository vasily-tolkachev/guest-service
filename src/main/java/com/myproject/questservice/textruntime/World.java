package com.myproject.questservice.textruntime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class World {
    private final Map<String, Location> locations = new LinkedHashMap<>();
    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Map<String, Set<String>> locationItems = new LinkedHashMap<>();
    private final List<Transition> transitions = new ArrayList<>();

    public World(Map<String, Location> sourceLocations) {
        for (Location location : sourceLocations.values()) {
            addLocation(location);
            for (Item item : location.getItems()) {
                addItem(item);
                placeItem(location.getId(), item.getId());
            }
            for (Location.Exit exit : location.getExits()) {
                if (exit.targetLocationId() == null || exit.targetLocationId().isBlank()) {
                    continue;
                }
                addTransition(location.getId(), exit.targetLocationId(), exit.actionText(), null);
            }
        }
    }

    public void addLocation(Location location) {
        locations.put(location.getId().toUpperCase(), location);
        locationItems.putIfAbsent(location.getId().toUpperCase(), new LinkedHashSet<>());
    }

    public void addItem(Item item) {
        items.put(item.getId().toUpperCase(), item);
    }

    public void placeItem(String locationId, String itemId) {
        locationItems.computeIfAbsent(locationId.toUpperCase(), ignored -> new LinkedHashSet<>()).add(itemId.toUpperCase());
    }

    public Location getLocation(String id) {
        return locations.get(id.toUpperCase());
    }

    public boolean hasLocation(String id) {
        return locations.containsKey(id.toUpperCase());
    }

    public Item getItem(String id) {
        return items.get(id.toUpperCase());
    }

    public Collection<Location> getLocations() {
        return Collections.unmodifiableCollection(locations.values());
    }

    public Set<String> getInitialItemsInLocation(String locationId) {
        return locationItems.getOrDefault(locationId.toUpperCase(), Collections.emptySet());
    }

    public List<Transition> getTransitionsFrom(String locationId) {
        List<Transition> result = new ArrayList<>();
        for (Transition transition : transitions) {
            if (transition.fromId().equalsIgnoreCase(locationId)) {
                result.add(transition);
            }
        }
        return result;
    }

    public void addTransition(String fromId, String toId, String actionText, Condition condition) {
        transitions.add(new Transition(fromId.toUpperCase(), toId.toUpperCase(), actionText, condition));
    }

    @FunctionalInterface
    public interface Condition {
        boolean test(GameState state, World world);
    }

    public record Transition(String fromId, String toId, String actionText, Condition condition) {
    }
}
