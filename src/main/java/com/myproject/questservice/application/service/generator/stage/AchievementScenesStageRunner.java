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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AchievementScenesStageRunner implements AchievementSceneStageRunner, PromptPreviewStageRunner {
    private static final String[] INTERPRETATION_MARKERS = {
            "лучше", "хуже", "оптималь", "пригодн", "идеальн", "правильн", "неправильн",
            "выгодн", "невыгодн", "эффективн", "неэффективн", "безопасн", "опасн",
            "шанс", "вероятн", "риск", "снижает", "повышает", "рекомендуется"
    };
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
              "scenes": [
                {
                  "id": "A1_S01",
                  "title": "",
                  "world_state": {
                    "known_facts": [],
                    "unknowns": [],
                    "constraints": []
                  },
                  "available_actions": [
                    {
                      "id": "ACT01",
                      "text": "",
                      "intent": "observe|probe|move|craft|risk"
                    }
                  ],
                  "leads_to_scene_ids": [],
                  "next_scene_hooks": []
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private static final int MAX_CONTEXT_CHARS = 9_000;

    @Override
    public StageType type() {
        return StageType.ACHIEVEMENT_SCENES;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        throw new ConflictException("ACHIEVEMENT_SCENES supports achievement-by-achievement generation only");
    }

    @Override
    public StagePromptPreview previewPrompt(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage descriptionStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage constraintsStage = requiredApprovedStage(project, StageType.QUEST_CONSTRAINTS);
        QuestStage analysisStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_RESOURCE_ANALYSIS);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage informationFlowStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_INFORMATION_FLOW);
        QuestStage knowledgeChainStage = requiredApprovedStage(project, StageType.KNOWLEDGE_CHAIN);

        JsonNode firstWay = firstWayNode(realisationStage.getCurrentRevision().outputJson());
        if (firstWay == null) {
            throw new ConflictException("ACHIEVEMENT_SCENES preview requires at least one way in ACHIEVEMENT_REALISATION");
        }
        String wayId = firstWay.path("id").asText("");
        String achievementId = firstWay.path("achievement_id").asText("");
        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), wayId);
        JsonNode infoFlowNode = findByWayId(informationFlowStage.getCurrentRevision().outputJson().path("achievement_information_flow"), wayId);
        JsonNode knowledgeChainNode = findByWayId(knowledgeChainStage.getCurrentRevision().outputJson().path("knowledge_chains"), wayId);
        JsonNode scopedWorld = buildScopedWorld(worldStage.getCurrentRevision().outputJson(), wayNode);

        String userPrompt = """
                Generate scenes for one way only.

                way_id: %s

                constraints_json:
                %s

                achievement_resource_analysis_json:
                %s

                scoped_world_json:
                %s

                selected_way_json:
                %s

                selected_information_flow_json:
                %s

                selected_knowledge_chain_json:
                %s

                Requirements:
                - generate 4-8 short micro-scenes for this way
                - format must use scenes/world_state/available_actions schema
                - each scene must include leads_to_scene_ids for non-linear graph progression
                - do NOT include outcome/consequence text for actions in this stage
                - do NOT include interpretation labels like "good/bad/optimal/suitable"
                - write only observable, objective facts in world_state
                - each scene should expose unknowns and investigation-oriented actions
                - style should feel like Space Rangers 2 exploration of unknown world
                - use only world entities from world_json
                - align scenes with this exact realisation way
                - no global endings or unrelated ways
                """.formatted(
                wayId,
                compactJson(constraintsStage.getCurrentRevision().outputJson()),
                compactJson(analysisStage.getCurrentRevision().outputJson()),
                compactJson(scopedWorld),
                compactJson(wayNode),
                compactJson(infoFlowNode),
                compactJson(knowledgeChainNode)
        );
        return new StagePromptPreview(SYSTEM_PROMPT, userPrompt + "\n\npreview_for_achievement_id: " + achievementId);
    }

    @Override
    public StagePromptPreview previewAchievementPrompt(UUID projectId, String wayId) {
        if (wayId == null || wayId.isBlank()) {
            throw new ConflictException("wayId is required");
        }
        String normalizedWayId = wayId.trim();
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage constraintsStage = requiredApprovedStage(project, StageType.QUEST_CONSTRAINTS);
        QuestStage analysisStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_RESOURCE_ANALYSIS);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        QuestStage informationFlowStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_INFORMATION_FLOW);
        QuestStage knowledgeChainStage = requiredStageWithRevision(project, StageType.KNOWLEDGE_CHAIN);

        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), normalizedWayId);
        if (wayNode == null) {
            throw new NotFoundException("Way not found in ACHIEVEMENT_REALISATION: " + normalizedWayId);
        }
        JsonNode infoFlowNode = findByWayId(informationFlowStage.getCurrentRevision().outputJson().path("achievement_information_flow"), normalizedWayId);
        JsonNode knowledgeChainNode = findByWayId(knowledgeChainStage.getCurrentRevision().outputJson().path("knowledge_chains"), normalizedWayId);
        ensureKnowledgeChainWayApproved(knowledgeChainNode, normalizedWayId);
        JsonNode scopedWorld = buildScopedWorld(worldStage.getCurrentRevision().outputJson(), wayNode);

        String userPrompt = """
                Generate scenes for one way only.

                way_id: %s

                constraints_json:
                %s

                achievement_resource_analysis_json:
                %s

                scoped_world_json:
                %s

                selected_way_json:
                %s

                selected_information_flow_json:
                %s

                selected_knowledge_chain_json:
                %s

                Requirements:
                - generate 4-8 short micro-scenes for this way
                - format must use scenes/world_state/available_actions schema
                - each scene must include leads_to_scene_ids for non-linear graph progression
                - do NOT include outcome/consequence text for actions in this stage
                - do NOT include interpretation labels like "good/bad/optimal/suitable"
                - write only observable, objective facts in world_state
                - each scene should expose unknowns and investigation-oriented actions
                - style should feel like Space Rangers 2 exploration of unknown world
                - use only world entities from world_json
                - align scenes with this exact realisation way
                - no global endings or unrelated ways
                """.formatted(
                normalizedWayId,
                compactJson(constraintsStage.getCurrentRevision().outputJson()),
                compactJson(analysisStage.getCurrentRevision().outputJson()),
                compactJson(scopedWorld),
                compactJson(wayNode),
                compactJson(infoFlowNode),
                compactJson(knowledgeChainNode)
        );
        return new StagePromptPreview(SYSTEM_PROMPT, userPrompt);
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
        QuestStage knowledgeChainStage = requiredStageWithRevision(project, StageType.KNOWLEDGE_CHAIN);

        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), normalizedWayId);
        if (wayNode == null) {
            throw new NotFoundException("Way not found in ACHIEVEMENT_REALISATION: " + normalizedWayId);
        }
        String achievementId = wayNode.path("achievement_id").asText("");
        JsonNode infoFlowNode = findByWayId(informationFlowStage.getCurrentRevision().outputJson().path("achievement_information_flow"), normalizedWayId);
        JsonNode knowledgeChainNode = findByWayId(knowledgeChainStage.getCurrentRevision().outputJson().path("knowledge_chains"), normalizedWayId);
        ensureKnowledgeChainWayApproved(knowledgeChainNode, normalizedWayId);
        JsonNode scopedWorld = buildScopedWorld(worldStage.getCurrentRevision().outputJson(), wayNode);

        String userPrompt = """
                Generate scenes for one way only.

                way_id: %s

                constraints_json:
                %s

                achievement_resource_analysis_json:
                %s

                scoped_world_json:
                %s

                selected_way_json:
                %s

                selected_information_flow_json:
                %s

                selected_knowledge_chain_json:
                %s

                Requirements:
                - generate 4-8 short micro-scenes for this way
                - format must use scenes/world_state/available_actions schema
                - each scene must include leads_to_scene_ids for non-linear graph progression
                - do NOT include outcome/consequence text for actions in this stage
                - do NOT include interpretation labels like "good/bad/optimal/suitable"
                - write only observable, objective facts in world_state
                - each scene should expose unknowns and investigation-oriented actions
                - style should feel like Space Rangers 2 exploration of unknown world
                - use only world entities from world_json
                - align scenes with this exact realisation way
                - no global endings or unrelated ways
                """.formatted(
                normalizedWayId,
                compactJson(constraintsStage.getCurrentRevision().outputJson()),
                compactJson(analysisStage.getCurrentRevision().outputJson()),
                compactJson(scopedWorld),
                compactJson(wayNode),
                compactJson(infoFlowNode),
                compactJson(knowledgeChainNode)
        );

        JsonNode generated = aiClient.generate(SYSTEM_PROMPT, userPrompt);
        validateSceneGraph(generated.path("scenes"));
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

    private QuestStage requiredStageWithRevision(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getCurrentRevision() == null) {
            throw new ConflictException("ACHIEVEMENT_SCENES generation requires generated " + type + " stage");
        }
        return stage;
    }

    private void ensureKnowledgeChainWayApproved(JsonNode knowledgeChainNode, String wayId) {
        if (knowledgeChainNode == null || knowledgeChainNode.isMissingNode() || knowledgeChainNode.isNull() || knowledgeChainNode.isEmpty()) {
            throw new ConflictException("KNOWLEDGE_CHAIN for way is missing: " + wayId);
        }
        if (!knowledgeChainNode.path("approved").asBoolean(false)) {
            throw new ConflictException("KNOWLEDGE_CHAIN way is not approved: " + wayId);
        }
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

    private JsonNode findByWayId(JsonNode arrayNode, String wayId) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return objectMapper.createObjectNode();
        }
        for (JsonNode item : arrayNode) {
            if (wayId.equalsIgnoreCase(item.path("way_id").asText(""))) {
                return item;
            }
        }
        return objectMapper.createObjectNode();
    }

    private JsonNode buildScopedWorld(JsonNode worldJson, JsonNode wayNode) {
        Set<String> ids = new HashSet<>();
        JsonNode usesElements = wayNode.path("uses_world_elements");
        if (usesElements.isArray()) {
            for (JsonNode item : usesElements) {
                String id = item.asText("");
                if (!id.isBlank()) {
                    ids.add(id);
                }
            }
        }

        ObjectNode scoped = objectMapper.createObjectNode();
        ArrayNode locations = objectMapper.createArrayNode();
        ArrayNode organizations = objectMapper.createArrayNode();
        ArrayNode npcs = objectMapper.createArrayNode();

        JsonNode allLocations = worldJson.path("locations");
        if (allLocations.isArray()) {
            for (JsonNode location : allLocations) {
                if (ids.contains(location.path("id").asText(""))) {
                    locations.add(location);
                }
            }
        }

        JsonNode allOrganizations = worldJson.path("organizations");
        if (allOrganizations.isArray()) {
            for (JsonNode organization : allOrganizations) {
                if (ids.contains(organization.path("id").asText(""))) {
                    organizations.add(organization);
                }
            }
        }

        JsonNode allNpcs = worldJson.path("npcs");
        if (allNpcs.isArray()) {
            for (JsonNode npc : allNpcs) {
                if (ids.contains(npc.path("id").asText(""))) {
                    npcs.add(npc);
                }
            }
        }

        scoped.set("locations", locations);
        scoped.set("organizations", organizations);
        scoped.set("npcs", npcs);
        scoped.set("rules", worldJson.path("rules").isArray() ? worldJson.path("rules") : objectMapper.createArrayNode());
        return scoped;
    }

    private String compactJson(JsonNode json) {
        String raw = json == null ? "{}" : json.toString();
        if (raw.length() <= MAX_CONTEXT_CHARS) {
            return raw;
        }
        return raw.substring(0, MAX_CONTEXT_CHARS) + "...";
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
        normalized.set("scenes", normalizeScenes(generated.path("scenes")));
        ways.add(normalized);
        seen.add(wayId.toUpperCase());

        root.set("ways", ways);
        return root;
    }

    private ArrayNode normalizeScenes(JsonNode scenesNode) {
        ArrayNode normalized = objectMapper.createArrayNode();
        if (!scenesNode.isArray()) {
            return normalized;
        }

        Map<String, JsonNode> byId = new HashMap<>();
        List<String> order = new ArrayList<>();
        for (JsonNode scene : scenesNode) {
            String id = scene.path("id").asText("").trim();
            if (id.isBlank()) {
                continue;
            }
            byId.put(id.toUpperCase(), scene);
            order.add(id);
        }

        for (String id : order) {
            JsonNode source = byId.get(id.toUpperCase());
            ObjectNode scene = objectMapper.createObjectNode();
            scene.put("id", id);
            scene.put("title", source.path("title").asText(""));
            scene.set("world_state", source.path("world_state").isObject()
                    ? source.path("world_state")
                    : objectMapper.createObjectNode());
            scene.set("available_actions", source.path("available_actions").isArray()
                    ? source.path("available_actions")
                    : objectMapper.createArrayNode());

            ArrayNode links = objectMapper.createArrayNode();
            JsonNode leadsTo = source.path("leads_to_scene_ids");
            if (leadsTo.isArray()) {
                for (JsonNode link : leadsTo) {
                    String target = link.asText("").trim();
                    if (!target.isBlank() && byId.containsKey(target.toUpperCase())) {
                        links.add(target);
                    }
                }
            }
            scene.set("leads_to_scene_ids", links);
            scene.set("next_scene_hooks", source.path("next_scene_hooks").isArray()
                    ? source.path("next_scene_hooks")
                    : objectMapper.createArrayNode());
            normalized.add(scene);
        }
        return normalized;
    }

    private void validateSceneGraph(JsonNode scenesNode) {
        if (!scenesNode.isArray() || scenesNode.isEmpty()) {
            throw new ConflictException("ACHIEVEMENT_SCENES generation produced empty scenes");
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode scene : scenesNode) {
            String id = scene.path("id").asText("").trim();
            if (id.isBlank()) {
                throw new ConflictException("ACHIEVEMENT_SCENES scene id is required");
            }
            ids.add(id.toUpperCase());
            validateWorldStateFacts(scene.path("world_state"), id);
            validateActions(scene.path("available_actions"), id);
        }
        for (JsonNode scene : scenesNode) {
            JsonNode links = scene.path("leads_to_scene_ids");
            if (!links.isArray()) {
                continue;
            }
            for (JsonNode link : links) {
                String target = link.asText("").trim();
                if (target.isBlank()) {
                    continue;
                }
                if (!ids.contains(target.toUpperCase())) {
                    throw new ConflictException("ACHIEVEMENT_SCENES has invalid graph link to unknown scene: " + target);
                }
            }
        }
    }

    private void validateWorldStateFacts(JsonNode worldState, String sceneId) {
        if (worldState == null || !worldState.isObject()) {
            return;
        }
        validateFactArray(worldState.path("known_facts"), "known_facts", sceneId);
        validateFactArray(worldState.path("unknowns"), "unknowns", sceneId);
        validateFactArray(worldState.path("constraints"), "constraints", sceneId);
    }

    private void validateActions(JsonNode actions, String sceneId) {
        if (actions == null || !actions.isArray()) {
            return;
        }
        for (JsonNode action : actions) {
            String text = action.path("text").asText("");
            if (containsInterpretation(text)) {
                throw new ConflictException("ACHIEVEMENT_SCENES action text must be objective in scene " + sceneId + ": " + text);
            }
        }
    }

    private void validateFactArray(JsonNode arrayNode, String fieldName, String sceneId) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return;
        }
        for (JsonNode item : arrayNode) {
            String text = item.asText("");
            if (containsInterpretation(text)) {
                throw new ConflictException("ACHIEVEMENT_SCENES " + fieldName + " must contain observable facts only in scene " + sceneId + ": " + text);
            }
        }
    }

    private boolean containsInterpretation(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String marker : INTERPRETATION_MARKERS) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode firstWayNode(JsonNode realisationJson) {
        JsonNode realisations = realisationJson.path("achievement_realisations");
        if (!realisations.isArray()) {
            return null;
        }
        for (JsonNode realisation : realisations) {
            String achievementId = realisation.path("achievement_id").asText("");
            JsonNode ways = realisation.path("ways");
            if (!ways.isArray() || ways.isEmpty()) {
                continue;
            }
            JsonNode firstWay = ways.get(0);
            ObjectNode node = firstWay.deepCopy();
            node.put("achievement_id", achievementId);
            return node;
        }
        return null;
    }
}
