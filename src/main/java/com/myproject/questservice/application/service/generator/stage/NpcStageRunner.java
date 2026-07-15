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
            You are an NPC Design Generator for a quest generation pipeline.

            Your task is to create ONLY the NPC design artifact for the NPC stage.
            Inputs are approved mystery and approved world artifacts.

            You are NOT writing scenes, dialogues, or quest flow.
            Keep NPC definitions role-based and expandable for later stages.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - No stage is allowed to rewrite or retell data from previous stages.

            Return JSON with this schema:
            {
              "npcs": [
                {
                  "id": "NPC01",
                  "name": "",
                  "role": "",
                  "organization": "O01",
                  "motivation": ""
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.NPC;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.MYSTERY);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);

        String userPrompt = buildUserPrompt(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("NPC generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private String buildUserPrompt(QuestProject project, JsonNode mysteryJson, JsonNode worldJson) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Build NPC stage artifact from approved mystery and world.

                project_name: %s
                quest_style: %s

                approved_mystery_json:
                %s

                approved_world_json:
                %s

                Requirements:
                - generate 4-12 NPC entries
                - ids must be unique and formatted as NPC01, NPC02, ...
                - organization must reference organization ids from WORLD (O01, O02, ...)
                - use concise names and roles, no dialogue and no scene planning
                - all text in Russian
                """.formatted(project.getName(), style, mysteryJson.toString(), worldJson.toString());
    }
}
