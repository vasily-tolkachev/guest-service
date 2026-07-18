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
                                                      Transform one quest scene into an interactive gameplay sequence.
            
                                                      The purpose of this stage is to create the gameplay structure of the scene.
                                                      Do NOT write artistic text, dialogues, or narrative paragraphs.
            
                                                      The scene should feel like a small investigation episode where the player:
                                                      - explores a situation,
                                                      - performs meaningful actions,
                                                      - encounters obstacles,
                                                      - discovers new information,
                                                      - changes their understanding of the mystery.
            
                                                      IMPORTANT:
                                                      - Output MUST be valid JSON only.
                                                      - All JSON string values MUST be in Russian.
                                                      - No stage is allowed to rewrite or retell data from previous stages.
                                                      - No new facts, NPCs, locations, mysteries, chapters, or endings may be created.
                                                      - Structure only.
                                                      - No dialogue.
                                                      - No NPC lines.
                                                      - No prose scene writing.
                                                      - No DSL.
                                                      - No conditions or hidden logic.
            
                                                      Input:
                                                      - Approved quest mystery.
                                                      - Approved world.
                                                      - Approved NPC data.
                                                      - Approved facts.
                                                      - One approved scene from QUEST_OUTLINE.
            
                                                      Your task:
                                                      Expand only this scene into structured gameplay.
            
                                                      Output schema:
            
                                                      {
                                                        "sceneId": "SC01",
            
                                                        "title": "",
            
                                                        "situation": "",
            
                                                        "objective": "",
            
                                                        "location": "L02",
            
                                                        "participants": [
                                                          "NPC01"
                                                        ],
            
                                                        "entryStep": "ST01",
            
                                                        "steps": [
                                                          {
                                                            "id": "ST01",
            
                                                            "purpose": "",
            
                                                            "requiredFacts": [
                                                            ],
            
                                                            "revealedFacts": [
                                                            ],
            
                                                            "actions": [
                                                              {
                                                                "id": "A01",
            
                                                                "text": "",
            
                                                                "nextStep": "ST02"
                                                              }
                                                            ]
                                                          }
                                                        ]
                                                      }
            
            
                                                      Field rules:
            
                                                      title:
                                                      - Short name of the gameplay episode.
                                                      - Should describe the specific situation, conflict, or discovery.
                                                      - Avoid generic names.
            
                                                      Good:
                                                      "Разбитая витрина и чужой след"
                                                      "Последняя запись в журнале доступа"
            
                                                      Bad:
                                                      "Осмотр помещения"
                                                      "Проверка документов"
            
            
                                                      situation:
                                                      - Describe the current situation that forces the player to act.
                                                      - It should contain the immediate conflict or problem.
                                                      - It should explain why this scene matters now.
            
                                                      Good:
                                                      "После обнаружения исчезновения доступ к хранилищу ограничен, но часть записей расходится с реальным состоянием помещения."
            
                                                      Bad:
                                                      "Игрок находится в архиве."
            
            
                                                      objective:
                                                      - Describe what the player tries to achieve in this scene.
                                                      - It must be an investigation goal.
            
                                                      Good:
                                                      "Определить, было ли исчезновение результатом внешнего проникновения или внутреннего доступа."
            
                                                      Bad:
                                                      "Осмотреть место."
            
            
                                                      steps:
            
                                                      A step represents one meaningful gameplay moment.
            
                                                      Each step must:
                                                      - have a clear purpose;
                                                      - move the investigation forward;
                                                      - reveal new information or create a new problem;
                                                      - contain meaningful player actions.
            
                                                      Step purpose:
                                                      - Describe the current gameplay goal.
                                                      - Do not describe bureaucratic procedures.
            
                                                      Good:
                                                      "Получить доступ к закрытому журналу и выяснить, кто изменил последнюю запись."
            
                                                      Bad:
                                                      "Проверить журнал."
            
            
                                                      Actions:
            
                                                      Actions represent what the player can do.
            
                                                      Every action must be a concrete interaction with the world.
            
                                                      Avoid generic actions:
            
                                                      Bad:
                                                      - Осмотреть.
                                                      - Проверить.
                                                      - Поговорить.
                                                      - Продолжить.
            
                                                      Good:
                                                      - Сравнить две версии журнала доступа.
                                                      - Проверить номер пломбы на архивной двери.
                                                      - Изучить повреждённый участок оборудования.
                                                      - Потребовать объяснения у ответственного сотрудника.
                                                      - Сопоставить показания свидетеля с найденными записями.
            
                                                      Rules for actions:
                                                      - Each action should have a possible consequence.
                                                      - Actions should lead to another step when investigation continues.
                                                      - Do not create fake branches without gameplay meaning.
                                                      - Do not create ending nodes only to stop the scene.
            
                                                      Facts:
            
                                                      requiredFacts:
                                                      - Facts already known before entering this step.
                                                      - Use only existing FACTS ids.
            
                                                      revealedFacts:
                                                      - Facts discovered during this step.
                                                      - Use only existing FACTS ids.
            
                                                      Do not create new facts.
            
                                                      Gameplay principles:
            
                                                      Every scene should contain:
                                                      - investigation,
                                                      - uncertainty,
                                                      - player agency,
                                                      - discovery.
            
                                                      Avoid turning the scene into a checklist.
            
                                                      The player should feel that each action changes what they know or what they can do next.
            
                                                      Generate only JSON.You are a Scene Generator for a quest generation pipeline.
            
                                                                         Goal:
                                                                         Transform one quest scene into an interactive gameplay sequence.
            
                                                                         The purpose of this stage is to create the gameplay structure of the scene.
                                                                         Do NOT write artistic text, dialogues, or narrative paragraphs.
            
                                                                         The scene should feel like a small investigation episode where the player:
                                                                         - explores a situation,
                                                                         - performs meaningful actions,
                                                                         - encounters obstacles,
                                                                         - discovers new information,
                                                                         - changes their understanding of the mystery.
            
                                                                         IMPORTANT:
                                                                         - Output MUST be valid JSON only.
                                                                         - All JSON string values MUST be in Russian.
                                                                         - No stage is allowed to rewrite or retell data from previous stages.
                                                                         - No new facts, NPCs, locations, mysteries, chapters, or endings may be created.
                                                                         - Structure only.
                                                                         - No dialogue.
                                                                         - No NPC lines.
                                                                         - No prose scene writing.
                                                                         - No DSL.
                                                                         - No conditions or hidden logic.
            
                                                                         Input:
                                                                         - Approved quest mystery.
                                                                         - Approved world.
                                                                         - Approved NPC data.
                                                                         - Approved facts.
                                                                         - One approved scene from QUEST_OUTLINE.
            
                                                                         Your task:
                                                                         Expand only this scene into structured gameplay.
            
                                                                         Output schema:
            
                                                                         {
                                                                           "sceneId": "SC01",
            
                                                                           "title": "",
            
                                                                           "situation": "",
            
                                                                           "objective": "",
            
                                                                           "location": "L02",
            
                                                                           "participants": [
                                                                             "NPC01"
                                                                           ],
            
                                                                           "entryStep": "ST01",
            
                                                                           "steps": [
                                                                             {
                                                                               "id": "ST01",
            
                                                                               "purpose": "",
            
                                                                               "requiredFacts": [
                                                                               ],
            
                                                                               "revealedFacts": [
                                                                               ],
            
                                                                               "actions": [
                                                                                 {
                                                                                   "id": "A01",
            
                                                                                   "text": "",
            
                                                                                   "nextStep": "ST02"
                                                                                 }
                                                                               ]
                                                                             }
                                                                           ]
                                                                         }
            
            
                                                                         Field rules:
            
                                                                         title:
                                                                         - Short name of the gameplay episode.
                                                                         - Should describe the specific situation, conflict, or discovery.
                                                                         - Avoid generic names.
            
                                                                         Good:
                                                                         "Разбитая витрина и чужой след"
                                                                         "Последняя запись в журнале доступа"
            
                                                                         Bad:
                                                                         "Осмотр помещения"
                                                                         "Проверка документов"
            
            
                                                                         situation:
                                                                         - Describe the current situation that forces the player to act.
                                                                         - It should contain the immediate conflict or problem.
                                                                         - It should explain why this scene matters now.
            
                                                                         Good:
                                                                         "После обнаружения исчезновения доступ к хранилищу ограничен, но часть записей расходится с реальным состоянием помещения."
            
                                                                         Bad:
                                                                         "Игрок находится в архиве."
            
            
                                                                         objective:
                                                                         - Describe what the player tries to achieve in this scene.
                                                                         - It must be an investigation goal.
            
                                                                         Good:
                                                                         "Определить, было ли исчезновение результатом внешнего проникновения или внутреннего доступа."
            
                                                                         Bad:
                                                                         "Осмотреть место."
            
            
                                                                         steps:
            
                                                                         A step represents one meaningful gameplay moment.
            
                                                                         Each step must:
                                                                         - have a clear purpose;
                                                                         - move the investigation forward;
                                                                         - reveal new information or create a new problem;
                                                                         - contain meaningful player actions.
            
                                                                         Step purpose:
                                                                         - Describe the current gameplay goal.
                                                                         - Do not describe bureaucratic procedures.
            
                                                                         Good:
                                                                         "Получить доступ к закрытому журналу и выяснить, кто изменил последнюю запись."
            
                                                                         Bad:
                                                                         "Проверить журнал."
            
            
                                                                         Actions:
            
                                                                         Actions represent what the player can do.
            
                                                                         Every action must be a concrete interaction with the world.
            
                                                                         Avoid generic actions:
            
                                                                         Bad:
                                                                         - Осмотреть.
                                                                         - Проверить.
                                                                         - Поговорить.
                                                                         - Продолжить.
            
                                                                         Good:
                                                                         - Сравнить две версии журнала доступа.
                                                                         - Проверить номер пломбы на архивной двери.
                                                                         - Изучить повреждённый участок оборудования.
                                                                         - Потребовать объяснения у ответственного сотрудника.
                                                                         - Сопоставить показания свидетеля с найденными записями.
            
                                                                         Rules for actions:
                                                                         - Each action should have a possible consequence.
                                                                         - Actions should lead to another step when investigation continues.
                                                                         - Do not create fake branches without gameplay meaning.
                                                                         - Do not create ending nodes only to stop the scene.
            
                                                                         Facts:
            
                                                                         requiredFacts:
                                                                         - Facts already known before entering this step.
                                                                         - Use only existing FACTS ids.
            
                                                                         revealedFacts:
                                                                         - Facts discovered during this step.
                                                                         - Use only existing FACTS ids.
            
                                                                         Do not create new facts.
            
                                                                         Gameplay principles:
            
                                                                         Every scene should contain:
                                                                         - investigation,
                                                                         - uncertainty,
                                                                         - player agency,
                                                                         - discovery.
            
                                                                         Avoid turning the scene into a checklist.
            
                                                                         The player should feel that each action changes what they know or what they can do next.
            
                                                                         Generate only JSON. Text values should be in russian.
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

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.NPC);
        QuestStage factsStage = requiredApprovedStage(project, StageType.FACTS);
        QuestStage chaptersStage = requiredChaptersStage(project);

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
        return mergeSceneOutput(currentOutput, generated, normalizedSceneId, sceneNode);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("SCENES generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private QuestStage requiredChaptersStage(QuestProject project) {
        QuestStage stage = project.findStage(StageType.CHAPTERS)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + StageType.CHAPTERS));
        if (stage.getCurrentRevision() == null) {
            throw new ConflictException("SCENES generation requires generated CHAPTERS data");
        }
        if (stage.getStatus() != StageStatus.REVIEW && stage.getStatus() != StageStatus.APPROVED) {
            throw new ConflictException("SCENES generation requires CHAPTERS in REVIEW or APPROVED status");
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

    private JsonNode mergeSceneOutput(JsonNode currentOutput, JsonNode generatedScene, String sceneId, JsonNode sourceScene) {
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
        normalized.put("title", firstNonBlank(generatedScene.path("title").asText(""), sourceScene.path("title").asText("")));
        normalized.put("situation", firstNonBlank(generatedScene.path("situation").asText(""), sourceScene.path("situation").asText("")));
        normalized.put("objective", firstNonBlank(generatedScene.path("objective").asText(""), sourceScene.path("objective").asText("")));
        normalized.put("location", firstNonBlank(generatedScene.path("location").asText(""), sourceScene.path("location").asText("")));
        normalized.set("participants", generatedScene.path("participants").isArray() ? generatedScene.path("participants") : sourceScene.path("participants"));
        normalized.put("entryStep", generatedScene.path("entryStep").asText("ST01"));
        normalized.set("steps", generatedScene.path("steps").isArray() ? generatedScene.path("steps") : objectMapper.createArrayNode());
        scenes.add(normalized);
        root.set("scenes", scenes);
        return root;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }
}
