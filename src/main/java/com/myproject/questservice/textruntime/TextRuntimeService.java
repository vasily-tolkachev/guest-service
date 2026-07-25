package com.myproject.questservice.textruntime;

import com.myproject.questservice.application.service.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TextRuntimeService {
    private final Map<UUID, RuntimeSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, SampleRuntimeQuests.RuntimeQuestDefinition> runtimeQuests;

    public TextRuntimeService() {
        this.runtimeQuests = Collections.unmodifiableMap(SampleRuntimeQuests.definitions());
    }

    public List<RuntimeQuestSummary> listRuntimeQuests() {
        List<RuntimeQuestSummary> result = new ArrayList<>();
        for (SampleRuntimeQuests.RuntimeQuestDefinition def : runtimeQuests.values()) {
            result.add(new RuntimeQuestSummary(def.id(), def.name(), def.description()));
        }
        return result;
    }

    public RuntimeSnapshot startRuntimeQuest(String questId) {
        SampleRuntimeQuests.RuntimeQuestDefinition def = runtimeQuests.get(normalizeQuestId(questId));
        if (def == null) {
            throw new NotFoundException("Runtime quest not found");
        }
        GameEngine engine = new GameEngine(def.world(), new GameState(def.startLocationId()));
        UUID sessionId = UUID.randomUUID();
        sessions.put(sessionId, new RuntimeSession(sessionId, def.world(), engine));
        return snapshot(sessionId, engine.inspect());
    }

    public RuntimeSnapshot inspect(UUID sessionId) {
        return snapshot(sessionId, getSession(sessionId).engine.inspect());
    }

    public RuntimeSnapshot move(UUID sessionId, String locationId) {
        RuntimeSession session = getSession(sessionId);
        session.engine.move(locationId);
        return snapshot(sessionId, session.engine.inspect());
    }

    public RuntimeSnapshot take(UUID sessionId, String itemId) {
        RuntimeSession session = getSession(sessionId);
        session.engine.take(itemId);
        return snapshot(sessionId, session.engine.inspect());
    }

    public RuntimeSnapshot use(UUID sessionId, String itemId, String targetId) {
        RuntimeSession session = getSession(sessionId);
        session.engine.use(itemId, targetId);
        return snapshot(sessionId, session.engine.inspect());
    }

    private RuntimeSession getSession(UUID sessionId) {
        RuntimeSession session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException("Runtime session not found");
        }
        return session;
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
        return new RuntimeSnapshot(
                sessionId,
                inspect.location().getId(),
                inspect.location().getDescription(),
                items,
                exits,
                inventory
        );
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record RuntimeSession(
            UUID sessionId,
            World world,
            GameEngine engine
    ) {
    }
}
