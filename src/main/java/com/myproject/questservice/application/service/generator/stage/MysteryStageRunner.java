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
public class MysteryStageRunner implements StageRunner, PromptPreviewStageRunner {
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
            
            The output should feel like a real mission offer from a game like Space Rangers 2.
            
            ---
            
            IMPORTANT:
            
            The output MUST be written in Russian.
            
            All JSON values must contain Russian text.
            
            Return ONLY valid JSON.
            
            Each quest must belong to a different genre.
            
            Examples of genres:
            
            - investigation
            - exploration
            - delivery
            - rescue
            - diplomacy
            - crime
            - survival
            - mystery
            - combat
            - business
            
            Do not repeat the same type of situation.
            
            ---
            
            CORE PHILOSOPHY:
            
            Create a concrete assignment given to the player.
            
            The player should immediately understand:
            
            1. What happened?
            2. Why is this a problem?
            3. Why was the player contacted?
            4. What exactly must be done?
            5. What reward is offered?
            
            The feeling should be:
            
            "Есть проблема. Вот что нужно сделать. Вот почему обратились ко мне. Вот награда."
            
            Not:
            
            "Эпическая история о судьбе галактики."
            
            ---
            
            DO NOT create:
            
            - achievements
            - achievement dependencies
            - locations descriptions
            - NPC biographies
            - dialogues
            - scenes
            - gameplay mechanics
            - puzzles
            - solutions
            - walkthrough
            - quest steps
            - detailed world history
            - complex lore
            - hidden truth
            - twists
            - moral themes
            
            Only create the initial quest offer.
            
            ---
            
            # STRUCTURE
            
            ## 1. Title
            
            Create a short memorable quest title.
            
            The title should describe the main situation.
            
            Good:
            
            "Тюрьма без выхода"
            
            "Последний рейс"
            
            "Чужой груз"
            
            "Пропавший маяк"
            
            "Долг капитана"
            
            Bad:
            
            "Цена выбора"
            
            "Тени прошлого"
            
            "Судьба галактики"
            
            The title should feel like a quest name, not a novel title.
            
            ---
            
            ## 2. Situation
            
            Describe what happened.
            
            The situation answers:
            
            "What is the current problem?"
            
            Rules:
            
            - 1-3 sentences.
            - Concrete event.
            - No explanations of the whole world.
            - No history.
            - No unnecessary background.
            
            The situation must create a reason for action.
            
            Good:
            
            "Грузовой корабль исчез во время обычного рейса. Последний сигнал указывает, что судно продолжает работать, но экипаж не отвечает."
            
            "После аварии орбитальная тюрьма закрыла все внешние шлюзы. Внутри остались люди, а система управления не принимает команды."
            
            Bad:
            
            "Галактика стоит перед угрозой."
            
            "Древняя сила пробудилась."
            
            "Мир изменился."
            
            ---
            
            ## 3. Quest Offer
            
            Describe the actual request to the player.
            
            This is the most important field.
            
            It answers:
            
            "What do they want from me?"
            
            The player should receive a clear assignment.
            
            Rules:
            
            - The player is hired, asked, or ordered.
            - The request must be specific.
            - Keep it simple.
            - Do not invent complicated organizations.
            - Do not explain unnecessary politics.
            
            Good:
            
            "Вас просят найти пропавший корабль и установить, что с ним произошло."
            
            "Вам предлагают доставить редкий груз до указанного срока."
            
            "Вас просят освободить человека, который оказался заперт внутри аварийного комплекса."
            
            Bad:
            
            "Вас выбрали, потому что только вы способны спасти галактику."
            
            ---
            
            ## 4. Why Me
            
            Explain why the player receives this offer.
            
            This answers:
            
            "Why don't they solve it themselves?"
            
            Rules:
            
            Good reasons:
            
            - need a neutral outsider;
            - official methods failed;
            - secrecy is important;
            - unusual skills are required;
            - time is limited;
            - client cannot act directly.
            
            Good:
            
            "Официальное расследование привлекло бы слишком много внимания, поэтому нужен независимый исполнитель."
            
            "Обычные службы не смогли попасть внутрь комплекса."
            
            Bad:
            
            "Только вы избранный герой."
            
            "Судьба выбрала вас."
            
            ---
            
            ## 5. Goal
            
            Create exactly ONE concrete objective.
            
            The goal is the final thing the player is asked to accomplish.
            
            It must be:
            
            - specific;
            - achievable;
            - understandable.
            
            Good:
            
            "Найти пропавший корабль."
            
            "Доставить груз владельцу."
            
            "Освободить заключённого."
            
            "Получить разрешение на аренду территории."
            
            Bad:
            
            "Изменить будущее."
            
            "Раскрыть тайну вселенной."
            
            "Спасти человечество."
            
            ---
            
            ## 6. Reward
            
            Create a simple motivation.
            
            Examples:
            
            - money;
            - reputation;
            - access;
            - information;
            - future opportunity.
            
            Keep it practical.
            
            Good:
            
            "10000 cr"
            
            "Оплата и доступ к закрытому рынку."
            
            "Деньги и благодарность владельцев."
            
            Bad:
            
            "Возможность изменить судьбу галактики."
            
            ---
            
            ## 7. Possible Outcomes
            
            Create possible post-quest states.
            
            IMPORTANT:
            
            These are NOT cinematic endings.
            
            They describe what exists after the mission is finished.
            
            The player should be able to return to the client and report:
            
            "The mission result is this."
            
            Rules:
            
            - 2-4 outcomes.
            - Short.
            - Concrete.
            - Connected to the goal.
            
            Good:
            
            Goal:
            "Найти пропавший корабль."
            
            Outcomes:
            
            "Корабль найден и возвращён."
            
            "Корабль найден, но восстановление невозможно."
            
            "Корабль не найден."
            
            Bad:
            
            "Игрок узнал правду."
            
            "Мир изменился."
            
            "Началась новая эпоха."
            
            ---
            
            OUTPUT SCHEMA:
            
            {
              "quests": [
                {
                  "title": "",
                  "situation": "",
                  "quest_offer": "",
                  "why_me": "",
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
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.QUEST_DESCRIPTION;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        String userPrompt = buildUserPrompt(project);
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    @Override
    public StagePromptPreview previewPrompt(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        return new StagePromptPreview(SYSTEM_PROMPT, buildUserPrompt(project));
    }

    private String buildUserPrompt(QuestProject project) {
        System.out.println(project);
        return """
                Output must strictly follow the JSON schema from system prompt.
                """;
    }
}
