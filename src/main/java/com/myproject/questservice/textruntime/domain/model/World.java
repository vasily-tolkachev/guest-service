package com.myproject.questservice.textruntime.domain.model;

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
    private final Map<String, Npc> npcs = new LinkedHashMap<>();
    private final Map<String, Set<String>> locationItems = new LinkedHashMap<>();
    private final Map<String, Set<String>> locationNpcs = new LinkedHashMap<>();
    private final List<Transition> transitions = new ArrayList<>();
    private final List<WorldAction> actions = new ArrayList<>();
    private final List<Ending> endings = new ArrayList<>();

    public void addLocation(Location location) {
        locations.put(location.getId(), location);
        locationItems.putIfAbsent(location.getId(), new LinkedHashSet<>());
        locationNpcs.putIfAbsent(location.getId(), new LinkedHashSet<>());
    }

    public void addItem(Item item) {
        items.put(item.getId(), item);
    }

    public void placeItem(String locationId, String itemId) {
        locationItems.computeIfAbsent(locationId, ignored -> new LinkedHashSet<>()).add(itemId);
    }

    public void addNpc(Npc npc) {
        npcs.put(npc.getId(), npc);
    }

    public void placeNpc(String locationId, String npcId) {
        locationNpcs.computeIfAbsent(locationId, ignored -> new LinkedHashSet<>()).add(npcId);
    }

    public void addTransition(String fromId, String toId, Condition condition) {
        transitions.add(new Transition(fromId, toId, condition));
    }

    public void addAction(WorldAction action) {
        actions.add(action);
    }

    public void addEnding(Ending ending) {
        endings.add(ending);
    }

    public Location getLocation(String id) {
        return locations.get(id);
    }

    public boolean hasLocation(String id) {
        return locations.containsKey(id);
    }

    public Item getItem(String id) {
        return items.get(id);
    }

    public Npc getNpc(String id) {
        return npcs.get(id);
    }

    public Collection<Location> getLocations() {
        return Collections.unmodifiableCollection(locations.values());
    }

    public Set<String> getInitialItemsInLocation(String locationId) {
        return locationItems.getOrDefault(locationId, Collections.emptySet());
    }

    public Set<String> getInitialNpcsInLocation(String locationId) {
        return locationNpcs.getOrDefault(locationId, Collections.emptySet());
    }

    public List<Transition> getTransitionsFrom(String locationId) {
        List<Transition> result = new ArrayList<>();
        for (Transition transition : transitions) {
            if (transition.fromId().equals(locationId)) {
                result.add(transition);
            }
        }
        return result;
    }

    public List<WorldAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public List<Ending> getEndings() {
        return Collections.unmodifiableList(endings);
    }

    @FunctionalInterface
    public interface Condition {
        boolean test(GameState state, World world);
    }

    @FunctionalInterface
    public interface Effect {
        void apply(GameState state, World world);
    }

    public record Transition(String fromId, String toId, Condition condition) {
    }

    public record WorldAction(
            String id,
            String locationId,
            String description,
            Condition condition,
            Effect effect,
            Set<String> requiredItems,
            String targetId,
            Set<String> progressFlagsToSet
    ) {
        public WorldAction {
            requiredItems = requiredItems == null ? Set.of() : Set.copyOf(requiredItems);
            progressFlagsToSet = progressFlagsToSet == null ? Set.of() : Set.copyOf(progressFlagsToSet);
        }
    }

    public record Ending(String id, Condition condition) {
    }
}
