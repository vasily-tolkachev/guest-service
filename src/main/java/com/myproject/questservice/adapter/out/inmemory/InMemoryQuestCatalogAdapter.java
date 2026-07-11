package com.myproject.questservice.adapter.out.inmemory;

import com.myproject.questservice.application.port.out.QuestCatalogPort;
import com.myproject.questservice.domain.Node;
import com.myproject.questservice.domain.Option;
import com.myproject.questservice.domain.Quest;
import com.myproject.questservice.domain.Transition;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class InMemoryQuestCatalogAdapter implements QuestCatalogPort {

    private final Map<String, Quest> quests = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        Quest quest = buildLighthouseQuest();
        quests.put(quest.id(), quest);
        log.info("Loaded demo quest: {}", quest.id());
    }

    @Override
    public List<Quest> findAll() {
        return quests.values().stream().toList();
    }

    @Override
    public Optional<Quest> findById(String questId) {
        return Optional.ofNullable(quests.get(questId));
    }

    private Quest buildLighthouseQuest() {
        String id = "lighthouse";

        Node start = new Node(
                "start",
                "Ты стоишь у маяка. Туман поднимается от воды.",
                List.of(
                        new Option("enter", "Войти в маяк", new Transition("keeper")),
                        new Option("shore", "Осмотреть берег", new Transition("beach")),
                        new Option("leave", "Уйти в порт", new Transition("end_leave"))
                )
        );

        Node keeper = new Node(
                "keeper",
                "Старый смотритель молча протягивает тебе карту рифов.",
                List.of(
                        new Option("accept_map", "Взять карту", new Transition("tower_top")),
                        new Option("decline_map", "Отказаться и выйти", new Transition("beach"))
                )
        );

        Node beach = new Node(
                "beach",
                "На мокром песке ты находишь следы ботинок и обрывок каната.",
                List.of(
                        new Option("follow_tracks", "Пойти по следам", new Transition("cave")),
                        new Option("return_lighthouse", "Вернуться к маяку", new Transition("start"))
                )
        );

        Node cave = new Node(
                "cave",
                "В пещере слышен шум волн. В глубине блестит металлический ящик.",
                List.of(
                        new Option("open_box", "Открыть ящик", new Transition("end_treasure")),
                        new Option("retreat", "Отступить", new Transition("end_retreat"))
                )
        );

        Node towerTop = new Node(
                "tower_top",
                "С вершины маяка ты замечаешь безопасный проход между рифами.",
                List.of(
                        new Option("signal_ship", "Подать сигнал кораблю", new Transition("end_hero")),
                        new Option("stay_silent", "Промолчать", new Transition("end_silent"))
                )
        );

        Node endLeave = new Node("end_leave", "Ты уходишь в порт, так и не узнав тайну маяка.", List.of());
        Node endTreasure = new Node("end_treasure", "Внутри ящика старинные монеты и журнал капитана. Конец.", List.of());
        Node endRetreat = new Node("end_retreat", "Ты решаешь не рисковать и покидаешь пещеру. Конец.", List.of());
        Node endHero = new Node("end_hero", "Корабль проходит рифы и экипаж благодарит тебя. Конец.", List.of());
        Node endSilent = new Node("end_silent", "Ты наблюдаешь, как корабль исчезает в тумане. Конец.", List.of());

        Map<String, Node> nodes = Map.of(
                start.id(), start,
                keeper.id(), keeper,
                beach.id(), beach,
                cave.id(), cave,
                towerTop.id(), towerTop,
                endLeave.id(), endLeave,
                endTreasure.id(), endTreasure,
                endRetreat.id(), endRetreat,
                endHero.id(), endHero,
                endSilent.id(), endSilent
        );

        return new Quest(id, "Тайна маяка", "start", nodes);
    }
}
