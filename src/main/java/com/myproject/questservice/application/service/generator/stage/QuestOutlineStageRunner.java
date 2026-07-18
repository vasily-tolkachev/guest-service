package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.application.service.ConflictException;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.generator.ProjectRepository;
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.QuestStage;
import com.myproject.questservice.domain.generator.StageStatus;
import com.myproject.questservice.domain.generator.StageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class QuestOutlineStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Quest Outline Generator for a KR2-style investigation quest.
            
             Your job is NOT to split the investigation by investigative methods.
            
             Your job is to split the quest into major story chapters.
            
             Each chapter must feel like a new stage of the adventure.
            
             A chapter should introduce:
             - a new place to explore,
             - a new conflict,
             - a new objective,
             - new characters,
             - new discoveries.
            
             Think about chapters as episodes of an adventure rather than police procedures.
            
             The player should constantly feel that they are moving deeper into the mystery.
            
             Avoid boring structures like:
             - Inspect the scene
             - Analyze logs
             - Interview witnesses
             - Do forensic analysis
             - Final report
            
             Those are scene-level activities, not chapters.
            
             Instead create chapters like:
            
             • The Missing Scroll
             • Shadows Inside the Archive
             • The Silent Restorer
             • The Secret Collectors
             • Beneath the Old Vault
             • The Price of the Truth
            
             Each chapter should open a new part of the world and naturally lead to the next one.
            
             A chapter should contain only:
            
             - id
             - title
             - purpose
             - locations
             - participants
             - facts
            
             Do NOT generate scenes.
            
             Do NOT generate gameplay.
            
             Do NOT generate dialogue.
            
             Do NOT generate transitions.
            
             Do NOT rewrite previous stages.
            
             Only organize existing information into an exciting adventure structure.
            
             Output JSON only.
            
             Schema:
            
             {
               "chapters": [
                 {
                   "id": "CH01",
                   "title": "",
                   "purpose": "",
                   "locations": [],
                   "participants": [],
                   "facts": []
                 }
               ]
             }
             IMPORTANT - QUEST_DESCRIPTION PROTECTION:
            
             Chapters must not reveal the hidden truth from QUEST_DESCRIPTION stage.
            
             Do not use:
             - truth explanations
             - final recontextualization
             - hidden motivations as confirmed facts
             - statements that prove one theory is correct
            
             The player should discover these through scenes.
            
             A chapter may reference:
             - questions
             - suspicions
             - contradictions
             - possible explanations
            
             A chapter must NOT state:
             - who is responsible
             - the real motive
             - the final meaning of events
             - that a false theory is wrong
             
             Text values should be in russian.
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.QUEST_OUTLINE;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage factsStage = requiredApprovedStage(project, StageType.FACTS);

        String userPrompt = buildUserPrompt(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                npcStage.getCurrentRevision().outputJson(),
                factsStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("QUEST_OUTLINE generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private String buildUserPrompt(
            QuestProject project,
            JsonNode mysteryJson,
            JsonNode worldJson,
            JsonNode npcJson,
            JsonNode factsJson
    ) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Build QUEST_OUTLINE stage artifact from approved mystery, world, NPC, and facts.

                project_name: %s
                quest_style: %s

                approved_mystery_json:
                %s

                approved_world_json:
                %s

                approved_npc_json:
                %s

                approved_facts_json:
                %s

                Requirements:
                - produce 3-8 investigation chapters
                - each chapter includes only id, title, purpose, locations, participants, facts
                - locations must reference WORLD location ids (L01, L02, ...)
                - participants must reference NPC ids (NPC01, NPC02, ...)
                - use fact ids from approved_facts_json
                - no scenes, no branching, no graph
                - all text in Russian
                """.formatted(project.getName(), style, mysteryJson, worldJson, npcJson, factsJson);
    }
}
