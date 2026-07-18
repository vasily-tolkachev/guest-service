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
public class ActionQuestsStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are an Action Quest Generator for a KR2-style quest pipeline.

            Goal:
            For each action found in ACHIEVEMENT_SCENES, generate an interesting standalone mini-quest
            in the style of Space Rangers 2 textual quests.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - Keep style close to KR2 mission tone: concise setup, tension, meaningful choices, consequences.
            - Choices must matter and lead to different outcomes.
            - Do NOT introduce entities that contradict WORLD and previous approved stages.

            Return JSON with this schema:
            {
              "action_quests": [
                {
                  "way_id": "W1",
                  "source_quest_id": "A1_Q01",
                  "source_action_id": "ACT01",
                  "title": "",
                  "situation": "",
                  "choices": [
                    {
                      "id": "C1",
                      "text": "",
                      "consequence": "",
                      "risk_level": "low|medium|high"
                    }
                  ],
                  "best_case": "",
                  "worst_case": ""
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.ACTION_QUESTS;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage scenesStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_SCENES);

        String userPrompt = """
                Build ACTION_QUESTS from approved WORLD, ACHIEVEMENT_REALISATION, and ACHIEVEMENT_SCENES.

                world_json:
                %s

                achievement_realisation_json:
                %s

                achievement_scenes_json:
                %s

                Requirements:
                - generate one mini-quest for each source action in achievement_scenes_json
                - each mini-quest must include a concrete situation and 2-4 meaningful choices
                - each choice must have clear consequence and risk level
                - keep KR2 quest tone and pacing
                - no dialogue screenplay format
                - all text in Russian
                """.formatted(
                worldStage.getCurrentRevision().outputJson(),
                realisationStage.getCurrentRevision().outputJson(),
                scenesStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("ACTION_QUESTS generation requires APPROVED " + type + " stage");
        }
        return stage;
    }
}
