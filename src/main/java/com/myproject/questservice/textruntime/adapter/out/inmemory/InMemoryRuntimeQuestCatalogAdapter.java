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
                        new LocationSeed("берег", "Вы стоите на берегу после кораблекрушения."),
                        new LocationSeed("лес", "Вы находитесь в тёмном лесу."),
                        new LocationSeed("пещера", "Внутри пещеры холодно и темно."),
                        new LocationSeed("руины", "Вы подошли к древним руинам с закрытыми воротами.")
                ),
                List.of(
                        new ItemSeed("компас", "Небольшой металлический компас.", "берег"),
                        new ItemSeed("ключ", "Старый ржавый ключ.", "пещера")
                ),
                List.of(
                        new NpcSeed("выживший", "Изможденный человек у костра.", "Я видел течение на восток. Если соберешь припасы, плыви с утренним ветром.", "лес")
                ),
                List.of(
                        new TransitionSeed("берег", "лес", null),
                        new TransitionSeed("лес", "берег", null),
                        new TransitionSeed("лес", "пещера", null),
                        new TransitionSeed("пещера", "лес", null),
                        new TransitionSeed("лес", "руины", "ruins_gate_open"),
                        new TransitionSeed("руины", "лес", null)
                ),
                List.of(
                        new ActionSeed(
                                "open_ruins_gate",
                                "лес",
                                "Открыть ворота руин ключом",
                                "has_key",
                                "open_ruins_gate",
                                Set.of("ключ"),
                                "ворота",
                                Set.of("gate_opened")
                        ),
                        new ActionSeed(
                                "inspect_marks",
                                "лес",
                                "Осмотреть метки на дереве",
                                null,
                                "add_fact_marks_to_ruins",
                                Set.of(),
                                "метки",
                                Set.of("marks_checked")
                        )
                )
        );

        WorldSeed stationSeed = new WorldSeed(
                List.of(
                        new LocationSeed("вестибюль", "Вы в пустом вестибюле исследовательской станции."),
                        new LocationSeed("коридор", "Узкий коридор, мигает аварийное освещение."),
                        new LocationSeed("серверная", "Серверная заперта, рядом терминал доступа."),
                        new LocationSeed("архив", "Архив с бумагами и старой картой здания.")
                ),
                List.of(
                        new ItemSeed("карта", "Схема станции с пометками маршрутов.", "архив"),
                        new ItemSeed("карта_доступа", "Служебная карта доступа.", "коридор")
                ),
                List.of(
                        new NpcSeed("оператор", "Дежурный оператор по внутренней связи.", "Отключи блокировку через терминал и путь в серверную откроется.", "вестибюль")
                ),
                List.of(
                        new TransitionSeed("вестибюль", "коридор", null),
                        new TransitionSeed("коридор", "вестибюль", null),
                        new TransitionSeed("коридор", "архив", null),
                        new TransitionSeed("архив", "коридор", null),
                        new TransitionSeed("коридор", "серверная", "server_door_open"),
                        new TransitionSeed("серверная", "коридор", null)
                ),
                List.of(
                        new ActionSeed(
                                "unlock_server_room",
                                "коридор",
                                "Открыть серверную картой доступа",
                                "has_access_card",
                                "open_server_room",
                                Set.of("карта_доступа"),
                                "терминал",
                                Set.of("server_opened")
                        ),
                        new ActionSeed(
                                "inspect_map",
                                "архив",
                                "Изучить карту станции",
                                null,
                                "add_fact_secret_route",
                                Set.of(),
                                "карта",
                                Set.of("map_checked")
                        )
                )
        );

        map.put("island_escape", new RuntimeQuestDefinition(
                "island_escape",
                "Остров: путь к руинам",
                "Демо-квест выживания на острове.",
                buildWorldFromDefinition(islandSeed),
                "берег"
        ));
        map.put("shipwreck", new RuntimeQuestDefinition(
                "shipwreck",
                "Демо-квест 1",
                "Совместимый id для уже существующих ссылок.",
                buildWorldFromDefinition(islandSeed),
                "берег"
        ));
        map.put("station_breach", new RuntimeQuestDefinition(
                "station_breach",
                "Станция: вскрытие серверной",
                "Демо-квест на закрытой станции.",
                buildWorldFromDefinition(stationSeed),
                "вестибюль"
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
            case "ruins_gate_open" -> (state, ignored) -> "открыты".equals(state.getObjectStates().get("ворота_руин"));
            case "server_door_open" -> (state, ignored) -> "открыта".equals(state.getObjectStates().get("дверь_серверной"));
            case "has_key" -> (state, ignored) -> state.getPlayer().getInventory().contains("ключ");
            case "has_access_card" -> (state, ignored) -> state.getPlayer().getInventory().contains("карта_доступа");
            default -> null;
        };
    }

    private static World.Effect resolveEffect(String effectKey) {
        if (effectKey == null || effectKey.isBlank()) {
            return null;
        }
        return switch (effectKey) {
            case "open_ruins_gate" -> (state, ignored) -> {
                state.getObjectStates().put("ворота_руин", "открыты");
                state.getWorldChanges().add("объект:ворота_руин=открыты");
            };
            case "open_server_room" -> (state, ignored) -> {
                state.getObjectStates().put("дверь_серверной", "открыта");
                state.getWorldChanges().add("объект:дверь_серверной=открыта");
            };
            case "add_fact_marks_to_ruins" -> (state, ignored) -> state.getKnownFacts().add("метки_ведут_к_руинам");
            case "add_fact_secret_route" -> (state, ignored) -> state.getKnownFacts().add("на_карте_есть_обходной_маршрут");
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
