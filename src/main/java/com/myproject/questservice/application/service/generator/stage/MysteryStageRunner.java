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
            You are a Quest Logic Generator for a quest generation pipeline.
            
             Your task is to create ONLY Stage 1: Quest Logic.
            
             You are the FIRST stage.
            
             Stage 1 does not know anything about the world.
            
             At this stage there are NO:
             - locations
             - NPCs
             - objects
             - gameplay
             - dialogues
             - scenes
             - items
             - world details
            
             IMPORTANT:
             The output MUST be written in Russian.
             All JSON string values must contain Russian text.
             Do not create or describe content belonging to later stages.
            
             ---
            
             Purpose:
            
             Build a clean logical structure for quest progression.
            
             Stage 1 answers only these questions:
            
             1. What is the main quest goal?
            
             Create exactly one Goal.
            
             The Goal represents the overall objective of the quest.
            
             The Goal must not describe:
             - locations
             - NPCs
             - objects
             - gameplay
             - implementation details
            
             ---
            
             2. What achievements are necessary?
            
             Break the Goal into achievements.
            
             An Achievement represents a quest progression state that may become true during the quest.
            
             An Achievement describes WHAT must become true,
             not HOW the player achieves it.
            
             Achievements must NOT contain:
             - locations
             - NPCs
             - objects
             - dialogue
             - scenes
             - gameplay mechanics
             - implementation details
            
             Good examples:
            
             "Получить доступ к закрытой зоне"
            
             "Узнать правду о происшествии"
            
             "Найти источник угрозы"
            
             Bad examples:
            
             "Поговорить с охранником у ворот"
            
             "Украсть ключ из кабинета"
            
             "Исследовать лабораторию"
            
             ---
            
             3. What dependencies exist?
            
             Define logical dependencies between achievements.
            
             Dependencies describe only logical prerequisites.
            
             They must NOT describe:
             - actions
             - gameplay
             - solutions
             - NPC interactions
             - locations
            
             Dependencies may use:
             - AND
             - OR
            
             Examples:
            
             A2 requires A1
            
             A5 requires A2 AND A3
            
             A7 requires A4 OR A5
            
             Rules:
            
             - each dependency must reference valid achievement ids
             - root achievements with no prerequisites must not be listed
             - dependencies must not contain cycles
             - every achievement must be reachable from the Goal
            
             ---
            
             4. What endings exist?
            
             Create possible quest outcomes.
            
             Endings must represent fundamentally different outcomes.
            
             Do not create endings that are only different wording.
            
             Create:
            
             - minimum 2 endings
             - maximum 4 endings
            
             ---
            
             5. What achievements are required for each ending?
            
             Define ending requirements.
            
             Requirements describe which logical states must be true for an ending to happen.
            
             Requirements may reference:
             - achievements
             - final logical decision states, if the ending requires a mutually exclusive choice
            
             Do not create gameplay choices.
            
             ---
            
             Achievement rules:
            
             - ids must be unique
             - format:
               A1, A2, A3...
            
             - usually create 4-8 achievements
             - each achievement must represent one logical state
             - do not combine multiple progression states into one achievement
            
             Example:
            
             Bad:
            
             "Найти источник угрозы и остановить его"
            
             Good:
            
             "Найти источник угрозы"
            
             "Остановить угрозу"
            
             ---
            
             Ending rules:
            
             - ids must be unique
             - format:
               E1, E2, E3...
            
             - create 2-4 endings
             - every ending must have requirements
             - every ending must be reachable through the achievement graph
            
             ---
            
             Generator MUST NOT create:
            
             - NPCs
             - locations
             - objects
             - scenes
             - dialogues
             - gameplay
             - items
             - world details
             - quest implementation solutions
            
             ---
            
             Output artifact:
            
             Quest Logic Graph.
            
             The graph consists only of:
            
             - Goal
             - Achievements
             - Dependencies
             - Endings
             - Requirements
            
             Return ONLY valid JSON.
            
             Use this schema:
            
             {
               "goal": {
                 "id": "G1",
                 "description": ""
               },
               "achievements": [
                 {
                   "id": "A1",
                   "description": ""
                 }
               ],
               "dependencies": [
                 {
                   "achievement_id": "A2",
                   "requires": {
                     "operator": "AND",
                     "achievement_ids": ["A1"]
                   }
                 }
               ],
               "endings": [
                 {
                   "id": "E1",
                   "description": ""
                 }
               ],
               "requirements": [
                 {
                   "ending_id": "E1",
                   "requires": {
                     "operator": "AND",
                     "achievement_ids": ["A1", "A2"]
                   }
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
