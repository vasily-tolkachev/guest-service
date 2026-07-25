package com.myproject.questservice.textruntime.application.service.compilation;

import com.myproject.questservice.textruntime.application.service.compilation.model.SceneGenerationRequest;
import com.myproject.questservice.textruntime.application.service.compilation.model.SceneKey;
import com.myproject.questservice.textruntime.application.service.compilation.support.GameStateCloner;
import com.myproject.questservice.textruntime.application.service.compilation.support.StateSignatureBuilder;
import com.myproject.questservice.textruntime.domain.model.GameState;
import com.myproject.questservice.textruntime.domain.model.World;
import com.myproject.questservice.textruntime.domain.service.GameEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class QuestExplorer {
    public int explore(
            String questId,
            World world,
            GameState startState,
            Consumer<SceneGenerationRequest> onStateDiscovered
    ) {
        Set<SceneKey> visited = new HashSet<>();
        Deque<GameState> stack = new ArrayDeque<>();
        stack.push(GameStateCloner.cloneState(startState));

        while (!stack.isEmpty()) {
            GameState current = stack.pop();
            GameEngine engine = new GameEngine(world, current);
            GameEngine.InspectResult inspect = engine.inspect();
            SceneKey key = new SceneKey(
                    questId,
                    current.getCurrentLocation(),
                    StateSignatureBuilder.build(current)
            );
            if (!visited.add(key)) {
                continue;
            }

            List<String> actionIds = engine.getAvailableActions().stream()
                    .map(World.WorldAction::id)
                    .toList();
            onStateDiscovered.accept(new SceneGenerationRequest(
                    key,
                    inspect.location().getDescription(),
                    inspect.visibleItems().stream().map(i -> i.getId()).toList(),
                    inspect.visibleNpcs().stream().map(n -> n.getId()).toList(),
                    inspect.exits().stream().map(GameEngine.ExitView::targetLocationId).toList(),
                    actionIds,
                    inspect.inventory().stream().map(i -> i.getId()).toList(),
                    Set.copyOf(current.getProgressFlags()),
                    Set.copyOf(current.getKnownFacts()),
                    Map.copyOf(current.getObjectStates()),
                    Map.copyOf(current.getCharacterStates())
            ));

            List<GameState> nextStates = buildNextStates(world, current, inspect, actionIds);
            for (GameState next : nextStates) {
                stack.push(next);
            }
        }

        return visited.size();
    }

    private List<GameState> buildNextStates(
            World world,
            GameState source,
            GameEngine.InspectResult inspect,
            List<String> actionIds
    ) {
        List<GameState> next = new ArrayList<>();

        for (GameEngine.ExitView exit : inspect.exits()) {
            GameState cloned = GameStateCloner.cloneState(source);
            GameEngine engine = new GameEngine(world, cloned);
            String result = engine.move(exit.targetLocationId());
            if (result.startsWith("Moved to ")) {
                next.add(cloned);
            }
        }

        for (var item : inspect.visibleItems()) {
            GameState cloned = GameStateCloner.cloneState(source);
            GameEngine engine = new GameEngine(world, cloned);
            String result = engine.take(item.getId());
            if (result.startsWith("Item added to inventory: ")) {
                next.add(cloned);
            }
        }

        for (var npc : inspect.visibleNpcs()) {
            GameState cloned = GameStateCloner.cloneState(source);
            GameEngine engine = new GameEngine(world, cloned);
            String result = engine.talk(npc.getId());
            if (!result.startsWith("NPC is not here: ") && !result.startsWith("Unknown NPC: ")) {
                next.add(cloned);
            }
        }

        for (String actionId : actionIds) {
            GameState cloned = GameStateCloner.cloneState(source);
            GameEngine engine = new GameEngine(world, cloned);
            String result = engine.executeAction(actionId);
            if (result.startsWith("Action executed: ")) {
                next.add(cloned);
            }
        }

        return next;
    }
}
