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
public class AchievementInformationFlowStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are an Achievement Information Flow Generator for a quest generation pipeline.

            Task:
            Describe how the player obtains the needed information for each achievement.

            This stage is AFTER ACHIEVEMENT_REALISATION and BEFORE ACHIEVEMENT_SCENES.

            Do NOT generate dialogues, full scenes, branching graph, or walkthrough.
            Keep output as structured information-acquisition flow only.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - No stage is allowed to rewrite or retell data from previous stages.

            Return JSON with this schema:
            {
              "achievement_information_flow": [
                {
                  "achievement_id": "A1",
                  "knowledge_targets": [""],
                  "information_sources": [""],
                  "acquisition_methods": [""],
                  "verification_signals": [""],
                  "blocking_factors": [""]
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.ACHIEVEMENT_INFORMATION_FLOW;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage descriptionStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage constraintsStage = requiredApprovedStage(project, StageType.QUEST_CONSTRAINTS);
        QuestStage analysisStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_RESOURCE_ANALYSIS);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);

        String userPrompt = """
                Build ACHIEVEMENT_INFORMATION_FLOW from approved stages:
                QUEST_DESCRIPTION, QUEST_CONSTRAINTS, ACHIEVEMENT_RESOURCE_ANALYSIS, WORLD, ACHIEVEMENT_REALISATION.

                quest_description_json:
                %s

                constraints_json:
                %s

                achievement_resource_analysis_json:
                %s

                world_json:
                %s

                achievement_realisation_json:
                %s

                Requirements:
                - for each achievement from QUEST_DESCRIPTION.achievements create one flow block
                - explain how player gets required knowledge/information
                - keep it abstract but actionable for later scene generation
                - no dialogues, no cinematic text
                - all text in Russian
                """.formatted(
                descriptionStage.getCurrentRevision().outputJson(),
                constraintsStage.getCurrentRevision().outputJson(),
                analysisStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                realisationStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("ACHIEVEMENT_INFORMATION_FLOW generation requires APPROVED " + type + " stage");
        }
        return stage;
    }
}
