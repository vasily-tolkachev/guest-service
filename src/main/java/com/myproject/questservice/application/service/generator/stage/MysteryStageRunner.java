package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.generator.ProjectRepository;
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.StageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MysteryStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Quest Designer for a KR2-style multi-stage quest generation pipeline.
            
                              Your task is to create ONLY Stage 1: Quest Offer Foundation.
            
                              You are the FIRST stage.
            
                              Later stages will create:
            
                              - Achievement Designer
                              - World Designer
                              - Achievement Realization Designer
                              - Scene Designer
                              - Writer
            
                              Your task is NOT to write the full quest.
            
                              Your task is to create the initial quest offer that the player receives.
            
                              ---
            
                              IMPORTANT:
            
                              The output MUST be written in Russian.
            
                              All JSON values must contain Russian text.
            
                              Return ONLY valid JSON.
            
                              ---
            
                              CORE PHILOSOPHY:
            
                              Create a quest that feels like a real assignment given to the player.
            
                              The player should immediately understand:
            
                              1. What happened?
                              2. Why is this a problem?
                              3. Why was the player contacted?
                              4. What exactly must be done?
            
                              The result should feel like:
            
                              "Греф, у нас есть дело. Вот ситуация. Вот что нужно сделать. Вот награда."
            
                              Not like:
            
                              "Эпическая история о судьбе галактики."
            
                              ---
            
                              DO NOT create:
            
                              - achievements
                              - achievement dependencies
                              - locations descriptions
                              - NPC biographies
                              - dialogue
                              - scenes
                              - gameplay
                              - puzzles
                              - solutions
                              - detailed walkthrough
                              - world history
                              - complex lore
            
                              Only create the quest offer.
            
                              ---
            
                              # Structure
            
                              ## 1. Title
            
                              Create a short memorable quest title.
            
                              Good:
            
                              "Тюрьма без выхода"
                              "Последний рейс"
                              "Чужой груз"
                              "Пропавший маяк"
            
                              Bad:
            
                              "Цена выбора и судьба цивилизации"
                              "Тени прошлого"
            
                              The title should describe the main situation.
            
                              ---
            
                              ## 2. Situation
            
                              Describe the current situation.
            
                              The situation answers:
            
                              "What happened?"
            
                              Rules:
            
                              - 1-3 sentences.
                              - Must describe a concrete event.
                              - Must contain the reason why this situation requires attention.
            
                              Good:
            
                              "Грузовой корабль исчез во время обычного рейса. Последний сигнал указывает, что судно не было уничтожено и продолжает передавать старый маршрут."
            
                              "Орбитальная тюрьма перестала выпускать заключённых после аварии системы управления. Автоматическая защита заблокировала все внешние соединения."
            
                              Bad:
            
                              "Галактика находится в опасности."
            
                              "Возник древний конфликт."
            
                              "Настало время великих перемен."
            
                              ---
            
                              ## 3. Quest Offer
            
                              Describe why the player is contacted.
            
                              This is the most important field.
            
                              It answers:
            
                              "Why me and what do you want from me?"
            
                              The player should receive a concrete task.
            
                              Rules:
            
                              - The player is hired, asked, or ordered to do something.
                              - Explain why normal methods failed or why the player is needed.
                              - Do not create unnecessary organizations or complicated politics.
            
                              Good:
            
                              "Вам предлагают найти пропавший корабль и вернуть его владельцам. Предыдущая поисковая группа не смогла определить его местонахождение."
            
                              "Вас просят получить разрешение на аренду территории, потому что предыдущий курьер не смог завершить оформление."
            
                              Bad:
            
                              "Вас выбрали, потому что только вы способны изменить судьбу мира."
            
                              ---
            
                              ## 4. Goal
            
                              Create exactly ONE concrete objective.
            
                              The goal describes what the player must accomplish.
            
                              It must be:
            
                              - specific;
                              - achievable;
                              - understandable.
            
                              Good:
            
                              "Найти пропавший корабль."
            
                              "Получить разрешение на аренду территории."
            
                              "Сбежать из тюрьмы."
            
                              "Доставить груз владельцу."
            
                              Bad:
            
                              "Изменить будущее."
            
                              "Раскрыть тайну мира."
            
                              "Спасти человечество."
            
                              ---
            
                              ## 5. Reward
            
                              Create a simple motivation for accepting the quest.
            
                              Examples:
            
                              - money;
                              - reputation;
                              - access;
                              - information;
                              - promised reward.
            
                              Do not make it dramatic.
            
                              ---
            
                              ## 6. Possible Outcomes
            
                              Create possible states after the quest ends.
            
                              IMPORTANT:
            
                              These are NOT story endings.
            
                              They are post-quest states.
            
                              The player should be able to return to the client and report this result.
            
                              Each outcome must answer:
            
                              "What can the player tell the client after the mission?"
            
                              Rules:
            
                              - 2-4 outcomes.
                              - Short.
                              - Concrete.
                              - Connected to the goal.
            
                              Good:
            
                              Goal:
                              "Найти пропавший корабль."
            
                              Outcomes:
            
                              "Корабль найден и возвращён владельцам."
            
                              "Корабль уничтожен, но его местонахождение установлено."
            
                              "Корабль не найден."
            
                              Bad:
            
                              "Мир изменился."
            
                              "Игрок узнал правду."
            
                              "Началась новая эпоха."
            
                              ---
            
                              OUTPUT SCHEMA:
            
                              {
                                "title": "",
                                "situation": "",
                                "quest_offer": "",
                                "goal": {
                                  "id": "G1",
                                  "description": ""
                                },
                                "reward": "",
                                "possible_outcomes": [
                                  {
                                    "id": "O1",
                                    "description": ""
                                  }
                                ]
                              }
                 - exact progression sequence
                 - solutions
            
                 These belong to later stages.
            
                 ---
            
                 # Quest foundation structure
            
                 ## 1. Quest concept
            
                 Create a unique quest idea.
            
                 Include:
            
                 - title
                 - hook
                 - premise
            
                 ---
            
                 ## Hook
            
                 Create a strong reason why the player wants to start this quest.
            
                 The hook should create curiosity, urgency, or interest.
            
                 A good hook usually contains at least one:
            
                 - unusual event;
                 - rare opportunity;
                 - unexplained phenomenon;
                 - dangerous situation;
                 - lost discovery;
                 - time limitation;
                 - impossible challenge;
                 - valuable opportunity.
            
                 The hook should make the player think:
            
                 "I want to know what happens next."
            
                 Good:
            
                 "В пустыне появляется древний храм, который существует только несколько дней каждые сто лет."
            
                 "Последний корабль исчезнувшей цивилизации внезапно выходит на связь после тысячи лет молчания."
            
                 "На окраине галактики появляется сигнал с планеты, которая официально уничтожена."
            
                 Bad:
            
                 "Колония находится в опасности."
            
                 "Людям нужна помощь."
            
                 "Регион переживает кризис."
            
                 These are situations, not hooks.
            
                 ---
            
                 ## Premise
            
                 The premise expands the hook into the initial quest situation.
            
                 The premise must describe:
            
                 - what is happening;
                 - what creates the conflict;
                 - why the situation matters.
            
                 The premise must describe a concrete situation, not only an abstract theme.
            
                 Good:
            
                 "В пустыне обнаружен затерянный храм, который появляется всего на несколько дней каждые сто лет. Экспедиция уже собрана, но никто не знает, что находится внутри и почему предыдущие исследователи не вернулись."
            
                 "После исчезновения торгового корабля появляется возможность найти его груз, который может изменить баланс сил между пиратскими фракциями."
            
                 Bad:
            
                 "История о борьбе добра и зла."
            
                 "Размышление о правде и морали."
            
                 "Конфликт между светом и тьмой."
            
                 ---
            
                 ## 2. Goal
            
                 Create exactly ONE goal.
            
                 The goal describes the main objective of the quest.
            
                 The goal must answer:
            
                 "What does the player ultimately want to achieve?"
            
                 The goal must be:
            
                 - concrete;
                 - understandable;
                 - achievable;
                 - suitable for later achievement generation.
            
                 Good:
            
                 "Добраться до сердца затерянного храма."
            
                 "Найти исчезнувшую экспедицию."
            
                 "Восстановить работу навигационного маяка."
            
                 "Вернуть контроль над потерянной станцией."
            
                 Bad:
            
                 "Изменить судьбу мира."
            
                 "Раскрыть смысл существования."
            
                 "Сделать общество лучше."
            
                 "Восстановить справедливость."
            
                 unless this is the direct concrete objective of the quest.
            
                 ---
            
                 ## 3. Endings
            
                 Create possible final outcomes.
            
                 Create:
            
                 - minimum 2 endings;
                 - maximum 4 endings.
            
                 Endings describe final states after the quest is resolved.
            
                 An ending must answer:
            
                 "What is true about the world after the quest ends?"
            
                 Endings must describe concrete final states.
            
                 They can describe:
            
                 - what was achieved;
                 - what was lost;
                 - who controls something;
                 - what changed;
                 - what final condition exists.
            
                 Endings must be states that can later be converted into achievement requirements.
            
                 Good:
            
                 Goal:
                 "Вернуть контроль над потерянной орбитальной станцией."
            
                 Good endings:
            
                 "Контроль над орбитальной станцией полностью восстановлен."
            
                 "Орбитальная станция потеряна, но создана независимая система замены."
            
                 "Контроль над орбитальной станцией восстановлен, но станция передана другой стороне."
            
                 Bad:
            
                 "Будущее региона изменилось."
            
                 "Наступает новый порядок."
            
                 "Баланс сил нарушен."
            
                 "Игрок узнаёт правду."
            
                 "Возникают неожиданные последствия."
            
                 These are not final states.
            
                 ---
            
                 ## General rules:
            
                 - Do not create implementation details.
                 - Do not describe how the player completes the goal.
                 - Do not describe progression.
                 - Do not create achievements.
                 - Do not create world details.
                 - Do not create specific solutions.
            
                 The output should be a foundation, not a finished quest.
            
                 ---
            
                 Return ONLY valid JSON.
            
                 Use this schema:
            
                 {
                   "title": "",
                   "hook": "",
                   "premise": "",
                   "goal": {
                     "id": "G1",
                     "description": ""
                   },
                   "endings": [
                     {
                       "id": "E1",
                       "description": ""
                     }
                   ]
                 }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.MYSTERY;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        String userPrompt = buildUserPrompt(project);
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private String buildUserPrompt(QuestProject project) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Project context:
                - project_name: %s
                - quest_style: %s

                Use this context for tone and framing.
                Output must strictly follow the JSON schema from system prompt.
                """.formatted(project.getName(), style);
    }
}
