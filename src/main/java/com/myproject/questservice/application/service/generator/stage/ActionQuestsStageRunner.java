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
            You are an Action Resolution Generator for a KR2-style quest pipeline.

            Goal:
            Resolve ONE chosen player action from ONE scene.
            This stage happens AFTER player picked action, so now you can reveal results.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - Keep style close to KR2 mission tone: exploration, uncertainty, discovery.
            - Do NOT introduce entities that contradict WORLD and previous approved stages.

            Return JSON with this schema:
            {
              "way_id": "W1",
              "scene_id": "A1_S01",
              "action_id": "ACT01",
              "resolution": {
                "revealed_info": [],
                "world_state_changes": [],
                "new_actions_unlocked": [],
                "risks_triggered": []
              }
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
        JsonNode scenes = waySceneNode.path("scenes");
        if (!scenes.isArray() || scenes.isEmpty()) {
            throw new ConflictException("ACHIEVEMENT_SCENES way has no scenes: " + normalizedWayId);
        }
        JsonNode firstScene = scenes.get(0);
        String sceneId = firstScene.path("id").asText("");
        JsonNode actions = firstScene.path("available_actions");
        if (!actions.isArray() || actions.isEmpty()) {
            throw new ConflictException("Selected scene has no available actions: " + sceneId);
        }
        String actionId = actions.get(0).path("id").asText("");
        return generateActionResolution(projectId, normalizedWayId, sceneId, actionId, currentOutput);
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
        JsonNode scenes = waySceneNode.path("scenes");
        if (!scenes.isArray() || scenes.isEmpty()) {
            throw new ConflictException("ACHIEVEMENT_SCENES way has no scenes: " + normalizedWayId);
        }
        JsonNode firstScene = scenes.get(0);
        String sceneId = firstScene.path("id").asText("");
        JsonNode actions = firstScene.path("available_actions");
        if (!actions.isArray() || actions.isEmpty()) {
            throw new ConflictException("Selected scene has no available actions: " + sceneId);
        }
        String actionId = actions.get(0).path("id").asText("");
        return previewActionResolutionPrompt(projectId, normalizedWayId, sceneId, actionId);
    }

    @Override
    public JsonNode generateActionResolution(UUID projectId, String wayId, String sceneId, String actionId, JsonNode currentOutput) {
        StagePromptPreview preview = previewActionResolutionPrompt(projectId, wayId, sceneId, actionId);
        JsonNode generated = aiClient.generate(preview.systemPrompt(), preview.userPrompt());

        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), wayId);
        String achievementId = wayNode == null ? "" : wayNode.path("achievement_id").asText("");
        return mergeResolutionOutput(currentOutput, generated, achievementId, wayId, sceneId, actionId);
    }

    @Override
    public StagePromptPreview previewActionResolutionPrompt(UUID projectId, String wayId, String sceneId, String actionId) {
        if (wayId == null || wayId.isBlank() || sceneId == null || sceneId.isBlank() || actionId == null || actionId.isBlank()) {
            throw new ConflictException("wayId, sceneId and actionId are required");
        }
        String normalizedWayId = wayId.trim();
        String normalizedSceneId = sceneId.trim();
        String normalizedActionId = actionId.trim();

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

        JsonNode selectedScene = findScene(waySceneNode.path("scenes"), normalizedSceneId);
        if (selectedScene == null) {
            throw new NotFoundException("Scene not found in way: " + normalizedSceneId);
        }
        JsonNode selectedAction = findAction(selectedScene.path("available_actions"), normalizedActionId);
        if (selectedAction == null) {
            throw new NotFoundException("Action not found in scene: " + normalizedActionId);
        }

        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), normalizedWayId);
        String userPrompt = """
                Resolve one selected player action.

                way_id: %s
                scene_id: %s
                action_id: %s

                world_json:
                %s

                selected_realisation_way_json:
                %s

                selected_scene_json:
                %s

                selected_action_json:
                %s

                Requirements:
                - output exactly one resolution block
                - reveal concrete new information discovered after action
                - include world state changes caused by action
                - include what new actions become available next
                - do not rewrite global quest, resolve only this action
                - all text in Russian
                """.formatted(
                normalizedWayId,
                normalizedSceneId,
                normalizedActionId,
                worldStage.getCurrentRevision().outputJson(),
                wayNode == null ? "{}" : wayNode.toString(),
                selectedScene.toString(),
                selectedAction.toString()
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

    private JsonNode findScene(JsonNode scenesArray, String sceneId) {
        if (scenesArray == null || !scenesArray.isArray()) {
            return null;
        }
        for (JsonNode scene : scenesArray) {
            if (sceneId.equalsIgnoreCase(scene.path("id").asText(""))) {
                return scene;
            }
        }
        return null;
    }

    private JsonNode findAction(JsonNode actionsArray, String actionId) {
        if (actionsArray == null || !actionsArray.isArray()) {
            return null;
        }
        for (JsonNode action : actionsArray) {
            if (actionId.equalsIgnoreCase(action.path("id").asText(""))) {
                return action;
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

    private JsonNode mergeResolutionOutput(
            JsonNode currentOutput,
            JsonNode generated,
            String achievementId,
            String wayId,
            String sceneId,
            String actionId
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode ways = objectMapper.createArrayNode();
        ObjectNode targetWay = null;

        if (currentOutput != null && currentOutput.path("ways").isArray()) {
            for (JsonNode existing : currentOutput.path("ways")) {
                if (wayId.equalsIgnoreCase(existing.path("way_id").asText(""))) {
                    targetWay = existing.deepCopy();
                } else {
                    ways.add(existing);
                }
            }
        }

        if (targetWay == null) {
            targetWay = objectMapper.createObjectNode();
            targetWay.put("achievement_id", achievementId);
            targetWay.put("way_id", wayId);
            targetWay.set("resolutions", objectMapper.createArrayNode());
        } else if (!targetWay.path("resolutions").isArray()) {
            targetWay.set("resolutions", objectMapper.createArrayNode());
        }

        ArrayNode updatedResolutions = objectMapper.createArrayNode();
        for (JsonNode existingResolution : targetWay.path("resolutions")) {
            boolean same = sceneId.equalsIgnoreCase(existingResolution.path("scene_id").asText(""))
                    && actionId.equalsIgnoreCase(existingResolution.path("action_id").asText(""));
            if (!same) {
                updatedResolutions.add(existingResolution);
            }
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("way_id", wayId);
        normalized.put("scene_id", sceneId);
        normalized.put("action_id", actionId);
        normalized.set("resolution", generated.path("resolution").isObject()
                ? generated.path("resolution")
                : objectMapper.createObjectNode());
        updatedResolutions.add(normalized);
        targetWay.set("resolutions", updatedResolutions);
        ways.add(targetWay);
        root.set("ways", ways);
        return root;
    }
}
