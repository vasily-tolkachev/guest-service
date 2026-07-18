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
public class NpcStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are an Achievement Realisation Generator for a quest generation pipeline.

            Your task is to create ONLY the ACHIEVEMENT_REALISATION artifact.
            Inputs are approved QUEST_DESCRIPTION, QUEST_CONSTRAINTS, and WORLD artifacts.

            Your task is to produce possible ways to reach achievements within current world and constraints.
            Do NOT create new world entities.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - No stage is allowed to rewrite or retell data from previous stages.

            Return JSON with this schema:
            {
              "achievement_realisations": [
                {
                  "achievement_id": "A1",
                  "ways": [
                    {
                      "id": "W1",
                      "description": "",
                      "uses_world_elements": ["L01", "O01"],
                      "fits_constraints": ""
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
        return StageType.ACHIEVEMENT_REALISATION;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage constraintsStage = requiredApprovedStage(project, StageType.QUEST_CONSTRAINTS);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);

        String userPrompt = buildUserPrompt(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                constraintsStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("ACHIEVEMENT_REALISATION generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private String buildUserPrompt(QuestProject project, JsonNode mysteryJson, JsonNode constraintsJson, JsonNode worldJson) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Build ACHIEVEMENT_REALISATION artifact from approved QUEST_DESCRIPTION, QUEST_CONSTRAINTS, and WORLD.

                project_name: %s
                quest_style: %s

                approved_mystery_json:
                %s

                approved_constraints_json:
                %s

                approved_world_json:
                %s

                Requirements:
                - for each achievement from QUEST_DESCRIPTION.achievements generate 2-4 possible ways
                - each way must stay within approved_constraints_json limits
                - uses_world_elements must reference existing WORLD ids (Lxx, Oxx, NPCxx if present)
                - do not create new ids that are not in input world
                - keep ways concise and implementation-level, but without scene/dialogue writing
                - all text in Russian
                """.formatted(project.getName(), style, mysteryJson.toString(), constraintsJson.toString(), worldJson.toString());
    }
}
