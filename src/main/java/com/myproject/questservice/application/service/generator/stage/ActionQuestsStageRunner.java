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

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActionQuestsStageRunner implements ActionQuestStageRunner, PromptPreviewStageRunner {
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
    private final ObjectMapper objectMapper;

    @Override
    public StageType type() {
        return StageType.ACTION_QUESTS;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        throw new ConflictException("ACTION_QUESTS supports way-by-way generation only");
    }

    @Override
    public JsonNode generateActionQuest(UUID projectId, String wayId, JsonNode currentOutput) {
        if (wayId == null || wayId.isBlank()) {
            throw new ConflictException("wayId is required");
        }
        String normalizedWayId = wayId.trim();

        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage scenesStage = requiredStageWithRevision(project, StageType.ACHIEVEMENT_SCENES);

        JsonNode waySceneNode = findByWayId(scenesStage.getCurrentRevision().outputJson().path("ways"), normalizedWayId);
        if (waySceneNode == null) {
            throw new NotFoundException("ACHIEVEMENT_SCENES way not found: " + normalizedWayId);
        }
        if (!waySceneNode.path("approved").asBoolean(false)) {
            throw new ConflictException("ACHIEVEMENT_SCENES way is not approved: " + normalizedWayId);
        }
        String achievementId = waySceneNode.path("achievement_id").asText("");
        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), normalizedWayId);

        String userPrompt = """
                Build ACTION_QUESTS for one way only.

                way_id: %s

                world_json:
                %s

                selected_realisation_way_json:
                %s

                selected_achievement_scenes_way_json:
                %s

                Requirements:
                - generate mini-quests only for actions in selected_achievement_scenes_way_json
                - each mini-quest must include a concrete situation and 2-4 meaningful choices
                - each choice must have clear consequence and risk level
                - keep KR2 quest tone and pacing
                - no dialogue screenplay format
                - all text in Russian
                """.formatted(
                normalizedWayId,
                worldStage.getCurrentRevision().outputJson(),
                wayNode == null ? "{}" : wayNode.toString(),
                waySceneNode.toString()
        );
        JsonNode generated = aiClient.generate(SYSTEM_PROMPT, userPrompt);
        return mergeWayOutput(currentOutput, generated, achievementId, normalizedWayId);
    }

    @Override
    public StagePromptPreview previewPrompt(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage scenesStage = requiredStageWithRevision(project, StageType.ACHIEVEMENT_SCENES);

        JsonNode wayScenes = scenesStage.getCurrentRevision().outputJson().path("ways");
        JsonNode firstWay = firstWayNode(wayScenes);
        if (firstWay == null) {
            throw new ConflictException("ACTION_QUESTS preview requires at least one generated ACHIEVEMENT_SCENES way");
        }
        String wayId = firstWay.path("way_id").asText("");
        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), wayId);
        String userPrompt = """
                Build ACTION_QUESTS for one way only.

                way_id: %s

                world_json:
                %s

                selected_realisation_way_json:
                %s

                selected_achievement_scenes_way_json:
                %s

                Requirements:
                - generate mini-quests only for actions in selected_achievement_scenes_way_json
                - each mini-quest must include a concrete situation and 2-4 meaningful choices
                - each choice must have clear consequence and risk level
                - keep KR2 quest tone and pacing
                - no dialogue screenplay format
                - all text in Russian
                """.formatted(
                wayId,
                worldStage.getCurrentRevision().outputJson(),
                wayNode == null ? "{}" : wayNode.toString(),
                firstWay
        );
        return new StagePromptPreview(SYSTEM_PROMPT, userPrompt);
    }

    @Override
    public StagePromptPreview previewActionQuestPrompt(UUID projectId, String wayId) {
        if (wayId == null || wayId.isBlank()) {
            throw new ConflictException("wayId is required");
        }
        String normalizedWayId = wayId.trim();
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage scenesStage = requiredStageWithRevision(project, StageType.ACHIEVEMENT_SCENES);

        JsonNode waySceneNode = findByWayId(scenesStage.getCurrentRevision().outputJson().path("ways"), normalizedWayId);
        if (waySceneNode == null) {
            throw new NotFoundException("ACHIEVEMENT_SCENES way not found: " + normalizedWayId);
        }
        if (!waySceneNode.path("approved").asBoolean(false)) {
            throw new ConflictException("ACHIEVEMENT_SCENES way is not approved: " + normalizedWayId);
        }
        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), normalizedWayId);

        String userPrompt = """
                Build ACTION_QUESTS for one way only.

                way_id: %s

                world_json:
                %s

                selected_realisation_way_json:
                %s

                selected_achievement_scenes_way_json:
                %s

                Requirements:
                - generate mini-quests only for actions in selected_achievement_scenes_way_json
                - each mini-quest must include a concrete situation and 2-4 meaningful choices
                - each choice must have clear consequence and risk level
                - keep KR2 quest tone and pacing
                - no dialogue screenplay format
                - all text in Russian
                """.formatted(
                normalizedWayId,
                worldStage.getCurrentRevision().outputJson(),
                wayNode == null ? "{}" : wayNode.toString(),
                waySceneNode
        );
        return new StagePromptPreview(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("ACTION_QUESTS generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private QuestStage requiredStageWithRevision(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getCurrentRevision() == null) {
            throw new ConflictException("ACTION_QUESTS generation requires generated " + type + " stage");
        }
        return stage;
    }

    private JsonNode findByWayId(JsonNode arrayNode, String wayId) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return null;
        }
        for (JsonNode item : arrayNode) {
            if (wayId.equalsIgnoreCase(item.path("way_id").asText(""))) {
                return item;
            }
        }
        return null;
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
                    ObjectNode node = way.deepCopy();
                    node.put("achievement_id", achievementId);
                    return node;
                }
            }
        }
        return null;
    }

    private JsonNode firstWayNode(JsonNode ways) {
        if (ways == null || !ways.isArray() || ways.isEmpty()) {
            return null;
        }
        return ways.get(0);
    }

    private JsonNode mergeWayOutput(JsonNode currentOutput, JsonNode generated, String achievementId, String wayId) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode ways = objectMapper.createArrayNode();
        if (currentOutput != null && currentOutput.path("ways").isArray()) {
            for (JsonNode existing : currentOutput.path("ways")) {
                if (!wayId.equalsIgnoreCase(existing.path("way_id").asText(""))) {
                    ways.add(existing);
                }
            }
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("achievement_id", achievementId);
        normalized.put("way_id", wayId);
        normalized.set("action_quests", generated.path("action_quests").isArray()
                ? generated.path("action_quests")
                : objectMapper.createArrayNode());
        ways.add(normalized);
        root.set("ways", ways);
        return root;
    }
}
