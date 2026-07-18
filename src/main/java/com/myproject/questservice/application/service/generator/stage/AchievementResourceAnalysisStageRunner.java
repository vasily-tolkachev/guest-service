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
public class AchievementResourceAnalysisStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are an Achievement Resource Analysis Generator for a quest generation pipeline.

            Your task is to analyze what can be used to achieve quest achievements.
            This stage is BEFORE WORLD and AFTER QUEST_CONSTRAINTS.

            Do NOT generate concrete locations, named NPCs, scenes, dialogues, or walkthrough.
            Keep everything abstract and reusable by WORLD stage.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - No stage is allowed to rewrite or retell data from previous stages.

            Return JSON with this schema:
            {
              "achievement_capabilities": [
                {
                  "achievement_id": "A1",
                  "physical_resources": [""],
                  "natural_factors": [""],
                  "objects_tools": [""],
                  "knowledge_skills": [""],
                  "limitations": [""]
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.ACHIEVEMENT_RESOURCE_ANALYSIS;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage descriptionStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage constraintsStage = requiredApprovedStage(project, StageType.QUEST_CONSTRAINTS);

        String userPrompt = """
                Build ACHIEVEMENT_RESOURCE_ANALYSIS from approved QUEST_DESCRIPTION and QUEST_CONSTRAINTS.

                approved_quest_description_json:
                %s

                approved_constraints_json:
                %s

                Requirements:
                - produce analysis for each achievement from QUEST_DESCRIPTION.achievements
                - focus only on usable capability categories, not concrete world entities
                - keep entries concise and practical
                - each list should contain 1-6 entries
                - all text in Russian
                """.formatted(
                descriptionStage.getCurrentRevision().outputJson().toString(),
                constraintsStage.getCurrentRevision().outputJson().toString()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("ACHIEVEMENT_RESOURCE_ANALYSIS generation requires APPROVED " + type + " stage");
        }
        return stage;
    }
}
