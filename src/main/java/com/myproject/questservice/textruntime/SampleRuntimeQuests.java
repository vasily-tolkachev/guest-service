package com.myproject.questservice.textruntime;

import java.util.LinkedHashMap;
import java.util.List;
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
                "БЕРЕГ"
        ));
        return map;
    }

    private static World buildShipwreckWorld() {
        Map<String, Location> locations = new LinkedHashMap<>();
        locations.put("БЕРЕГ", new Location(
                "БЕРЕГ",
                "Вы на берегу после шторма. Рядом обломки лодки и мокрый рюкзак.",
                List.of(new Item("КОМПАС", "Компас")),
                List.of(new Location.Exit("Уйти в лес", "ЛЕС"))
        ));
        locations.put("ЛЕС", new Location(
                "ЛЕС",
                "Темный лес. Слышно море и треск веток.",
                List.of(),
                List.of(
                        new Location.Exit("Вернуться на берег", "БЕРЕГ"),
                        new Location.Exit("Пойти к пещере", "ПЕЩЕРА"),
                        new Location.Exit("Пойти к руинам", "РУИНЫ")
                )
        ));
        locations.put("ПЕЩЕРА", new Location(
                "ПЕЩЕРА",
                "В пещере холодно. В нише виден ржавый ключ.",
                List.of(new Item("КЛЮЧ", "Ржавый ключ")),
                List.of(new Location.Exit("Вернуться в лес", "ЛЕС"))
        ));
        locations.put("РУИНЫ", new Location(
                "РУИНЫ",
                "Перед вами древние руины с закрытыми воротами.",
                List.of(),
                List.of(new Location.Exit("Вернуться в лес", "ЛЕС"))
        ));
        return new World(locations);
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

