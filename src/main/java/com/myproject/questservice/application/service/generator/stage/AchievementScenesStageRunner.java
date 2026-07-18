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

            Generate scene/quest content for ONE achievement only.
            This is manual per-achievement generation.

            Output MUST be valid JSON only.
            All JSON string values MUST be in Russian.
            Do NOT create entities not supported by previous stages.

            Return JSON with this schema:
            {
              "achievement_id": "A1",
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
    public JsonNode generateAchievement(UUID projectId, String achievementId, JsonNode currentOutput) {
        if (achievementId == null || achievementId.isBlank()) {
            throw new ConflictException("achievementId is required");
        }
        String normalizedAchievementId = achievementId.trim();

        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage descriptionStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage constraintsStage = requiredApprovedStage(project, StageType.QUEST_CONSTRAINTS);
        QuestStage analysisStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_RESOURCE_ANALYSIS);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage informationFlowStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_INFORMATION_FLOW);
        QuestStage knowledgeChainStage = requiredApprovedStage(project, StageType.KNOWLEDGE_CHAIN);

        JsonNode achievementNode = findAchievement(descriptionStage.getCurrentRevision().outputJson(), normalizedAchievementId);
        if (achievementNode == null) {
            throw new NotFoundException("Achievement not found in QUEST_DESCRIPTION: " + normalizedAchievementId);
        }

        String userPrompt = """
                Generate scenes for one achievement only.

                achievement_id: %s

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

                achievement_json:
                %s

                Requirements:
                - generate 1-3 short quest entries for this achievement
                - style should feel like Space Rangers 2 quest episodes
                - use only world entities from world_json
                - align scenes with realisation ways for this achievement
                - take one meaningful first_action and convert it into a choice-driven quest situation
                - each choice must have a clear consequence and meaningful impact
                - no global endings or unrelated achievements
                """.formatted(
                normalizedAchievementId,
                descriptionStage.getCurrentRevision().outputJson(),
                constraintsStage.getCurrentRevision().outputJson(),
                analysisStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                realisationStage.getCurrentRevision().outputJson(),
                informationFlowStage.getCurrentRevision().outputJson(),
                knowledgeChainStage.getCurrentRevision().outputJson(),
                achievementNode
        );

        JsonNode generated = aiClient.generate(SYSTEM_PROMPT, userPrompt);
        return mergeAchievementOutput(currentOutput, generated, normalizedAchievementId);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("ACHIEVEMENT_SCENES generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private JsonNode findAchievement(JsonNode descriptionJson, String achievementId) {
        JsonNode achievements = descriptionJson.path("achievements");
        if (!achievements.isArray()) {
            return null;
        }
        for (JsonNode achievement : achievements) {
            if (achievementId.equalsIgnoreCase(achievement.path("id").asText(""))) {
                return achievement;
            }
        }
        return null;
    }

    private JsonNode mergeAchievementOutput(JsonNode currentOutput, JsonNode generated, String achievementId) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode achievements = objectMapper.createArrayNode();
        Set<String> seen = new HashSet<>();

        if (currentOutput != null && currentOutput.path("achievements").isArray()) {
            for (JsonNode existing : currentOutput.path("achievements")) {
                String id = existing.path("achievement_id").asText("");
                if (!id.equalsIgnoreCase(achievementId) && !id.isBlank()) {
                    achievements.add(existing);
                    seen.add(id.toUpperCase());
                }
            }
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("achievement_id", achievementId);
        normalized.set("quests", generated.path("quests").isArray() ? generated.path("quests") : objectMapper.createArrayNode());
        achievements.add(normalized);
        seen.add(achievementId.toUpperCase());

        root.set("achievements", achievements);
        return root;
    }
}
