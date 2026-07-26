package com.myproject.questservice.textruntime.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.textruntime.application.port.in.TextRuntimeUseCase;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeActionResult;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeGenerationStatus;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestImportRequest;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestSummary;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeSnapshot;
import com.myproject.questservice.textruntime.application.port.out.RuntimeQuestCatalogPort;
import com.myproject.questservice.textruntime.application.port.out.RuntimeSessionStorePort;
import com.myproject.questservice.textruntime.domain.model.GameState;
import com.myproject.questservice.textruntime.domain.model.Player;
import com.myproject.questservice.textruntime.domain.model.RuntimeQuestDefinition;
import com.myproject.questservice.textruntime.domain.model.World;
import com.myproject.questservice.textruntime.domain.model.WorldObject;
import com.myproject.questservice.textruntime.domain.service.GameEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TextRuntimeApplicationService implements TextRuntimeUseCase {
    private final RuntimeQuestCatalogPort questCatalogPort;
    private final RuntimeSessionStorePort sessionStorePort;
    private final AiClient aiClient;
    private final Map<UUID, Map<String, GeneratedSceneState>> generatedBySession = new ConcurrentHashMap<>();
    private final Map<UUID, RuntimeQuestDefinition> questBySession = new ConcurrentHashMap<>();

    public TextRuntimeApplicationService(
            RuntimeQuestCatalogPort questCatalogPort,
            RuntimeSessionStorePort sessionStorePort,
            AiClient aiClient
    ) {
        this.questCatalogPort = questCatalogPort;
        this.sessionStorePort = sessionStorePort;
        this.aiClient = aiClient;
    }

    @Override
    public List<RuntimeQuestSummary> listRuntimeQuests() {
        return questCatalogPort.findAll().stream()
                .map(def -> new RuntimeQuestSummary(def.id(), def.name(), def.description()))
                .toList();
    }

    @Override
    public RuntimeQuestSummary importRuntimeQuest(RuntimeQuestImportRequest request) {
        World world = new World();

        for (RuntimeQuestImportRequest.LocationView location : request.locations()) {
            world.addLocation(new com.myproject.questservice.textruntime.domain.model.Location(location.id(), location.description()));
        }
        for (RuntimeQuestImportRequest.ItemView item : request.items()) {
            world.addItem(new com.myproject.questservice.textruntime.domain.model.Item(item.id(), item.description()));
        }
        for (RuntimeQuestImportRequest.NpcView npc : request.npcs()) {
            world.addNpc(new com.myproject.questservice.textruntime.domain.model.Npc(npc.id(), npc.description(), npc.dialogue()));
        }
        for (RuntimeQuestImportRequest.ObjectView worldObject : request.worldObjects() == null ? List.<RuntimeQuestImportRequest.ObjectView>of() : request.worldObjects()) {
            world.addWorldObject(new WorldObject(worldObject.id(), worldObject.description()));
        }

        for (Map.Entry<String, List<String>> entry : request.locationItems().entrySet()) {
            String locationId = entry.getKey();
            for (String itemId : entry.getValue() == null ? List.<String>of() : entry.getValue()) {
                world.placeItem(locationId, itemId);
            }
        }
        for (Map.Entry<String, List<String>> entry : request.locationNpcs().entrySet()) {
            String locationId = entry.getKey();
            for (String npcId : entry.getValue() == null ? List.<String>of() : entry.getValue()) {
                world.placeNpc(locationId, npcId);
            }
        }
        for (Map.Entry<String, List<String>> entry : (request.locationObjects() == null ? Map.<String, List<String>>of() : request.locationObjects()).entrySet()) {
            String locationId = entry.getKey();
            for (String objectId : entry.getValue() == null ? List.<String>of() : entry.getValue()) {
                world.placeWorldObject(locationId, objectId);
            }
        }

        for (RuntimeQuestImportRequest.TransitionView transition : request.transitions()) {
            world.addTransition(
                    transition.fromId(),
                    transition.toId(),
                    toCondition(transition.condition())
            );
        }

        for (RuntimeQuestImportRequest.ActionView action : request.actions()) {
            world.addAction(new World.WorldAction(
                    action.id(),
                    action.locationId(),
                    action.description(),
                    toCondition(action.condition()),
                    toEffect(action.effects()),
                    action.requiredItems() == null ? Set.of() : action.requiredItems(),
                    action.targetId(),
                    action.progressFlagsToSet() == null ? Set.of() : action.progressFlagsToSet()
            ));
        }

        for (RuntimeQuestImportRequest.EndingView ending : request.endings()) {
            world.addEnding(new World.Ending(ending.id(), toCondition(ending.condition())));
        }

        List<RuntimeQuestDefinition.Objective> objectives = (request.objectives() == null ? List.<RuntimeQuestImportRequest.ObjectiveView>of() : request.objectives()).stream()
                .map(TextRuntimeApplicationService::toObjective)
                .filter(objective -> objective != null)
                .toList();

        RuntimeQuestDefinition definition = new RuntimeQuestDefinition(
                normalizeQuestId(request.id()),
                request.name(),
                request.description(),
                world,
                request.startLocationId(),
                objectives
        );
        questCatalogPort.save(definition, request);
        return new RuntimeQuestSummary(definition.id(), definition.name(), definition.description());
    }

    @Override
    public RuntimeQuestImportRequest exportRuntimeQuest(String questId) {
        RuntimeQuestImportRequest source = questCatalogPort.findSourceById(normalizeQuestId(questId)).orElse(null);
        if (source != null) {
            return source;
        }

        RuntimeQuestDefinition definition = questCatalogPort.findById(normalizeQuestId(questId))
                .orElseThrow(() -> new NotFoundException("Runtime quest not found"));
        World world = definition.world();

        List<RuntimeQuestImportRequest.LocationView> locations = world.getLocations().stream()
                .map(location -> new RuntimeQuestImportRequest.LocationView(location.getId(), location.getDescription()))
                .toList();

        List<RuntimeQuestImportRequest.ItemView> items = locations.stream()
                .flatMap(location -> world.getInitialItemsInLocation(location.id()).stream())
                .distinct()
                .map(world::getItem)
                .filter(item -> item != null)
                .map(item -> new RuntimeQuestImportRequest.ItemView(item.getId(), item.getDescription()))
                .toList();

        List<RuntimeQuestImportRequest.NpcView> npcs = locations.stream()
                .flatMap(location -> world.getInitialNpcsInLocation(location.id()).stream())
                .distinct()
                .map(world::getNpc)
                .filter(npc -> npc != null)
                .map(npc -> new RuntimeQuestImportRequest.NpcView(npc.getId(), npc.getDescription(), npc.getDialogue()))
                .toList();
        List<RuntimeQuestImportRequest.ObjectView> worldObjects = locations.stream()
                .flatMap(location -> world.getInitialObjectsInLocation(location.id()).stream())
                .distinct()
                .map(world::getWorldObject)
                .filter(worldObject -> worldObject != null)
                .map(worldObject -> new RuntimeQuestImportRequest.ObjectView(worldObject.getId(), worldObject.getDescription()))
                .toList();

        Map<String, List<String>> locationItems = locations.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RuntimeQuestImportRequest.LocationView::id,
                        location -> world.getInitialItemsInLocation(location.id()).stream().toList(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));

        Map<String, List<String>> locationNpcs = locations.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RuntimeQuestImportRequest.LocationView::id,
                        location -> world.getInitialNpcsInLocation(location.id()).stream().toList(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
        Map<String, List<String>> locationObjects = locations.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RuntimeQuestImportRequest.LocationView::id,
                        location -> world.getInitialObjectsInLocation(location.id()).stream().toList(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));

        List<RuntimeQuestImportRequest.TransitionView> transitions = locations.stream()
                .flatMap(location -> world.getTransitionsFrom(location.id()).stream())
                .map(transition -> new RuntimeQuestImportRequest.TransitionView(
                        transition.fromId(),
                        transition.toId(),
                        null,
                        transition.condition() != null
                ))
                .toList();

        List<RuntimeQuestImportRequest.ActionView> actions = world.getActions().stream()
                .map(action -> new RuntimeQuestImportRequest.ActionView(
                        action.id(),
                        action.locationId(),
                        action.description(),
                        action.targetId(),
                        action.requiredItems(),
                        action.progressFlagsToSet(),
                        null,
                        List.of(),
                        action.condition() != null,
                        action.effect() != null
                ))
                .toList();
        List<RuntimeQuestImportRequest.ObjectiveView> objectives = definition.objectives().stream()
                .map(TextRuntimeApplicationService::toObjectiveView)
                .toList();

        List<RuntimeQuestImportRequest.EndingView> endings = world.getEndings().stream()
                .map(ending -> new RuntimeQuestImportRequest.EndingView(ending.id(), null, ending.condition() != null))
                .toList();

        return new RuntimeQuestImportRequest(
                definition.id(),
                definition.name(),
                definition.description(),
                definition.startLocationId(),
                locations,
                items,
                npcs,
                worldObjects,
                locationItems,
                locationNpcs,
                locationObjects,
                transitions,
                actions,
                objectives,
                endings
        );
    }

    @Override
    public RuntimeSnapshot startRuntimeQuest(String questId) {
        RuntimeQuestDefinition definition = questCatalogPort.findById(normalizeQuestId(questId))
                .orElseThrow(() -> new NotFoundException("Runtime quest not found"));
        GameEngine engine = new GameEngine(
                definition.world(),
                new GameState(definition.startLocationId(), new Player())
        );
        UUID sessionId = UUID.randomUUID();
        sessionStorePort.save(sessionId, engine);
        questBySession.put(sessionId, definition);
        generatedBySession.put(sessionId, new ConcurrentHashMap<>());
        refreshGeneratedActions(sessionId, engine);
        return snapshot(sessionId, engine);
    }

    @Override
    public RuntimeSnapshot inspect(UUID sessionId) {
        return snapshot(sessionId, getEngine(sessionId));
    }

    @Override
    public RuntimeSnapshot move(UUID sessionId, String locationId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.move(locationId);
        if (!message.startsWith("Moved to ")) {
            throw new IllegalArgumentException(message);
        }
        refreshGeneratedActions(sessionId, engine);
        return snapshot(sessionId, engine);
    }

    @Override
    public RuntimeSnapshot take(UUID sessionId, String itemId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.take(itemId);
        if (!message.startsWith("Item added to inventory: ")) {
            throw new IllegalArgumentException(message);
        }
        refreshGeneratedActions(sessionId, engine);
        return snapshot(sessionId, engine);
    }

    @Override
    public RuntimeSnapshot use(UUID sessionId, String itemId, String targetId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.use(itemId, targetId);
        if (message.startsWith("No action for using ")
                || message.startsWith("Ambiguous action")
                || message.startsWith("Item is not in inventory: ")
                || message.startsWith("Action is not available in this location")
                || message.startsWith("Action conditions are not met")
                || message.startsWith("Unknown action: ")) {
            throw new IllegalArgumentException(message);
        }
        refreshGeneratedActions(sessionId, engine);
        return snapshot(sessionId, engine);
    }

    @Override
    public RuntimeActionResult interact(UUID sessionId, String targetId) {
        GameEngine engine = getEngine(sessionId);
        GameEngine.InteractionResult result = engine.interactDetailed(targetId);
        String message = result.message();
        if (message.startsWith("No interaction available for: ")
                || message.startsWith("Ambiguous interaction target: ")
                || message.startsWith("Action id must be executed via execute-action: ")
                || message.startsWith("Ambiguous action id: ")
                || message.startsWith("Target is empty")) {
            throw new IllegalArgumentException(message);
        }
        refreshGeneratedActions(sessionId, engine);
        return new RuntimeActionResult(message, snapshot(sessionId, engine), result.engineAction());
    }

    @Override
    public RuntimeSnapshot executeAction(UUID sessionId, String actionId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.executeAction(actionId);
        if (!message.startsWith("Action executed: ")) {
            throw new IllegalArgumentException(message);
        }
        refreshGeneratedActions(sessionId, engine);
        return snapshot(sessionId, engine);
    }

    @Override
    public String inspectTarget(UUID sessionId, String targetId) {
        GameEngine engine = getEngine(sessionId);
        return engine.inspect(targetId);
    }

    @Override
    public RuntimeGenerationStatus generateScene(UUID sessionId) {
        GameEngine engine = getEngine(sessionId);
        RuntimeSnapshot snapshot = snapshot(sessionId, engine);
        String sceneId = snapshot.currentLocationId();
        GeneratedSceneState state = getGeneratedState(sessionId, sceneId);
        JsonNode response = aiClient.generate(
                "Ты генератор сцены текстового квеста. Верни JSON формата {\"scene\":\"...\"}.",
                buildSceneUserPrompt(snapshot)
        );
        String generatedScene = response.path("scene").asText("").trim();
        state.generatedSceneText = generatedScene.isBlank() ? snapshot.description() : generatedScene;
        state.sceneGenerated = true;
        return toStatus(sessionId, sceneId, state);
    }

    @Override
    public RuntimeGenerationStatus generateActions(UUID sessionId) {
        GameEngine engine = getEngine(sessionId);
        refreshGeneratedActions(sessionId, engine);
        RuntimeSnapshot snapshot = snapshot(sessionId, engine);
        String sceneId = snapshot.currentLocationId();
        GeneratedSceneState state = getGeneratedState(sessionId, sceneId);
        return toStatus(sessionId, sceneId, state);
    }

    @Override
    public RuntimeGenerationStatus generationStatus(UUID sessionId) {
        GameEngine engine = getEngine(sessionId);
        refreshGeneratedActions(sessionId, engine);
        String sceneId = engine.inspect().location().getId();
        GeneratedSceneState state = getGeneratedState(sessionId, sceneId);
        return toStatus(sessionId, sceneId, state);
    }

    private void refreshGeneratedActions(UUID sessionId, GameEngine engine) {
        RuntimeSnapshot current = snapshot(sessionId, engine);
        String sceneId = current.currentLocationId();
        GeneratedSceneState state = getGeneratedState(sessionId, sceneId);
        List<RuntimeGenerationStatus.GeneratedAction> actions = new ArrayList<>();
        for (RuntimeSnapshot.ActionView action : current.availableActions()) {
            String actionId = action.id() == null ? "" : action.id().trim();
            String targetId = action.targetId() == null ? "" : action.targetId().trim();
            if (actionId.isBlank()) {
                continue;
            }
            if (actionId.startsWith("move:") && targetId.isBlank()) {
                continue;
            }
            String label = action.description() == null || action.description().isBlank()
                    ? (targetId.isBlank() ? actionId : targetId)
                    : action.description();
            actions.add(new RuntimeGenerationStatus.GeneratedAction(
                    actionId,
                    label,
                    targetId.isBlank() ? null : targetId
            ));
        }
        state.generatedActions = List.copyOf(actions);
        state.actionsGenerated = true;
    }

    private GameEngine getEngine(UUID sessionId) {
        return sessionStorePort.find(sessionId)
                .orElseThrow(() -> new NotFoundException("Runtime session not found"));
    }

    private RuntimeSnapshot snapshot(UUID sessionId, GameEngine engine) {
        GameEngine.InspectResult inspect = engine.inspect();
        List<RuntimeSnapshot.ItemView> items = inspect.visibleItems().stream()
                .map(i -> new RuntimeSnapshot.ItemView(i.getId(), i.getName()))
                .toList();
        List<RuntimeSnapshot.ExitView> exits = inspect.exits().stream()
                .map(e -> new RuntimeSnapshot.ExitView(e.actionText(), e.targetLocationId()))
                .toList();
        List<RuntimeSnapshot.ActionView> worldActions = engine.getAvailableActions().stream()
                .map(a -> new RuntimeSnapshot.ActionView(
                        a.id(),
                        formatActionLabel(a),
                        a.targetId(),
                        List.copyOf(a.requiredItems())
                ))
                .toList();
        // Всегда отдаём действия для UI: world-actions + переходы + предметы + NPC.
        List<RuntimeSnapshot.ActionView> availableActions = new ArrayList<>();
        Set<String> uniqueKeys = new LinkedHashSet<>();
        for (RuntimeSnapshot.ActionView action : worldActions) {
            String key = "wa:" + (action.id() == null ? "" : action.id());
            if (uniqueKeys.add(key)) {
                availableActions.add(action);
            }
        }
        for (RuntimeSnapshot.ExitView exit : exits) {
            String target = exit.targetLocationId();
            if (target == null || target.isBlank()) {
                continue;
            }
            String key = "move:" + target;
            if (uniqueKeys.add(key)) {
                availableActions.add(new RuntimeSnapshot.ActionView(
                        "move:" + target,
                        "Перейти: " + (exit.actionText() == null || exit.actionText().isBlank() ? target : exit.actionText()),
                        target,
                        List.of()
                ));
            }
        }
        for (RuntimeSnapshot.ItemView item : items) {
            if (item.id() == null || item.id().isBlank()) {
                continue;
            }
            String key = "item:" + item.id();
            if (uniqueKeys.add(key)) {
                availableActions.add(new RuntimeSnapshot.ActionView(
                        "item:" + item.id(),
                        "Взаимодействовать с предметом: " + (item.name() == null || item.name().isBlank() ? item.id() : item.name()),
                        item.id(),
                        List.of()
                ));
            }
        }
        List<RuntimeSnapshot.NpcView> npcs = inspect.visibleNpcs().stream()
                .map(n -> new RuntimeSnapshot.NpcView(n.getId(), n.getDescription(), n.getDialogue()))
                .toList();
        List<RuntimeSnapshot.ObjectView> objects = inspect.visibleObjects().stream()
                .map(o -> new RuntimeSnapshot.ObjectView(o.getId(), o.getDescription()))
                .toList();
        for (RuntimeSnapshot.NpcView npc : npcs) {
            if (npc.id() == null || npc.id().isBlank()) {
                continue;
            }
            String key = "npc:" + npc.id();
            if (uniqueKeys.add(key)) {
                availableActions.add(new RuntimeSnapshot.ActionView(
                        "npc:" + npc.id(),
                        "Поговорить: " + npc.id(),
                        npc.id(),
                        List.of()
                ));
            }
        }
        List<RuntimeSnapshot.ItemView> inventory = inspect.inventory().stream()
                .map(i -> new RuntimeSnapshot.ItemView(i.getId(), i.getName()))
                .toList();
        RuntimeQuestDefinition definition = questBySession.get(sessionId);
        List<RuntimeSnapshot.ObjectiveView> objectives = definition == null ? List.of() : definition.objectives().stream()
                .map(objective -> toSnapshotObjective(objective, engine.getState(), definition.world()))
                .toList();
        return new RuntimeSnapshot(
                sessionId,
                inspect.location().getId(),
                inspect.location().getDescription(),
                items,
                exits,
                availableActions,
                inventory,
                npcs,
                objects,
                objectives,
                List.copyOf(engine.getState().getKnownFacts()),
                Map.copyOf(engine.getState().getObjectStates()),
                Map.copyOf(engine.getState().getCharacterStates())
        );
    }

    private GeneratedSceneState getGeneratedState(UUID sessionId, String sceneId) {
        Map<String, GeneratedSceneState> byScene = generatedBySession.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>());
        return byScene.computeIfAbsent(sceneId, ignored -> new GeneratedSceneState());
    }

    private RuntimeGenerationStatus toStatus(UUID sessionId, String sceneId, GeneratedSceneState state) {
        List<RuntimeGenerationStatus.GeneratedAction> safeActions = (state.generatedActions == null ? List.<RuntimeGenerationStatus.GeneratedAction>of() : state.generatedActions)
                .stream()
                .filter(action -> action != null && action.id() != null)
                .toList();
        return new RuntimeGenerationStatus(
                sessionId,
                sceneId,
                state.sceneGenerated,
                state.actionsGenerated,
                state.generatedSceneText,
                safeActions
        );
    }

    private static String buildSceneUserPrompt(RuntimeSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Текущая сцена: ").append(snapshot.currentLocationId()).append('\n');
        sb.append("Описание: ").append(snapshot.description()).append('\n');
        sb.append("Инвентарь: ").append(snapshot.inventory().stream().map(RuntimeSnapshot.ItemView::name).toList()).append('\n');
        sb.append("NPC: ").append(snapshot.npcs().stream().map(RuntimeSnapshot.NpcView::id).toList()).append('\n');
        sb.append("Objects: ").append(snapshot.objects().stream().map(RuntimeSnapshot.ObjectView::id).toList()).append('\n');
        sb.append("Переходы: ").append(snapshot.exits().stream().map(RuntimeSnapshot.ExitView::targetLocationId).toList()).append('\n');
        sb.append("Сделай короткое, атмосферное, но игровое описание этой сцены.");
        return sb.toString();
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static RuntimeQuestDefinition.Objective toObjective(RuntimeQuestImportRequest.ObjectiveView view) {
        if (view == null) {
            return null;
        }
        List<RuntimeQuestDefinition.Objective> children = (view.children() == null ? List.<RuntimeQuestImportRequest.ObjectiveView>of() : view.children()).stream()
                .map(TextRuntimeApplicationService::toObjective)
                .filter(child -> child != null)
                .toList();
        return new RuntimeQuestDefinition.Objective(
                view.id(),
                view.title(),
                view.description(),
                toCondition(view.condition()),
                children
        );
    }

    private static RuntimeQuestImportRequest.ObjectiveView toObjectiveView(RuntimeQuestDefinition.Objective objective) {
        List<RuntimeQuestImportRequest.ObjectiveView> children = objective.children().stream()
                .map(TextRuntimeApplicationService::toObjectiveView)
                .toList();
        return new RuntimeQuestImportRequest.ObjectiveView(
                objective.id(),
                objective.title(),
                objective.description(),
                null,
                objective.condition() != null,
                children
        );
    }

    private static RuntimeSnapshot.ObjectiveView toSnapshotObjective(RuntimeQuestDefinition.Objective objective, GameState state, World world) {
        List<RuntimeSnapshot.ObjectiveView> children = objective.children().stream()
                .map(child -> toSnapshotObjective(child, state, world))
                .toList();
        boolean selfCompleted = objective.condition() != null && objective.condition().test(state, world);
        boolean childrenCompleted = !children.isEmpty() && children.stream().allMatch(RuntimeSnapshot.ObjectiveView::completed);
        boolean completed = selfCompleted || childrenCompleted;
        return new RuntimeSnapshot.ObjectiveView(
                objective.id(),
                objective.title(),
                objective.description(),
                completed,
                children
        );
    }

    private static World.Condition toCondition(RuntimeQuestImportRequest.ConditionSpec spec) {
        if (spec == null || spec.type() == null || spec.type().isBlank()) {
            return null;
        }
        return switch (spec.type().trim().toUpperCase()) {
            case "ALWAYS" -> (state, world) -> true;
            case "HAS_ITEM" -> (state, world) -> spec.value() != null && state.getPlayer().getInventory().contains(spec.value());
            case "HAS_FACT" -> (state, world) -> spec.value() != null && state.getKnownFacts().contains(spec.value());
            case "FLAG" -> (state, world) -> spec.value() != null && state.getProgressFlags().contains(spec.value());
            case "OBJECT_STATE" -> (state, world) -> spec.key() != null && spec.value() != null && spec.value().equals(state.getObjectStates().get(spec.key()));
            case "CHARACTER_STATE" -> (state, world) -> spec.key() != null && spec.value() != null && spec.value().equals(state.getCharacterStates().get(spec.key()));
            case "NOT" -> {
                World.Condition child = toCondition(spec.condition());
                yield child == null ? null : (state, world) -> !child.test(state, world);
            }
            case "AND" -> {
                List<World.Condition> children = (spec.conditions() == null ? List.<RuntimeQuestImportRequest.ConditionSpec>of() : spec.conditions())
                        .stream()
                        .map(TextRuntimeApplicationService::toCondition)
                        .filter(c -> c != null)
                        .toList();
                yield (state, world) -> children.stream().allMatch(c -> c.test(state, world));
            }
            case "OR" -> {
                List<World.Condition> children = (spec.conditions() == null ? List.<RuntimeQuestImportRequest.ConditionSpec>of() : spec.conditions())
                        .stream()
                        .map(TextRuntimeApplicationService::toCondition)
                        .filter(c -> c != null)
                        .toList();
                yield (state, world) -> children.stream().anyMatch(c -> c.test(state, world));
            }
            default -> null;
        };
    }

    private static World.Effect toEffect(List<RuntimeQuestImportRequest.EffectSpec> specs) {
        List<RuntimeQuestImportRequest.EffectSpec> effects = specs == null ? List.of() : specs;
        if (effects.isEmpty()) {
            return null;
        }
        return (state, world) -> {
            for (RuntimeQuestImportRequest.EffectSpec effect : effects) {
                if (effect == null || effect.type() == null || effect.type().isBlank()) {
                    continue;
                }
                switch (effect.type().trim().toUpperCase()) {
                    case "SET_OBJECT_STATE" -> {
                        if (effect.key() != null && effect.value() != null) {
                            state.getObjectStates().put(effect.key(), effect.value());
                        }
                    }
                    case "SET_CHARACTER_STATE" -> {
                        if (effect.key() != null && effect.value() != null) {
                            state.getCharacterStates().put(effect.key(), effect.value());
                        }
                    }
                    case "ADD_FACT" -> {
                        if (effect.value() != null) {
                            state.getKnownFacts().add(effect.value());
                        }
                    }
                    case "ADD_FLAG" -> {
                        if (effect.value() != null) {
                            state.getProgressFlags().add(effect.value());
                        }
                    }
                    case "ADD_ITEM" -> {
                        if (effect.value() != null) {
                            state.getPlayer().getInventory().add(effect.value());
                        }
                    }
                    case "REMOVE_ITEM" -> {
                        if (effect.value() != null) {
                            state.getPlayer().getInventory().remove(effect.value());
                        }
                    }
                    default -> {
                    }
                }
            }
        };
    }

    private static String formatActionLabel(com.myproject.questservice.textruntime.domain.model.World.WorldAction action) {
        String targetId = action.targetId() == null ? "" : action.targetId().trim();
        List<String> requiredItems = action.requiredItems() == null ? List.of() : action.requiredItems().stream()
                .filter(item -> item != null && !item.isBlank())
                .toList();

        if (!requiredItems.isEmpty() && !targetId.isBlank()) {
            if (requiredItems.size() == 1) {
                return "Применить " + requiredItems.get(0) + " к " + targetId;
            }
            return "Применить " + String.join(", ", requiredItems) + " к " + targetId;
        }

        if (!targetId.isBlank()) {
            String description = action.description() == null ? "" : action.description().trim();
            if (!description.isBlank()) {
                return description;
            }
            return "Взаимодействовать: " + targetId;
        }

        String description = action.description() == null ? "" : action.description().trim();
        return description.isBlank() ? action.id() : description;
    }

    private static final class GeneratedSceneState {
        private volatile boolean sceneGenerated;
        private volatile boolean actionsGenerated;
        private volatile String generatedSceneText;
        private volatile List<RuntimeGenerationStatus.GeneratedAction> generatedActions = Collections.emptyList();
    }
}
