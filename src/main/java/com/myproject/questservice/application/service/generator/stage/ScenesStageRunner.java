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
public class ScenesStageRunner implements SceneStageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Scene Generator for a quest generation pipeline.

            Goal:
            Expand one scene into a structured gameplay episode.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - No stage is allowed to rewrite or retell data from previous stages.
            - No artistic text, no scene descriptions, no dialogues, no NPC lines.
            - Structure only.

            Output schema:
            {
              "sceneId": "SC01",
              "entryStep": "ST01",
              "steps": [
                {
                  "id": "ST01",
                  "purpose": "",
                  "requiredFacts": [],
                  "revealedFacts": [],
                  "actions": [
                    {
                      "id": "A01",
                      "description": "",
                      "nextStep": "ST02"
                    }
                  ]
                }
              ]
            }
            
            Rules:
            - AI may split scene into steps, actions, transitions, and fact reveals.
            - AI must not create new facts, NPCs, locations, mystery/chapter/scene changes.
            - Action contains only id, description, nextStep.
            - No conditions, no hidden logic, no DSL.
            
            Output json fileds in russian
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public StageType type() {
        return StageType.SCENES;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        throw new ConflictException("SCENES stage supports scene-by-scene generation only");
    }

    @Override
    public JsonNode generateScene(UUID projectId, String sceneId, JsonNode currentOutput) {
        if (sceneId == null || sceneId.isBlank()) {
            throw new ConflictException("sceneId is required");
        }
        String normalizedSceneId = sceneId.trim();

        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.MYSTERY);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.NPC);
        QuestStage factsStage = requiredApprovedStage(project, StageType.FACTS);
        QuestStage chaptersStage = requiredApprovedStage(project, StageType.CHAPTERS);

        JsonNode sceneNode = findScene(chaptersStage.getCurrentRevision().outputJson(), normalizedSceneId);
        if (sceneNode == null) {
            throw new NotFoundException("Scene not found in CHAPTERS output: " + normalizedSceneId);
        }

        JsonNode scopedWorld = buildScopedWorld(worldStage.getCurrentRevision().outputJson(), sceneNode, npcStage.getCurrentRevision().outputJson());
        JsonNode scopedNpc = buildScopedNpc(npcStage.getCurrentRevision().outputJson(), sceneNode);
        JsonNode scopedFacts = buildScopedFacts(factsStage.getCurrentRevision().outputJson(), sceneNode);

        String userPrompt = """
                Generate structured gameplay for one scene only.

                approved_mystery_json:
                %s

                scoped_world_json:
                %s

                scoped_npc_json:
                %s

                scoped_facts_json:
                %s

                scene_json:
                %s
                """.formatted(
                mysteryStage.getCurrentRevision().outputJson(),
                scopedWorld,
                scopedNpc,
                scopedFacts,
                sceneNode
        );

        JsonNode generated = aiClient.generate(SYSTEM_PROMPT, userPrompt);
        return mergeSceneOutput(currentOutput, generated, normalizedSceneId);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("SCENES generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private JsonNode findScene(JsonNode chaptersOutput, String sceneId) {
        JsonNode chapters = chaptersOutput.path("chapters");
        if (!chapters.isArray()) {
            return null;
        }
        for (JsonNode chapter : chapters) {
            JsonNode scenes = chapter.path("scenes");
            if (!scenes.isArray()) {
                continue;
            }
            for (JsonNode scene : scenes) {
                if (sceneId.equalsIgnoreCase(scene.path("id").asText(""))) {
                    return scene;
                }
            }
        }
        return null;
    }

    private JsonNode buildScopedWorld(JsonNode worldJson, JsonNode sceneNode, JsonNode npcJson) {
        Set<String> locationIds = toIdSet(sceneNode.path("location"));
        Set<String> orgIds = new HashSet<>();
        Set<String> participantIds = toIdSet(sceneNode.path("participants"));
        JsonNode npcs = npcJson.path("npcs");
        if (npcs.isArray()) {
            for (JsonNode npc : npcs) {
                String npcId = npc.path("id").asText("");
                if (participantIds.contains(npcId)) {
                    String org = npc.path("organization").asText("");
                    if (!org.isBlank()) {
                        orgIds.add(org);
                    }
                }
            }
        }

        ObjectNode scoped = objectMapper.createObjectNode();
        ArrayNode locations = objectMapper.createArrayNode();
        JsonNode worldLocations = worldJson.path("locations");
        if (worldLocations.isArray()) {
            for (JsonNode location : worldLocations) {
                if (locationIds.contains(location.path("id").asText(""))) {
                    locations.add(location);
                }
            }
        }
        ArrayNode organizations = objectMapper.createArrayNode();
        JsonNode worldOrganizations = worldJson.path("organizations");
        if (worldOrganizations.isArray()) {
            for (JsonNode organization : worldOrganizations) {
                if (orgIds.contains(organization.path("id").asText(""))) {
                    organizations.add(organization);
                }
            }
        }
        scoped.set("locations", locations);
        scoped.set("organizations", organizations);
        scoped.set("rules", worldJson.path("rules").isArray() ? worldJson.path("rules") : objectMapper.createArrayNode());
        return scoped;
    }

    private JsonNode buildScopedNpc(JsonNode npcJson, JsonNode sceneNode) {
        Set<String> participantIds = toIdSet(sceneNode.path("participants"));
        ObjectNode scoped = objectMapper.createObjectNode();
        ArrayNode npcs = objectMapper.createArrayNode();
        JsonNode allNpcs = npcJson.path("npcs");
        if (allNpcs.isArray()) {
            for (JsonNode npc : allNpcs) {
                if (participantIds.contains(npc.path("id").asText(""))) {
                    npcs.add(npc);
                }
            }
        }
        scoped.set("npcs", npcs);
        return scoped;
    }

    private JsonNode buildScopedFacts(JsonNode factsJson, JsonNode sceneNode) {
        Set<String> factIds = toIdSet(sceneNode.path("requiredFacts"));
        factIds.addAll(toIdSet(sceneNode.path("revealedFacts")));
        ObjectNode scoped = objectMapper.createObjectNode();
        ArrayNode facts = objectMapper.createArrayNode();
        JsonNode allFacts = factsJson.path("facts");
        if (allFacts.isArray()) {
            for (JsonNode fact : allFacts) {
                if (factIds.contains(fact.path("id").asText(""))) {
                    facts.add(fact);
                }
            }
        }
        scoped.set("facts", facts);
        return scoped;
    }

    private Set<String> toIdSet(JsonNode node) {
        Set<String> ids = new HashSet<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return ids;
        }
        if (node.isTextual()) {
            String value = node.asText("");
            if (!value.isBlank()) {
                ids.add(value);
            }
            return ids;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("");
                if (!value.isBlank()) {
                    ids.add(value);
                }
            }
        }
        return ids;
    }

    private JsonNode mergeSceneOutput(JsonNode currentOutput, JsonNode generatedScene, String sceneId) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode scenes = objectMapper.createArrayNode();
        if (currentOutput != null && currentOutput.path("scenes").isArray()) {
            for (JsonNode existing : currentOutput.path("scenes")) {
                String id = existing.path("sceneId").asText("");
                if (!id.equalsIgnoreCase(sceneId) && !id.isBlank()) {
                    scenes.add(existing);
                }
            }
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("sceneId", sceneId);
        normalized.put("entryStep", generatedScene.path("entryStep").asText(""));
        normalized.set("steps", generatedScene.path("steps").isArray() ? generatedScene.path("steps") : objectMapper.createArrayNode());
        scenes.add(normalized);
        root.set("scenes", scenes);
        return root;
    }
}
