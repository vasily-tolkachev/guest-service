package com.myproject.questservice.textruntime.application.service;

import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.textruntime.application.port.in.TextRuntimeUseCase;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeActionResult;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestSummary;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeSnapshot;
import com.myproject.questservice.textruntime.application.port.out.RuntimeQuestCatalogPort;
import com.myproject.questservice.textruntime.application.port.out.RuntimeSessionStorePort;
import com.myproject.questservice.textruntime.domain.model.GameState;
import com.myproject.questservice.textruntime.domain.model.Player;
import com.myproject.questservice.textruntime.domain.model.RuntimeQuestDefinition;
import com.myproject.questservice.textruntime.domain.service.GameEngine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TextRuntimeApplicationService implements TextRuntimeUseCase {
    private final RuntimeQuestCatalogPort questCatalogPort;
    private final RuntimeSessionStorePort sessionStorePort;

    public TextRuntimeApplicationService(
            RuntimeQuestCatalogPort questCatalogPort,
            RuntimeSessionStorePort sessionStorePort
    ) {
        this.questCatalogPort = questCatalogPort;
        this.sessionStorePort = sessionStorePort;
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
        return snapshot(sessionId, engine.inspect());
    }

    @Override
    public RuntimeSnapshot inspect(UUID sessionId) {
        return snapshot(sessionId, getEngine(sessionId).inspect());
    }

    @Override
    public RuntimeSnapshot move(UUID sessionId, String locationId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.move(locationId);
        if (!message.startsWith("Moved to ")) {
            throw new IllegalArgumentException(message);
        }
        return snapshot(sessionId, engine.inspect());
    }

    @Override
    public RuntimeSnapshot take(UUID sessionId, String itemId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.take(itemId);
        if (!message.startsWith("Item added to inventory: ")) {
            throw new IllegalArgumentException(message);
        }
        return snapshot(sessionId, engine.inspect());
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
        return snapshot(sessionId, engine.inspect());
    }

    @Override
    public RuntimeActionResult interact(UUID sessionId, String targetId) {
        GameEngine engine = getEngine(sessionId);
        String message = engine.interact(targetId);
        if (message.startsWith("No interaction available for: ")
                || message.startsWith("Ambiguous interaction target: ")
                || message.startsWith("Target is empty")) {
            throw new IllegalArgumentException(message);
        }
        return new RuntimeActionResult(message, snapshot(sessionId, engine.inspect()));
    }

    @Override
    public String inspectTarget(UUID sessionId, String targetId) {
        GameEngine engine = getEngine(sessionId);
        return engine.inspect(targetId);
    }

    private GameEngine getEngine(UUID sessionId) {
        return sessionStorePort.find(sessionId)
                .orElseThrow(() -> new NotFoundException("Runtime session not found"));
    }

    private RuntimeSnapshot snapshot(UUID sessionId, GameEngine.InspectResult inspect) {
        List<RuntimeSnapshot.ItemView> items = inspect.visibleItems().stream()
                .map(i -> new RuntimeSnapshot.ItemView(i.getId(), i.getName()))
                .toList();
        List<RuntimeSnapshot.ExitView> exits = inspect.exits().stream()
                .map(e -> new RuntimeSnapshot.ExitView(e.actionText(), e.targetLocationId()))
                .toList();
        List<RuntimeSnapshot.ItemView> inventory = inspect.inventory().stream()
                .map(i -> new RuntimeSnapshot.ItemView(i.getId(), i.getName()))
                .toList();
        List<RuntimeSnapshot.NpcView> npcs = inspect.visibleNpcs().stream()
                .map(n -> new RuntimeSnapshot.NpcView(n.getId(), n.getDescription(), n.getDialogue()))
                .toList();
        return new RuntimeSnapshot(
                sessionId,
                inspect.location().getId(),
                inspect.location().getDescription(),
                items,
                exits,
                inventory,
                npcs
        );
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
