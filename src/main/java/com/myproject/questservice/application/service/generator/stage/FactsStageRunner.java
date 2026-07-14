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
            You are a Fact Graph Design Generator for a quest generation pipeline.

            Your task is to create ONLY the FACTS stage artifact.
            Inputs are approved mystery, world, and npc artifacts.

            You are NOT writing scenes, dialogues, or quest flow.
            You are NOT generating final quest text.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.

            Return JSON with this schema:
            {
              "facts": [
                {
                  "id": "",
                  "statement": "",
                  "visibility": "public|hidden|contested",
                  "related_roles": [""],
                  "interpretation_risk": ""
                }
              ],
              "contradictions": [
                {
                  "between_fact_ids": ["", ""],
                  "apparent_conflict": "",
                  "why_both_seem_true": ""
                }
              ],
              "fact_progression_notes": [""],
              "reveal_pressure_points": [""]
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
                Build FACTS stage artifact from approved mystery, world, and npc data.

                project_name: %s
                quest_style: %s

                approved_mystery_json:
                %s

                approved_world_json:
                %s

                approved_npc_json:
                %s

                Requirements:
                - facts should be high-value for investigation reasoning
                - include ambiguity and competing interpretations
                - avoid exact clue placement, scene scripting, or dialogue
                - keep output actionable for FLOW stage
                - all text in Russian
                """.formatted(
                project.getName(),
                style,
                mysteryJson.toPrettyString(),
                worldJson.toPrettyString(),
                npcJson.toPrettyString()
        );
    }
}

