package com.myproject.questservice.textruntime.adapter.out.inmemory;

import com.myproject.questservice.textruntime.application.port.out.RuntimeQuestCatalogPort;
import com.myproject.questservice.textruntime.domain.model.Item;
import com.myproject.questservice.textruntime.domain.model.Location;
import com.myproject.questservice.textruntime.domain.model.Npc;
import com.myproject.questservice.textruntime.domain.model.RuntimeQuestDefinition;
import com.myproject.questservice.textruntime.domain.model.World;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class InMemoryRuntimeQuestCatalogAdapter implements RuntimeQuestCatalogPort {
    private final Map<String, RuntimeQuestDefinition> quests = definitions();

    @Override
    public List<RuntimeQuestDefinition> findAll() {
        return quests.values().stream().toList();
    }

    @Override
    public Optional<RuntimeQuestDefinition> findById(String questId) {
        return Optional.ofNullable(quests.get(normalizeQuestId(questId)));
    }

    private static Map<String, RuntimeQuestDefinition> definitions() {
        Map<String, RuntimeQuestDefinition> map = new LinkedHashMap<>();

        WorldSeed islandSeed = new WorldSeed(
                List.of(
                        new LocationSeed("shore", "You stand on a stormy shore after a shipwreck."),
                        new LocationSeed("forest", "Dark forest with strange marks on trees."),
                        new LocationSeed("cave", "Cold cave with dripping water."),
                        new LocationSeed("ruins", "Ancient ruins with a sealed gate.")
                ),
                List.of(
                        new ItemSeed("compass", "A small metal compass.", "shore"),
                        new ItemSeed("key", "An old rusty key.", "cave")
                ),
                List.of(
                        new NpcSeed("survivor", "An exhausted survivor by a fire.", "I saw lights near the ruins at night.", "forest")
                ),
                List.of(
                        new TransitionSeed("shore", "forest", null),
                        new TransitionSeed("forest", "shore", null),
                        new TransitionSeed("forest", "cave", null),
                        new TransitionSeed("cave", "forest", null),
                        new TransitionSeed("forest", "ruins", "ruins_gate_open"),
                        new TransitionSeed("ruins", "forest", null)
                ),
                List.of(
                        new ActionSeed(
                                "open_ruins_gate",
                                "forest",
                                "Open the ruins gate with key",
                                "has_key",
                                "open_ruins_gate",
                                Set.of("key"),
                                "gate",
                                Set.of("gate_opened")
                        )
                )
        );

        WorldSeed stationZeroSeed = new WorldSeed(
                List.of(
                        new LocationSeed("lobby", "You are in the empty station lobby."),
                        new LocationSeed("archive", "Dusty archive with old logs and route maps."),
                        new LocationSeed("workshop", "Workshop with broken equipment and spare parts."),
                        new LocationSeed("server", "Server room door is sealed. Access terminal is nearby."),
                        new LocationSeed("airlock", "Airlock control panel blinks in emergency mode.")
                ),
                List.of(
                        new ItemSeed("access_card", "Service access card.", "archive"),
                        new ItemSeed("fuse", "Industrial power fuse.", "workshop")
                ),
                List.of(
                        new NpcSeed("operator", "Remote operator over intercom.", "If you restore safe power, I can guide evacuation.", "lobby"),
                        new NpcSeed("technician", "Injured technician near tools.", "I can bypass safety if you want sabotage.", "workshop")
                ),
                List.of(
                        new TransitionSeed("lobby", "archive", null),
                        new TransitionSeed("archive", "lobby", null),
                        new TransitionSeed("lobby", "workshop", null),
                        new TransitionSeed("workshop", "lobby", null),
                        new TransitionSeed("lobby", "server", "server_unlocked"),
                        new TransitionSeed("server", "lobby", null),
                        new TransitionSeed("server", "airlock", "airlock_path_open"),
                        new TransitionSeed("airlock", "server", null)
                ),
                List.of(
                        // Objects/locations/NPCs lead to different endings.
                        new ActionSeed("unlock_server", "lobby", "Unlock server room with access card", "has_access_card", "unlock_server", Set.of("access_card"), "terminal", Set.of("path_server")),
                        new ActionSeed("restore_airlock_power", "server", "Install fuse and restore airlock power", "has_fuse", "restore_airlock_power", Set.of("fuse"), "power_bus", Set.of("path_airlock")),
                        new ActionSeed("operator_route", "lobby", "Ask operator for evacuation protocol", null, "set_evac_route", Set.of(), "operator", Set.of("route_known")),
                        new ActionSeed("enable_bypass", "workshop", "Ask technician to enable bypass", null, "enable_bypass", Set.of(), "technician", Set.of("bypass_enabled")),
                        // Ending 1: evacuation
                        new ActionSeed("evacuate", "airlock", "Launch evacuation", "can_evacuate", "finish_evacuation", Set.of(), "airlock_panel", Set.of("ending_evac_ready")),
                        // Ending 2: stabilize station
                        new ActionSeed("stabilize_core", "server", "Stabilize station core", "can_stabilize", "stabilize_core", Set.of(), "core_console", Set.of("ending_stabilize_ready")),
                        // Ending 3: sabotage
                        new ActionSeed("overload_core", "server", "Overload station core", "can_overload", "overload_core", Set.of(), "core_console", Set.of("ending_sabotage_ready"))
                )
        );

        map.put("island_escape", new RuntimeQuestDefinition(
                "island_escape",
                "Island Escape",
                "Short island quest.",
                buildWorldFromDefinition(islandSeed),
                "shore"
        ));
        map.put("shipwreck", new RuntimeQuestDefinition(
                "shipwreck",
                "Shipwreck",
                "Compatibility id for old links.",
                buildWorldFromDefinition(islandSeed),
                "shore"
        ));
        map.put("station_zero", new RuntimeQuestDefinition(
                "station_zero",
                "Station Zero",
                "Small test quest with multiple endings and routes.",
                buildWorldFromDefinition(stationZeroSeed),
                "lobby"
        ));
        return map;
    }

    private static World buildWorldFromDefinition(WorldSeed seed) {
        World world = new World();
        for (LocationSeed location : seed.locations()) {
            world.addLocation(new Location(location.id(), location.description()));
        }
        for (ItemSeed item : seed.items()) {
            world.addItem(new Item(item.id(), item.description()));
            world.placeItem(item.locationId(), item.id());
        }
        for (NpcSeed npc : seed.npcs()) {
            world.addNpc(new Npc(npc.id(), npc.description(), npc.dialogue()));
            world.placeNpc(npc.locationId(), npc.id());
        }
        for (TransitionSeed transition : seed.transitions()) {
            world.addTransition(transition.fromId(), transition.toId(), resolveCondition(transition.conditionKey()));
        }
        for (ActionSeed action : seed.actions()) {
            world.addAction(new World.WorldAction(
                    action.id(),
                    action.locationId(),
                    action.description(),
                    resolveCondition(action.conditionKey()),
                    resolveEffect(action.effectKey()),
                    action.requiredItems(),
                    action.targetId(),
                    action.progressFlags()
            ));
        }
        return world;
    }

    private static World.Condition resolveCondition(String conditionKey) {
        if (conditionKey == null || conditionKey.isBlank()) {
            return null;
        }
        return switch (conditionKey) {
            case "ruins_gate_open" -> (state, ignored) -> "open".equals(state.getObjectStates().get("ruins_gate"));
            case "has_key" -> (state, ignored) -> state.getPlayer().getInventory().contains("key");
            case "has_access_card" -> (state, ignored) -> state.getPlayer().getInventory().contains("access_card");
            case "has_fuse" -> (state, ignored) -> state.getPlayer().getInventory().contains("fuse");
            case "server_unlocked" -> (state, ignored) -> "open".equals(state.getObjectStates().get("server_door"));
            case "airlock_path_open" -> (state, ignored) -> "restored".equals(state.getObjectStates().get("airlock_power"));
            case "can_evacuate" -> (state, ignored) -> "known".equals(state.getObjectStates().get("evac_route"))
                    && "restored".equals(state.getObjectStates().get("airlock_power"));
            case "can_stabilize" -> (state, ignored) -> "open".equals(state.getObjectStates().get("server_door"))
                    && "online".equals(state.getObjectStates().get("operator_state"));
            case "can_overload" -> (state, ignored) -> "open".equals(state.getObjectStates().get("server_door"))
                    && "bypass".equals(state.getObjectStates().get("tech_mode"));
            default -> null;
        };
    }

    private static World.Effect resolveEffect(String effectKey) {
        if (effectKey == null || effectKey.isBlank()) {
            return null;
        }
        return switch (effectKey) {
            case "open_ruins_gate" -> (state, ignored) -> state.getObjectStates().put("ruins_gate", "open");
            case "unlock_server" -> (state, ignored) -> state.getObjectStates().put("server_door", "open");
            case "restore_airlock_power" -> (state, ignored) -> state.getObjectStates().put("airlock_power", "restored");
            case "set_evac_route" -> (state, ignored) -> {
                state.getObjectStates().put("evac_route", "known");
                state.getObjectStates().put("operator_state", "online");
            };
            case "enable_bypass" -> (state, ignored) -> state.getObjectStates().put("tech_mode", "bypass");
            case "stabilize_core" -> (state, ignored) -> {
                state.getObjectStates().put("core_state", "stable");
                state.getKnownFacts().add("ending:stabilization");
            };
            case "overload_core" -> (state, ignored) -> {
                state.getObjectStates().put("core_state", "overload");
                state.getKnownFacts().add("ending:sabotage");
            };
            case "finish_evacuation" -> (state, ignored) -> state.getKnownFacts().add("ending:evacuation");
            default -> null;
        };
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record LocationSeed(String id, String description) {
    }

    private record ItemSeed(String id, String description, String locationId) {
    }

    private record NpcSeed(String id, String description, String dialogue, String locationId) {
    }

    private record TransitionSeed(String fromId, String toId, String conditionKey) {
    }

    private record ActionSeed(
            String id,
            String locationId,
            String description,
            String conditionKey,
            String effectKey,
            Set<String> requiredItems,
            String targetId,
            Set<String> progressFlags
    ) {
    }

    private record WorldSeed(
            List<LocationSeed> locations,
            List<ItemSeed> items,
            List<NpcSeed> npcs,
            List<TransitionSeed> transitions,
            List<ActionSeed> actions
    ) {
    }
}
