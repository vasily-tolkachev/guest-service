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
            
              Your task is to create ONLY the quest foundation for a multi-stage quest generation pipeline.
            
              You are the FIRST stage.
            
              Later stages will create:
            
              - Achievement Designer
              - World Designer
              - Achievement Realization Designer
              - Scene Designer
              - Writer
            
              Your task is NOT to write a quest.
            
              Your task is to define the core foundation of the quest that later stages will expand.
            
              IMPORTANT:
            
              The output MUST be written in Russian.
            
              All JSON values must contain Russian text.
            
              No stage is allowed to rewrite or retell data from previous stages.
            
              ---
            
              Core philosophy:
            
              Create a strong quest foundation where:
            
              - the player has a clear objective;
              - the situation creates meaningful conflict;
              - the quest can be expanded into multiple achievements;
              - different outcomes are possible;
              - later stages can build a world around this foundation.
            
              The player should feel:
            
              "This is a meaningful situation where my decisions can change the outcome."
            
              ---
            
              Do NOT create:
            
              - achievements
              - achievement dependencies
              - locations
              - NPCs
              - character biographies
              - objects
              - items
              - scenes
              - dialogues
              - gameplay
              - puzzles
              - combat situations
              - quest steps
              - exact progression sequence
            
              These belong to later stages.
            
              ---
            
              Quest foundation structure:
            
              ## 1. Quest concept
            
              Create the basic idea of the quest.
            
              Include:
            
              - title
              - short premise
            
              The premise should describe:
            
              - what situation exists;
              - what creates the conflict;
              - why the situation matters.
            
              Do NOT describe:
            
              - how the player solves it;
              - exact events;
              - locations;
              - characters.
            
              ---
            
              ## 2. Main goal
            
              Create exactly one main goal.
            
              The goal describes the final objective of the quest.
            
              The goal should answer:
            
              "What does the player ultimately want to achieve?"
            
              Good:
            
              "Найти легендарный клад."
            
              "Остановить угрозу, уничтожающую колонию."
            
              "Раскрыть причину исчезновения экспедиции."
            
              Bad:
            
              "Поговорить с капитаном."
            
              "Найти ключ."
            
              "Исследовать лабораторию."
            
              ---
            
              ## 3. Core conflict
            
              Describe the main conflict of the quest.
            
              The conflict explains:
            
              - what forces oppose each other;
              - why the situation is difficult;
              - what is at stake.
            
              Do NOT create:
            
              - factions with names;
              - NPCs;
              - locations;
              - detailed history.
            
              ---
            
              ## 4. Player motivation
            
              Explain why the player becomes involved.
            
              The motivation should create emotional or practical importance.
            
              Examples:
            
              - something valuable is at risk;
              - people may suffer;
              - a major opportunity exists;
              - a dangerous situation must be resolved.
            
              Do NOT create:
            
              - player biography;
              - personal history;
              - specific relationships.
            
              ---
            
              ## 5. Possible endings
            
              Create possible final outcomes.
            
              Create:
            
              - 2-4 endings.
            
              Each ending describes a different final state of the quest.
            
              Good:
            
              "Угроза полностью устранена."
            
              "Цель достигнута, но с серьёзными последствиями."
            
              "Ситуация остаётся нерешённой."
            
              Bad:
            
              "Игрок получает ключ."
            
              "Игрок находит документ."
            
              "Игрок разговаривает с NPC."
            
              ---
            
              ## 6. Quest tone and direction
            
              Describe the intended feeling of the quest.
            
              Examples:
            
              - mystery
              - adventure
              - survival
              - tragedy
              - political conflict
              - exploration
              - comedy
            
              Do not create scenes or gameplay.
            
              ---
            
              Return ONLY valid JSON.
            
              Use this schema:
            
              {
                "title": "",
                "premise": "",
                "goal": {
                  "id": "G1",
                  "description": ""
                },
                "core_conflict": "",
                "player_motivation": "",
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
