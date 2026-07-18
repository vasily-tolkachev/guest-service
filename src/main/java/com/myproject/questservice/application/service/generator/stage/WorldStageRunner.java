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
public class WorldStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a World Design Generator for a quest generation pipeline.

            Your task is to create ONLY the world design artifact for the WORLD stage.
            Input mystery is already approved and should be used as the foundation.

            You are NOT writing quest scenes, dialogues, or flow.
            You are NOT creating NPC biographies.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - No stage is allowed to rewrite or retell data from previous stages.

            Keep output concrete and structured for later NPC/FACTS stages.

            Return JSON with this schema:
            {
              "locations": [
                {
                  "id": "L01",
                  "name": "",
                  "purpose": ""
                }
              ],
              "organizations": [
                {
                  "id": "O01",
                  "name": ""
                }
              ],
              "rules": [""]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.WORLD;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = project.findStage(StageType.QUEST_DESCRIPTION)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + StageType.QUEST_DESCRIPTION));

        if (mysteryStage.getStatus() != StageStatus.APPROVED || mysteryStage.getCurrentRevision() == null) {
            throw new ConflictException("WORLD generation requires APPROVED MYSTERY stage");
        }

        String userPrompt = buildUserPrompt(project, mysteryStage.getCurrentRevision().outputJson());
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private String buildUserPrompt(QuestProject project, JsonNode approvedMysteryJson) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Build WORLD stage artifact from approved mystery.

                project_name: %s
                quest_style: %s

                approved_mystery_json:
                %s

                Requirements:
                - generate 3-8 locations with unique ids L01, L02, ...
                - generate 1-5 organizations with unique ids O01, O02, ...
                - generate 3-10 world rules as short statements
                - do not generate scenes, dialogues, or quest steps
                - all text in Russian
                """.formatted(project.getName(), style, approvedMysteryJson.toString());
    }
}
