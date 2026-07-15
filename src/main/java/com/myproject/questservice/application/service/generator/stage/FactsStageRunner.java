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
public class FactsStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Facts Generator for a quest generation pipeline.

            Your task is to create ONLY canonical facts for the FACTS stage.
            Inputs are approved mystery, world, and NPC artifacts.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - Facts must be canonical statements only.
            - Do NOT include owner, unlocks, mandatory, visibility, graph, or any extra metadata.
            - No stage is allowed to rewrite or retell data from previous stages.

            Return JSON with this schema:
            {
              "facts": [
                {
                  "id": "F01",
                  "description": ""
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.FACTS;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.MYSTERY);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.NPC);

        String userPrompt = buildUserPrompt(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                npcStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("FACTS generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private String buildUserPrompt(QuestProject project, JsonNode mysteryJson, JsonNode worldJson, JsonNode npcJson) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Build FACTS stage artifact from approved mystery, world, and NPC.

                project_name: %s
                quest_style: %s

                approved_mystery_json:
                %s

                approved_world_json:
                %s

                approved_npc_json:
                %s

                Requirements:
                - generate 10-20 canonical investigation facts
                - each fact must have only id and description
                - ids must be unique and formatted as F01, F02, ...
                - do not output any extra fields
                - all text in Russian
                """.formatted(project.getName(), style, mysteryJson, worldJson, npcJson);
    }
}
