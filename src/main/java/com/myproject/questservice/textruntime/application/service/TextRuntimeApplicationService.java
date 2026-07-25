package com.myproject.questservice.textruntime.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.textruntime.application.port.in.TextRuntimeUseCase;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeActionResult;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeGenerationStatus;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestSummary;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeSnapshot;
import com.myproject.questservice.textruntime.application.port.out.RuntimeQuestCatalogPort;
import com.myproject.questservice.textruntime.application.port.out.RuntimeSessionStorePort;
import com.myproject.questservice.textruntime.domain.model.GameState;
import com.myproject.questservice.textruntime.domain.model.Player;
import com.myproject.questservice.textruntime.domain.model.RuntimeQuestDefinition;
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
    public RuntimeSnapshot startRuntimeQuest(String questId) {
        RuntimeQuestDefinition definition = questCatalogPort.findById(normalizeQuestId(questId))
                .orElseThrow(() -> new NotFoundException("Runtime quest not found"));
        GameEngine engine = new GameEngine(
                definition.world(),
                new GameState(definition.startLocationId(), new Player())
        );
        UUID sessionId = UUID.randomUUID();
        sessionStorePort.save(sessionId, engine);
        generatedBySession.put(sessionId, new ConcurrentHashMap<>());
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
        return snapshot(sessionId, engine);
    }

    @Override
    public RuntimeSnapshot take(UUID sessionId, String itemId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.take(itemId);
        if (!message.startsWith("Item added to inventory: ")) {
            throw new IllegalArgumentException(message);
        }
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
        return snapshot(sessionId, engine);
    }

    @Override
    public RuntimeActionResult interact(UUID sessionId, String targetId) {
        GameEngine engine = getEngine(sessionId);
        GameEngine.InteractionResult result = engine.interactDetailed(targetId);
        String message = result.message();
        if (message.startsWith("No interaction available for: ")
                || message.startsWith("Ambiguous interaction target: ")
                || message.startsWith("Target is empty")) {
            throw new IllegalArgumentException(message);
        }
        return new RuntimeActionResult(message, snapshot(sessionId, engine), result.engineAction());
    }

    @Override
    public RuntimeSnapshot executeAction(UUID sessionId, String actionId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.executeAction(actionId);
        if (!message.startsWith("Action executed: ")) {
            throw new IllegalArgumentException(message);
        }
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
        RuntimeSnapshot snapshot = snapshot(sessionId, engine);
        String sceneId = snapshot.currentLocationId();
        GeneratedSceneState state = getGeneratedState(sessionId, sceneId);
        List<RuntimeGenerationStatus.GeneratedAction> actions = new ArrayList<>();
        for (RuntimeSnapshot.ActionView action : snapshot.availableActions()) {
            String targetId = action.targetId();
            if (targetId == null || targetId.isBlank()) {
                continue;
            }
            actions.add(new RuntimeGenerationStatus.GeneratedAction(
                    action.id() == null || action.id().isBlank() ? "generated:" + targetId : action.id(),
                    action.description() == null || action.description().isBlank() ? targetId : action.description(),
                    targetId
            ));
        }
        state.generatedActions = List.copyOf(actions);
        state.actionsGenerated = true;
        return toStatus(sessionId, sceneId, state);
    }

    @Override
    public RuntimeGenerationStatus generationStatus(UUID sessionId) {
        GameEngine engine = getEngine(sessionId);
        String sceneId = engine.inspect().location().getId();
        GeneratedSceneState state = getGeneratedState(sessionId, sceneId);
        return toStatus(sessionId, sceneId, state);
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
        return new RuntimeSnapshot(
                sessionId,
                inspect.location().getId(),
                inspect.location().getDescription(),
                items,
                exits,
                availableActions,
                inventory,
                npcs,
                List.copyOf(engine.getState().getKnownFacts())
        );
    }

    private GeneratedSceneState getGeneratedState(UUID sessionId, String sceneId) {
        Map<String, GeneratedSceneState> byScene = generatedBySession.computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>());
        return byScene.computeIfAbsent(sceneId, ignored -> new GeneratedSceneState());
    }

    private RuntimeGenerationStatus toStatus(UUID sessionId, String sceneId, GeneratedSceneState state) {
        return new RuntimeGenerationStatus(
                sessionId,
                sceneId,
                state.sceneGenerated,
                state.actionsGenerated,
                state.generatedSceneText,
                state.generatedActions == null ? List.of() : state.generatedActions
        );
    }

    private static String buildSceneUserPrompt(RuntimeSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("Текущая сцена: ").append(snapshot.currentLocationId()).append('\n');
        sb.append("Описание: ").append(snapshot.description()).append('\n');
        sb.append("Инвентарь: ").append(snapshot.inventory().stream().map(RuntimeSnapshot.ItemView::name).toList()).append('\n');
        sb.append("NPC: ").append(snapshot.npcs().stream().map(RuntimeSnapshot.NpcView::id).toList()).append('\n');
        sb.append("Переходы: ").append(snapshot.exits().stream().map(RuntimeSnapshot.ExitView::targetLocationId).toList()).append('\n');
        sb.append("Сделай короткое, атмосферное, но игровое описание этой сцены.");
        return sb.toString();
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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
