package com.myproject.questservice.textruntime.domain.service;

import com.myproject.questservice.textruntime.domain.model.GameState;
import com.myproject.questservice.textruntime.domain.model.Item;
import com.myproject.questservice.textruntime.domain.model.Location;
import com.myproject.questservice.textruntime.domain.model.Npc;
import com.myproject.questservice.textruntime.domain.model.Dialogue;
import com.myproject.questservice.textruntime.domain.model.World;
import com.myproject.questservice.textruntime.domain.model.WorldObject;

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
        if (actionId != null && actionId.startsWith("dialogue:")) {
            String[] tokens = actionId.split(":", 3);
            if (tokens.length == 3) {
                try {
                    int optionIndex = Integer.parseInt(tokens[2]);
                    return chooseDialogueOption(tokens[1], optionIndex);
                } catch (NumberFormatException ignored) {
                    return "Unknown action: " + actionId;
                }
            }
        }
        for (World.WorldAction action : world.getActions()) {
            if (action.id().equalsIgnoreCase(actionId)) {
                if (action.locationId() != null && !action.locationId().equals(state.getCurrentLocation())) {
                    return "Action is not available in this location";
                }
                if (!action.requiredItems().isEmpty()) {
                    boolean hasAllRequiredItems = action.requiredItems().stream()
                            .allMatch(requiredItem -> state.getPlayer().getInventory().stream()
                                    .anyMatch(inventoryItem -> inventoryItem.equalsIgnoreCase(requiredItem)));
                    if (!hasAllRequiredItems) {
                        return "Action required items are missing";
                    }
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
        String dialogueId = npc.getDialogueId();
        if (dialogueId == null || dialogueId.isBlank()) {
            return npc.getDialogue() == null ? "" : npc.getDialogue();
        }

        Dialogue dialogue = world.getDialogue(dialogueId);
        if (dialogue == null) {
            return npc.getDialogue() == null ? "" : npc.getDialogue();
        }

        Dialogue.Node node = resolveCurrentDialogueNode(npc, dialogue);
        if (node == null) {
            return npc.getDialogue() == null ? "" : npc.getDialogue();
        }
        return node.text();
    }

    public List<DialogueOptionView> getDialogueOptions(String npcId) {
        String npcKey = findVisibleNpc(npcId);
        if (npcKey == null) {
            return List.of();
        }
        Npc npc = world.getNpc(npcKey);
        if (npc == null || npc.getDialogueId() == null || npc.getDialogueId().isBlank()) {
            return List.of();
        }
        Dialogue dialogue = world.getDialogue(npc.getDialogueId());
        if (dialogue == null) {
            return List.of();
        }
        Dialogue.Node node = resolveCurrentDialogueNode(npc, dialogue);
        if (node == null || node.options() == null || node.options().isEmpty()) {
            return List.of();
        }
        List<DialogueOptionView> options = new ArrayList<>();
        for (int i = 0; i < node.options().size(); i++) {
            Dialogue.Option option = node.options().get(i);
            if (option == null || option.text() == null || option.text().isBlank()) {
                continue;
            }
            if (option.condition() != null && !option.condition().test(state, world)) {
                continue;
            }
            options.add(new DialogueOptionView("dialogue:" + npc.getId() + ":" + i, option.text(), npc.getId()));
        }
        return options;
    }

    private String chooseDialogueOption(String npcId, int optionIndex) {
        String npcKey = findVisibleNpc(npcId);
        if (npcKey == null) {
            return "No interaction available for: " + npcId;
        }
        Npc npc = world.getNpc(npcKey);
        if (npc == null || npc.getDialogueId() == null || npc.getDialogueId().isBlank()) {
            return "No interaction available for: " + npcId;
        }
        Dialogue dialogue = world.getDialogue(npc.getDialogueId());
        if (dialogue == null) {
            return "No interaction available for: " + npcId;
        }
        Dialogue.Node currentNode = resolveCurrentDialogueNode(npc, dialogue);
        if (currentNode == null || currentNode.options() == null || optionIndex < 0 || optionIndex >= currentNode.options().size()) {
            return "No interaction available for: " + npcId;
        }
        Dialogue.Option option = currentNode.options().get(optionIndex);
        if (option.condition() != null && !option.condition().test(state, world)) {
            return "Action conditions are not met";
        }

        String nextNodeId = option.nextNodeId();
        if (nextNodeId == null || nextNodeId.isBlank()) {
            state.getDialogueNodeByNpc().remove(npc.getId());
            state.getPerformedActions().add("dialogue_end:" + npc.getId());
            return "Диалог завершен.";
        }
        Dialogue.Node nextNode = findDialogueNode(dialogue, nextNodeId);
        if (nextNode == null) {
            state.getDialogueNodeByNpc().remove(npc.getId());
            state.getPerformedActions().add("dialogue_end:" + npc.getId());
            return "Диалог завершен.";
        }

        state.getDialogueNodeByNpc().put(npc.getId(), nextNode.id());
        if (nextNode.effect() != null) {
            nextNode.effect().apply(state, world);
        }
        state.getPerformedActions().add("dialogue:" + npc.getId() + ":" + optionIndex);
        return nextNode.text();
    }

    private Dialogue.Node resolveCurrentDialogueNode(Npc npc, Dialogue dialogue) {
        String currentNodeId = state.getDialogueNodeByNpc().get(npc.getId());
        Dialogue.Node node = findDialogueNode(dialogue, currentNodeId);
        if (node != null) {
            return node;
        }
        Dialogue.Node startNode = findDialogueNode(dialogue, dialogue.startNodeId());
        if (startNode != null) {
            state.getDialogueNodeByNpc().put(npc.getId(), startNode.id());
        }
        return startNode;
    }

    private static Dialogue.Node findDialogueNode(Dialogue dialogue, String nodeId) {
        if (dialogue == null || nodeId == null || nodeId.isBlank() || dialogue.nodes() == null) {
            return null;
        }
        for (Dialogue.Node node : dialogue.nodes()) {
            if (node != null && node.id() != null && node.id().equals(nodeId)) {
                return node;
            }
        }
        return null;
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

        String objectKey = findVisibleObject(targetId);
        if (objectKey != null) {
            WorldObject worldObject = world.getWorldObject(objectKey);
            return worldObject == null ? "Object not found: " + targetId : worldObject.getDescription();
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
        if (targetId.startsWith("dialogue:")) {
            return new InteractionResult(executeAction(targetId), "dialogue:" + targetId);
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

        // 5) Explicitly reject action-id routed to interact.
        List<World.WorldAction> actionById = getAvailableActions().stream()
                .filter(action -> action.id() != null && action.id().equalsIgnoreCase(targetId))
                .toList();
        if (actionById.size() == 1) {
            return new InteractionResult(
                    "Action id must be executed via execute-action: " + targetId,
                    "error:wrong_endpoint_action_id:" + targetId
            );
        }
        if (actionById.size() > 1) {
            return new InteractionResult("Ambiguous action id: " + targetId, "error:ambiguous_action_id:" + targetId);
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
        List<WorldObject> visibleObjects = getVisibleObjectsInCurrentLocation().stream()
                .map(world::getWorldObject)
                .filter(worldObject -> worldObject != null)
                .toList();

        List<ExitView> exits = getAvailableExitIds().stream()
                .map(exit -> new ExitView(exit, exit))
                .toList();

        List<Item> inventory = state.getPlayer().getInventory().stream()
                .map(world::getItem)
                .filter(item -> item != null)
                .toList();

        return new InspectResult(location, visibleItems, exits, inventory, visibleNpcs, visibleObjects);
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

    public List<String> getVisibleObjectsInCurrentLocation() {
        return new ArrayList<>(world.getInitialObjectsInLocation(state.getCurrentLocation()));
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

    private String findVisibleObject(String objectId) {
        for (String currentObjectId : getVisibleObjectsInCurrentLocation()) {
            if (currentObjectId.equalsIgnoreCase(objectId)) {
                return currentObjectId;
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
            List<Npc> visibleNpcs,
            List<WorldObject> visibleObjects
    ) {
    }

    public record InteractionResult(
            String message,
            String engineAction
    ) {
    }

    public record DialogueOptionView(
            String actionId,
            String text,
            String npcId
    ) {
    }
}
