package com.myproject.questservice.textruntime.domain.service;

import com.myproject.questservice.textruntime.domain.model.GameState;
import com.myproject.questservice.textruntime.domain.model.Item;
import com.myproject.questservice.textruntime.domain.model.Location;
import com.myproject.questservice.textruntime.domain.model.Npc;
import com.myproject.questservice.textruntime.domain.model.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GameEngine {
    private final World world;
    private final GameState state;

    public GameEngine(World world, GameState state) {
        this.world = world;
        this.state = state;
    }

    public String move(String locationId) {
        if (!world.hasLocation(locationId)) {
            return "Unknown location: " + locationId;
        }
        for (World.Transition transition : world.getTransitionsFrom(state.getCurrentLocation())) {
            if (transition.toId().equals(locationId)) {
                if (transition.condition() != null && !transition.condition().test(state, world)) {
                    return "Transition is blocked now";
                }
                state.setCurrentLocation(locationId);
                state.getPerformedActions().add("move:" + locationId);
                return "Moved to " + locationId;
            }
        }
        return "Destination is not reachable from current location";
    }

    public String take(String itemId) {
        String itemKey = findVisibleItem(itemId);
        if (itemKey == null) {
            return "Item is not available here: " + itemId;
        }

        state.getPlayer().getInventory().add(itemKey);
        state.getRemovedWorldItems().add(state.getCurrentLocation() + ":" + itemKey);
        state.getWorldChanges().add("item_taken:" + state.getCurrentLocation() + ":" + itemKey);
        state.getPerformedActions().add("take:" + itemKey);
        return "Item added to inventory: " + itemKey;
    }

    public String use(String itemId, String targetId) {
        String inventoryItem = state.getPlayer().getInventory().stream()
                .filter(i -> i.equalsIgnoreCase(itemId))
                .findFirst()
                .orElse(null);
        if (inventoryItem == null) {
            return "Item is not in inventory: " + itemId;
        }

        List<World.WorldAction> matchedActions = getAvailableActions().stream()
                .filter(action -> action.targetId() != null && action.targetId().equalsIgnoreCase(targetId))
                .filter(action -> action.requiredItems().stream()
                        .anyMatch(required -> required.equalsIgnoreCase(inventoryItem)))
                .toList();

        if (matchedActions.isEmpty()) {
            return "No action for using " + inventoryItem + " on " + targetId;
        }
        if (matchedActions.size() > 1) {
            return "Ambiguous action, please specify";
        }
        return executeAction(matchedActions.get(0).id());
    }

    public String executeAction(String actionId) {
        for (World.WorldAction action : world.getActions()) {
            if (action.id().equalsIgnoreCase(actionId)) {
                if (action.locationId() != null && !action.locationId().equals(state.getCurrentLocation())) {
                    return "Action is not available in this location";
                }
                if (action.condition() != null && !action.condition().test(state, world)) {
                    return "Action conditions are not met";
                }
                if (action.effect() != null) {
                    action.effect().apply(state, world);
                }
                state.getProgressFlags().addAll(action.progressFlagsToSet());
                state.getPerformedActions().add("action:" + action.id());
                return "Action executed: " + action.id();
            }
        }
        return "Unknown action: " + actionId;
    }

    public GameState getState() {
        return state;
    }

    public String talk(String npcId) {
        String npcKey = findVisibleNpc(npcId);
        if (npcKey == null) {
            return "NPC is not here: " + npcId;
        }
        Npc npc = world.getNpc(npcKey);
        if (npc == null) {
            return "Unknown NPC: " + npcId;
        }
        state.getCharacterStates().put("last_talked", npc.getId());
        state.getPerformedActions().add("talk:" + npc.getId());
        return npc.getDialogue();
    }

    public String inspect(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return "Target is empty";
        }

        Location location = world.getLocation(state.getCurrentLocation());
        if (location != null && location.getId().equalsIgnoreCase(targetId)) {
            return location.getDescription();
        }

        String itemKey = findVisibleItem(targetId);
        if (itemKey != null) {
            Item item = world.getItem(itemKey);
            return item == null ? "Item not found: " + targetId : item.getDescription();
        }

        String npcKey = findVisibleNpc(targetId);
        if (npcKey != null) {
            Npc npc = world.getNpc(npcKey);
            return npc == null ? "NPC not found: " + targetId : npc.getDescription();
        }

        return "Nothing to inspect: " + targetId;
    }

    public String interact(String targetId) {
        return interactDetailed(targetId).message();
    }

    public InteractionResult interactDetailed(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return new InteractionResult("Target is empty", "error:target_empty");
        }

        // 1) NPC interaction has priority.
        String npcKey = findVisibleNpc(targetId);
        if (npcKey != null) {
            return new InteractionResult(talk(npcKey), "talk:" + npcKey);
        }

        // 2) Try executing world action bound to target in current location.
        List<World.WorldAction> targetActions = getAvailableActions().stream()
                .filter(action -> action.targetId() != null && action.targetId().equalsIgnoreCase(targetId))
                .toList();
        if (targetActions.size() == 1) {
            String actionId = targetActions.get(0).id();
            return new InteractionResult(executeAction(actionId), "executeAction:" + actionId);
        }
        if (targetActions.size() > 1) {
            return new InteractionResult("Ambiguous interaction target: " + targetId, "error:ambiguous_target:" + targetId);
        }

        // 3) Item defaults to take (common text-quest behavior).
        String itemKey = findVisibleItem(targetId);
        if (itemKey != null) {
            return new InteractionResult(take(itemKey), "take:" + itemKey);
        }

        // 4) Location interaction defaults to move.
        if (isReachableLocation(targetId)) {
            String locationId = findReachableLocationId(targetId);
            return new InteractionResult(move(locationId), "move:" + locationId);
        }

        return new InteractionResult("No interaction available for: " + targetId, "error:no_interaction:" + targetId);
    }

    public InspectResult inspect() {
        Location location = world.getLocation(state.getCurrentLocation());
        if (location == null) {
            throw new IllegalStateException("Current location not found");
        }

        List<Item> visibleItems = getVisibleItemsInCurrentLocation().stream()
                .map(world::getItem)
                .filter(item -> item != null)
                .toList();
        List<Npc> visibleNpcs = getVisibleNpcsInCurrentLocation().stream()
                .map(world::getNpc)
                .filter(npc -> npc != null)
                .toList();

        List<ExitView> exits = getAvailableExitIds().stream()
                .map(exit -> new ExitView(exit, exit))
                .toList();

        List<Item> inventory = state.getPlayer().getInventory().stream()
                .map(world::getItem)
                .filter(item -> item != null)
                .toList();

        return new InspectResult(location, visibleItems, exits, inventory, visibleNpcs);
    }

    public List<World.WorldAction> getAvailableActions() {
        return world.getActions().stream()
                .filter(action -> action.locationId() == null || action.locationId().equals(state.getCurrentLocation()))
                .filter(action -> action.condition() == null || action.condition().test(state, world))
                .collect(Collectors.toList());
    }

    public List<String> getVisibleItemsInCurrentLocation() {
        List<String> result = new ArrayList<>();
        Set<String> itemIds = world.getInitialItemsInLocation(state.getCurrentLocation());
        for (String itemId : itemIds) {
            String marker = state.getCurrentLocation() + ":" + itemId;
            if (!state.getRemovedWorldItems().contains(marker)) {
                result.add(itemId);
            }
        }
        return result;
    }

    public List<String> getAvailableExitIds() {
        List<String> result = new ArrayList<>();
        for (World.Transition transition : world.getTransitionsFrom(state.getCurrentLocation())) {
            if (transition.condition() == null || transition.condition().test(state, world)) {
                result.add(transition.toId());
            }
        }
        return result;
    }

    public List<String> getVisibleNpcsInCurrentLocation() {
        return new ArrayList<>(world.getInitialNpcsInLocation(state.getCurrentLocation()));
    }

    private String findVisibleItem(String itemId) {
        for (String currentItemId : getVisibleItemsInCurrentLocation()) {
            if (currentItemId.equalsIgnoreCase(itemId)) {
                return currentItemId;
            }
        }
        return null;
    }

    private String findVisibleNpc(String npcId) {
        for (String currentNpcId : getVisibleNpcsInCurrentLocation()) {
            if (currentNpcId.equalsIgnoreCase(npcId)) {
                return currentNpcId;
            }
        }
        return null;
    }

    private boolean isReachableLocation(String locationId) {
        return findReachableLocationId(locationId) != null;
    }

    private String findReachableLocationId(String locationId) {
        for (World.Transition transition : world.getTransitionsFrom(state.getCurrentLocation())) {
            if (!transition.toId().equalsIgnoreCase(locationId)) {
                continue;
            }
            if (transition.condition() == null || transition.condition().test(state, world)) {
                return transition.toId();
            }
        }
        return null;
    }

    public record ExitView(String actionText, String targetLocationId) {
    }

    public record InspectResult(
            Location location,
            List<Item> visibleItems,
            List<ExitView> exits,
            List<Item> inventory,
            List<Npc> visibleNpcs
    ) {
    }

    public record InteractionResult(
            String message,
            String engineAction
    ) {
    }
}
