package com.myproject.questservice.textruntime;

import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.generator.ProjectRepository;
import com.myproject.questservice.domain.generator.NodeWorkspace;
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.WorkspaceAction;
import com.myproject.questservice.domain.generator.WorkspaceNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TextRuntimeService {
    private final ProjectRepository projectRepository;
    private final Map<UUID, RuntimeSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, SampleRuntimeQuests.RuntimeQuestDefinition> runtimeQuests;

    public TextRuntimeService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
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
        sessions.put(sessionId, new RuntimeSession(sessionId, null, def.world(), engine));
        return snapshot(sessionId, engine.inspect());
    }

    public RuntimeSnapshot start(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        NodeWorkspace workspace = project.getNodeWorkspace();
        if (workspace == null || workspace.getNodes() == null || workspace.getNodes().isEmpty()) {
            throw new NotFoundException("Workspace is empty");
        }

        World world = toWorld(workspace);
        String startNodeId = resolveStartNodeId(workspace.getNodes());
        GameEngine engine = new GameEngine(world, new GameState(startNodeId));
        UUID sessionId = UUID.randomUUID();
        sessions.put(sessionId, new RuntimeSession(sessionId, projectId, world, engine));
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

    private World toWorld(NodeWorkspace workspace) {
        List<WorkspaceNode> nodes = workspace.getNodes() == null ? List.of() : workspace.getNodes();
        Map<String, WorkspaceNode> byId = new LinkedHashMap<>();
        for (WorkspaceNode node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            byId.put(normalize(node.getId()), node);
        }

        Map<String, Map<String, String>> linksBySource = new HashMap<>();
        for (WorkspaceNode node : nodes) {
            String sourceId = normalize(node.getSourceNodeId());
            String actionId = normalize(node.getSourceActionId());
            String targetId = normalize(node.getId());
            if (sourceId.isBlank() || actionId.isBlank() || targetId.isBlank()) {
                continue;
            }
            linksBySource.computeIfAbsent(sourceId, k -> new HashMap<>()).put(actionId, targetId);
        }

        Map<String, Location> locations = new LinkedHashMap<>();
        for (WorkspaceNode node : byId.values()) {
            String locationId = normalize(node.getId());
            String description = firstNonBlank(node.getStateDescription(), node.getActionDescription(), "...");
            List<Item> items = new ArrayList<>();
            List<Location.Exit> exits = new ArrayList<>();
            Map<String, String> targets = linksBySource.getOrDefault(locationId, Map.of());
            if (node.getActions() != null) {
                for (WorkspaceAction action : node.getActions()) {
                    String actionText = action.getText() == null ? "" : action.getText().trim();
                    if (actionText.isBlank()) {
                        continue;
                    }
                    String actionId = normalize(action.getId());
                    String target = targets.get(actionId);
                    exits.add(new Location.Exit(actionText, target));
                }
            }
            locations.put(locationId, new Location(locationId, description, items, exits));
        }
        return new World(locations);
    }

    private String resolveStartNodeId(List<WorkspaceNode> nodes) {
        for (WorkspaceNode node : nodes) {
            String sourceNode = normalize(node.getSourceNodeId());
            String nodeId = normalize(node.getId());
            if (sourceNode.isBlank() && !nodeId.isBlank()) {
                return nodeId;
            }
        }
        for (WorkspaceNode node : nodes) {
            String nodeId = normalize(node.getId());
            if (!nodeId.isBlank()) {
                return nodeId;
            }
        }
        throw new NotFoundException("Workspace has no valid scene ids");
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.trim().isBlank()) return a.trim();
        if (b != null && !b.trim().isBlank()) return b.trim();
        return fallback;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record RuntimeSession(
            UUID sessionId,
            UUID projectId,
            World world,
            GameEngine engine
    ) {
    }
}
