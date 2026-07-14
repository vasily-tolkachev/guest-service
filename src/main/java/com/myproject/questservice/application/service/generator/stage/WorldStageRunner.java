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

            Keep output abstract enough for later NPC/FACTS/FLOW stages.

            Return JSON with this schema:
            {
              "world_premise": "",
              "environmental_rules": [""],
              "factions_or_forces": [""],
              "public_beliefs": [""],
              "hidden_pressures": [""],
              "world_tensions": [""],
              "world_recontextualization_axis": ""
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

        QuestStage mysteryStage = project.findStage(StageType.MYSTERY)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + StageType.MYSTERY));

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
                - derive world assumptions from mystery ambiguity and reinterpretation arc
                - do not invent exact locations, named characters, or quest steps
                - keep it actionable for next stages
                - all text in Russian
                """.formatted(project.getName(), style, approvedMysteryJson.toPrettyString());
    }
}

