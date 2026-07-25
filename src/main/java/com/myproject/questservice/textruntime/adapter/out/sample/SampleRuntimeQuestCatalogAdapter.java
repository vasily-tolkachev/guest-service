package com.myproject.questservice.textruntime.adapter.out.sample;

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
public class SampleRuntimeQuestCatalogAdapter implements RuntimeQuestCatalogPort {
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
        map.put("shipwreck", new RuntimeQuestDefinition(
                "shipwreck",
                "Кораблекрушение",
                "Выживание на острове: пляж, лес, пещера и древние руины.",
                buildShipwreckWorld(),
                "берег"
        ));
        return map;
    }

    private static World buildShipwreckWorld() {
        World world = new World();

        Location beach = new Location("берег", "Вы стоите на берегу после кораблекрушения.");
        Location forest = new Location("лес", "Вы находитесь в тёмном лесу.");
        Location cave = new Location("пещера", "Внутри пещеры холодно и темно.");
        Location ruins = new Location("руины", "Вы подошли к древним руинам с закрытыми воротами.");

        world.addLocation(beach);
        world.addLocation(forest);
        world.addLocation(cave);
        world.addLocation(ruins);

        Item compass = new Item("компас", "Небольшой металлический компас.");
        Item key = new Item("ключ", "Старый ржавый ключ.");
        Npc survivor = new Npc(
                "выживший",
                "Изможденный человек у костра.",
                "Я видел течение на восток. Если соберешь припасы, плыви с утренним ветром."
        );
        world.addItem(compass);
        world.addItem(key);
        world.addNpc(survivor);
        world.placeItem("берег", compass.getId());
        world.placeItem("пещера", key.getId());
        world.placeNpc("лес", survivor.getId());

        world.addTransition("берег", "лес", null);
        world.addTransition("лес", "берег", null);
        world.addTransition("лес", "пещера", null);
        world.addTransition("пещера", "лес", null);
        world.addTransition("лес", "руины", (state, w) -> "открыты".equals(state.getObjectStates().get("ворота_руин")));
        world.addTransition("руины", "лес", null);

        // Тест взаимодействия с world object: interact("ворота") -> executeAction.
        world.addAction(new World.WorldAction(
                "open_ruins_gate",
                "лес",
                "Открыть ворота руин ключом",
                (state, w) -> state.getPlayer().getInventory().contains("ключ"),
                (state, w) -> {
                    state.getObjectStates().put("ворота_руин", "открыты");
                    state.getWorldChanges().add("объект:ворота_руин=открыты");
                },
                Set.of("ключ"),
                "ворота",
                Set.of("gate_opened")
        ));

        // Ещё один объект для interact/inspect-target.
        world.addAction(new World.WorldAction(
                "inspect_marks",
                "лес",
                "Осмотреть метки на дереве",
                null,
                (state, w) -> state.getKnownFacts().add("метки_ведут_к_руинам"),
                Set.of(),
                "метки",
                Set.of("marks_checked")
        ));

        return world;
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
