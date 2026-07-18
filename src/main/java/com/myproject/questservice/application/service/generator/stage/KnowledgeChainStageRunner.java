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
public class KnowledgeChainStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Knowledge Chain Generator for a quest generation pipeline.

            Task:
            Build knowledge-acquisition chains for each achievement.

            The chain answers:
            "How does the player understand what to do?"

            IMPORTANT:
            - Achievement remains high-level (for example: "Open bunker").
            - Chain steps are NOT achievement steps.
            - Chain steps are knowledge discovery steps.

            Output MUST be valid JSON only.
            All JSON string values MUST be in Russian.

            Return JSON with this schema:
            {
              "knowledge_chains": [
                {
                  "achievement_id": "A1",
                  "target_achievement": "",
                  "knowledge_chain": [
                    {
                      "id": "K1",
                      "action": "",
                      "knowledge_gained": "",
                      "leads_to": "K2"
                    }
                  ],
                  "entry_point": "K1",
                  "final_knowledge": ""
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.KNOWLEDGE_CHAIN;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage descriptionStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage informationFlowStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_INFORMATION_FLOW);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);

        String userPrompt = """
                Build KNOWLEDGE_CHAIN from approved QUEST_DESCRIPTION, ACHIEVEMENT_INFORMATION_FLOW and WORLD.

                quest_description_json:
                %s

                achievement_information_flow_json:
                %s

                world_json:
                %s

                Requirements:
                - produce one chain per achievement
                - each chain must have 4-8 linked knowledge steps
                - each step must describe how new knowledge is obtained
                - chain should be playable and logically connected
                - do not turn chain into direct achievement checklist
                - all text in Russian
                """.formatted(
                descriptionStage.getCurrentRevision().outputJson(),
                informationFlowStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("KNOWLEDGE_CHAIN generation requires APPROVED " + type + " stage");
        }
        return stage;
    }
}
