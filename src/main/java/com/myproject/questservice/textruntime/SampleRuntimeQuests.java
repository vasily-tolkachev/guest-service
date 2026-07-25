package com.myproject.questservice.textruntime;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SampleRuntimeQuests {
    private SampleRuntimeQuests() {
    }

    public static Map<String, RuntimeQuestDefinition> definitions() {
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
        world.addTransition("лес", "руины", (state, w) -> state.getPlayer().getInventory().contains("ключ"));
        world.addTransition("руины", "лес", null);

        return world;
    }

    public record RuntimeQuestDefinition(
            String id,
            String name,
            String description,
            World world,
            String startLocationId
    ) {
    }
}
