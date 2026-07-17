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
