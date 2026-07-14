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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FactsStageRunner implements StageRunner {
    private static final int MAX_CONTEXT_CHARS = 6_000;

    private static final String SYSTEM_PROMPT_LIST = """
            Ты генератор списка фактов для стадии FACTS.
            Верни только валидный JSON.
            Все строковые поля должны быть на русском языке.

            Сгенерируй 10-16 фактов расследования.
            Верни схему:
            {
              "facts": [
                {
                  "id": "F1",
                  "description": "",
                  "mandatory": true
                }
              ]
            }
            """;

    private static final String SYSTEM_PROMPT_OWNERS = """
            Ты генератор владельцев фактов для стадии FACTS.
            Верни только валидный JSON.
            Все строковые поля должны быть на русском языке.

            Для каждого входного факта назначь owner (роль/источник знания).
            Верни схему:
            {
              "owners": [
                {
                  "id": "F1",
                  "owner": ""
                }
              ]
            }
            """;

    private static final String SYSTEM_PROMPT_DEPENDENCIES = """
            Ты генератор зависимостей фактов для стадии FACTS.
            Верни только валидный JSON.
            Все строковые поля должны быть на русском языке.

            Для каждого факта определи, какие факты он может разблокировать.
            Не создавай циклы и самоссылки.
            Верни схему:
            {
              "dependencies": [
                {
                  "id": "F1",
                  "unlocks": ["F3", "F5"]
                }
              ]
            }
            """;

    private static final String SYSTEM_PROMPT_VISIBILITY = """
            Ты генератор видимости и риска интерпретации для фактов стадии FACTS.
            Верни только валидный JSON.
            Все строковые поля должны быть на русском языке.

            Для каждого факта задай:
            - visibility: public|hidden|contested
            - interpretation_risk: короткое объяснение риска неверной трактовки

            Верни схему:
            {
              "visibility": [
                {
                  "id": "F1",
                  "visibility": "public",
                  "interpretation_risk": ""
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public StageType type() {
        return StageType.FACTS;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.MYSTERY);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.NPC);

        String baseContext = buildBaseContext(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                npcStage.getCurrentRevision().outputJson()
        );

        JsonNode listOutput = aiClient.generate(SYSTEM_PROMPT_LIST, baseContext);
        JsonNode ownersOutput = aiClient.generate(SYSTEM_PROMPT_OWNERS, baseContext + "\n\nfacts:\n" + compactJson(listOutput.path("facts")));
        JsonNode dependenciesOutput = aiClient.generate(SYSTEM_PROMPT_DEPENDENCIES, baseContext + "\n\nfacts:\n" + compactJson(listOutput.path("facts")));
        JsonNode visibilityOutput = aiClient.generate(SYSTEM_PROMPT_VISIBILITY, baseContext + "\n\nfacts:\n" + compactJson(listOutput.path("facts")));

        return assembleFactsArtifact(listOutput, ownersOutput, dependenciesOutput, visibilityOutput);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("FACTS generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private String buildBaseContext(QuestProject project, JsonNode mysteryJson, JsonNode worldJson, JsonNode npcJson) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Контекст проекта:
                project_name: %s
                quest_style: %s

                approved_mystery_json:
                %s

                approved_world_json:
                %s

                approved_npc_json:
                %s
                """.formatted(
                project.getName(),
                style,
                compactJson(mysteryJson),
                compactJson(worldJson),
                compactJson(npcJson)
        );
    }

    private JsonNode assembleFactsArtifact(
            JsonNode listOutput,
            JsonNode ownersOutput,
            JsonNode dependenciesOutput,
            JsonNode visibilityOutput
    ) {
        Map<String, JsonNode> ownerById = mapById(ownersOutput.path("owners"));
        Map<String, JsonNode> depsById = mapById(dependenciesOutput.path("dependencies"));
        Map<String, JsonNode> visibilityById = mapById(visibilityOutput.path("visibility"));

        ArrayNode factsResult = objectMapper.createArrayNode();
        JsonNode facts = listOutput.path("facts");
        if (facts.isArray()) {
            for (JsonNode fact : facts) {
                String id = fact.path("id").asText("");
                if (id.isBlank()) {
                    continue;
                }
                ObjectNode node = objectMapper.createObjectNode();
                node.put("id", id);
                node.put("description", fact.path("description").asText(""));
                node.put("mandatory", fact.path("mandatory").asBoolean(true));

                JsonNode ownerNode = ownerById.get(id);
                node.put("owner", ownerNode == null ? "" : ownerNode.path("owner").asText(""));

                ArrayNode unlocks = objectMapper.createArrayNode();
                JsonNode depNode = depsById.get(id);
                JsonNode unlocksNode = depNode == null ? null : depNode.path("unlocks");
                if (unlocksNode != null && unlocksNode.isArray()) {
                    for (JsonNode unlock : unlocksNode) {
                        String unlockId = unlock.asText("");
                        if (!unlockId.isBlank() && !unlockId.equals(id)) {
                            unlocks.add(unlockId);
                        }
                    }
                }
                node.set("unlocks", unlocks);

                JsonNode visNode = visibilityById.get(id);
                node.put("visibility", visNode == null ? "contested" : visNode.path("visibility").asText("contested"));
                node.put("interpretation_risk", visNode == null ? "" : visNode.path("interpretation_risk").asText(""));

                factsResult.add(node);
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.set("facts", factsResult);
        ObjectNode stepOutputs = objectMapper.createObjectNode();
        stepOutputs.set("fact_list", listOutput);
        stepOutputs.set("fact_owners", ownersOutput);
        stepOutputs.set("fact_dependencies", dependenciesOutput);
        stepOutputs.set("fact_visibility", visibilityOutput);
        result.set("step_outputs", stepOutputs);
        return result;
    }

    private Map<String, JsonNode> mapById(JsonNode arrayNode) {
        Map<String, JsonNode> map = new HashMap<>();
        if (!arrayNode.isArray()) {
            return map;
        }
        for (JsonNode node : arrayNode) {
            String id = node.path("id").asText("");
            if (!id.isBlank()) {
                map.put(id, node);
            }
        }
        return map;
    }

    private String compactJson(JsonNode json) {
        String raw = json == null ? "{}" : json.toString();
        if (raw.length() <= MAX_CONTEXT_CHARS) {
            return raw;
        }
        return raw.substring(0, MAX_CONTEXT_CHARS) + "...";
    }
}

