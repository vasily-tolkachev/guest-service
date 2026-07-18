package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AchievementScenesStageRunner implements AchievementSceneStageRunner {
    private static final String SYSTEM_PROMPT = """
            You are an Achievement Scene Generator for a KR2-style quest pipeline.

            Generate scene/quest content for ONE realisation way only.
            This is manual per-way generation.

            Output MUST be valid JSON only.
            All JSON string values MUST be in Russian.
            Do NOT create entities not supported by previous stages.

            Return JSON with this schema:
            {
              "achievement_id": "A1",
              "way_id": "W1",
              "quests": [
                {
                  "id": "A1_Q01",
                  "title": "",
                  "situation": "",
                  "first_action": {
                    "id": "ACT01",
                    "text": ""
                  },
                  "choices": [
                    {
                      "id": "CH01",
                      "text": "",
                      "consequence": "",
                      "impact": "high|medium|low"
                    }
                  ]
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public StageType type() {
        return StageType.ACHIEVEMENT_SCENES;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        throw new ConflictException("ACHIEVEMENT_SCENES supports achievement-by-achievement generation only");
    }

    @Override
    public JsonNode generateAchievement(UUID projectId, String wayId, JsonNode currentOutput) {
        if (wayId == null || wayId.isBlank()) {
            throw new ConflictException("wayId is required");
        }
        String normalizedWayId = wayId.trim();

        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage descriptionStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage constraintsStage = requiredApprovedStage(project, StageType.QUEST_CONSTRAINTS);
        QuestStage analysisStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_RESOURCE_ANALYSIS);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage informationFlowStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_INFORMATION_FLOW);
        QuestStage knowledgeChainStage = requiredApprovedStage(project, StageType.KNOWLEDGE_CHAIN);

        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), normalizedWayId);
        if (wayNode == null) {
            throw new NotFoundException("Way not found in ACHIEVEMENT_REALISATION: " + normalizedWayId);
        }
        String achievementId = wayNode.path("achievement_id").asText("");

        String userPrompt = """
                Generate scenes for one way only.

                way_id: %s

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

                achievement_information_flow_json:
                %s

                knowledge_chain_json:
                %s

                way_json:
                %s

                Requirements:
                - generate 1-3 short quest entries for this way
                - style should feel like Space Rangers 2 quest episodes
                - use only world entities from world_json
                - align scenes with this exact realisation way
                - take one meaningful first_action and convert it into a choice-driven quest situation
                - each choice must have a clear consequence and meaningful impact
                - no global endings or unrelated ways
                """.formatted(
                normalizedWayId,
                descriptionStage.getCurrentRevision().outputJson(),
                constraintsStage.getCurrentRevision().outputJson(),
                analysisStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                realisationStage.getCurrentRevision().outputJson(),
                informationFlowStage.getCurrentRevision().outputJson(),
                knowledgeChainStage.getCurrentRevision().outputJson(),
                wayNode
        );

        JsonNode generated = aiClient.generate(SYSTEM_PROMPT, userPrompt);
        return mergeAchievementOutput(currentOutput, generated, achievementId, normalizedWayId);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("ACHIEVEMENT_SCENES generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private JsonNode findWay(JsonNode realisationJson, String wayId) {
        JsonNode realisations = realisationJson.path("achievement_realisations");
        if (!realisations.isArray()) {
            return null;
        }
        for (JsonNode realisation : realisations) {
            String achievementId = realisation.path("achievement_id").asText("");
            JsonNode ways = realisation.path("ways");
            if (!ways.isArray()) {
                continue;
            }
            for (JsonNode way : ways) {
                if (wayId.equalsIgnoreCase(way.path("id").asText(""))) {
                    ObjectNode wayWithAchievement = way.deepCopy();
                    wayWithAchievement.put("achievement_id", achievementId);
                    return wayWithAchievement;
                }
            }
        }
        return null;
    }

    private JsonNode mergeAchievementOutput(JsonNode currentOutput, JsonNode generated, String achievementId, String wayId) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode ways = objectMapper.createArrayNode();
        Set<String> seen = new HashSet<>();

        if (currentOutput != null && currentOutput.path("ways").isArray()) {
            for (JsonNode existing : currentOutput.path("ways")) {
                String id = existing.path("way_id").asText("");
                if (!id.equalsIgnoreCase(wayId) && !id.isBlank()) {
                    ways.add(existing);
                    seen.add(id.toUpperCase());
                }
            }
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("achievement_id", achievementId);
        normalized.put("way_id", wayId);
        normalized.set("quests", generated.path("quests").isArray() ? generated.path("quests") : objectMapper.createArrayNode());
        ways.add(normalized);
        seen.add(wayId.toUpperCase());

        root.set("ways", ways);
        return root;
    }
}
