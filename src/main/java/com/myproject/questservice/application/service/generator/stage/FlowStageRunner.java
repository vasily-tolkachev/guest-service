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
public class FlowStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Quest Graph Design Generator for a quest generation pipeline.

            Your task is to create ONLY the QUEST_GRAPH stage artifact.
            Inputs are approved mystery, world, npc, and facts artifacts.

            You are NOT writing prose scenes or dialogues.
            You are designing graph structure directly convertible to quest DSL.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.

            Return JSON with this schema:
            {
              "nodes": [
                {
                  "id": "",
                  "purpose": "",
                  "required_facts": [""],
                  "revealed_facts": [""],
                  "participants": [""],
                  "choices": [
                    {
                      "text": "",
                      "next": ""
                    }
                  ]
                }
              ],
              "endings": [""]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.QUEST_GRAPH;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.MYSTERY);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.NPC);
        QuestStage factsStage = requiredApprovedStage(project, StageType.FACTS);

        String userPrompt = buildUserPrompt(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                npcStage.getCurrentRevision().outputJson(),
                factsStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("QUEST_GRAPH generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private String buildUserPrompt(
            QuestProject project,
            JsonNode mysteryJson,
            JsonNode worldJson,
            JsonNode npcJson,
            JsonNode factsJson
    ) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Build QUEST_GRAPH stage artifact from approved mystery, world, npc, and facts data.

                project_name: %s
                quest_style: %s

                approved_mystery_json:
                %s

                approved_world_json:
                %s

                approved_npc_json:
                %s

                approved_facts_json:
                %s

                Requirements:
                - output node-and-edge quest graph
                - each node should have clear purpose and choice links
                - avoid prose atmosphere blocks and dialogue scripts
                - keep output directly mappable to quest DSL
                - all text in Russian
                """.formatted(
                project.getName(),
                style,
                mysteryJson.toString(),
                worldJson.toString(),
                npcJson.toString(),
                factsJson.toString()
        );
    }
}
