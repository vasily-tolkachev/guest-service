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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FlowStageRunner implements StepStageRunner {
    private static final int MAX_CONTEXT_CHARS = 7_000;

    private static final String SYSTEM_PROMPT_NODE_LIST = """
            Ты генератор структуры узлов для стадии QUEST_GRAPH (Quest Structure).
            Верни только валидный JSON. Все строковые поля должны быть на русском языке.

            Верни схему:
            {
              "nodes": [
                {
                  "id": "N1",
                  "title": "",
                  "purpose": ""
                }
              ]
            }
            Сгенерируй 8-16 узлов, с уникальными id N1..N16.
            """;

    private static final String SYSTEM_PROMPT_NODE_DETAILS = """
            Ты генератор деталей узлов для стадии QUEST_GRAPH (Quest Structure).
            Верни только валидный JSON. Все строковые поля должны быть на русском языке.

            Для каждого узла добавь:
            - required_facts
            - revealed_facts
            - participants

            Верни схему:
            {
              "node_details": [
                {
                  "id": "N1",
                  "required_facts": ["F1"],
                  "revealed_facts": ["F2"],
                  "participants": ["NPC01"]
                }
              ]
            }
            """;

    private static final String SYSTEM_PROMPT_EDGES = """
            Ты генератор переходов графа для стадии QUEST_GRAPH (Quest Structure).
            Верни только валидный JSON. Все строковые поля должны быть на русском языке.

            Для каждого узла создай 1-3 выбора:
            - text
            - next

            Верни схему:
            {
              "edges": [
                {
                  "id": "N1",
                  "choices": [
                    { "text": "", "next": "N2" }
                  ]
                }
              ]
            }
            Граф должен быть связным и вести к финальным узлам.
            """;

    private static final String SYSTEM_PROMPT_ENDINGS = """
            Ты генератор финальных узлов для стадии QUEST_GRAPH (Quest Structure).
            Верни только валидный JSON.

            Верни схему:
            {
              "ending_nodes": ["N12"]
            }
            Выбери 1-4 id финальных узлов из списка уже существующих nodes.
            """;

    private static final List<String> STEPS = List.of("node_list", "node_details", "edges", "endings");

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public StageType type() {
        return StageType.QUEST_GRAPH;
    }

    @Override
    public List<String> steps() {
        return STEPS;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        return generateStep(projectId, "node_list", null);
    }

    @Override
    public JsonNode generateStep(UUID projectId, String step, JsonNode currentOutput) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.MYSTERY);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.NPC);
        QuestStage factsStage = requiredApprovedStage(project, StageType.FACTS);

        String baseContext = buildBaseContext(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                npcStage.getCurrentRevision().outputJson(),
                factsStage.getCurrentRevision().outputJson()
        );

        ObjectNode stepOutputs = currentStepOutputs(currentOutput);
        JsonNode nodeListOutput = stepOutputs.path("node_list");
        JsonNode detailsOutput = stepOutputs.path("node_details");
        JsonNode edgesOutput = stepOutputs.path("edges");
        JsonNode endingsOutput = stepOutputs.path("endings");

        if ("node_list".equals(step)) {
            nodeListOutput = aiClient.generate(SYSTEM_PROMPT_NODE_LIST, baseContext);
            stepOutputs.set("node_list", nodeListOutput);
        } else if ("node_details".equals(step)) {
            ensureNodesPresent(nodeListOutput);
            detailsOutput = aiClient.generate(
                    SYSTEM_PROMPT_NODE_DETAILS,
                    baseContext + "\n\nnodes:\n" + compactJson(nodeListOutput.path("nodes"))
                            + "\n\nfacts:\n" + compactJson(factsStage.getCurrentRevision().outputJson().path("facts"))
            );
            stepOutputs.set("node_details", detailsOutput);
        } else if ("edges".equals(step)) {
            ensureNodesPresent(nodeListOutput);
            edgesOutput = aiClient.generate(
                    SYSTEM_PROMPT_EDGES,
                    baseContext + "\n\nnodes:\n" + compactJson(nodeListOutput.path("nodes"))
            );
            stepOutputs.set("edges", edgesOutput);
        } else if ("endings".equals(step)) {
            ensureNodesPresent(nodeListOutput);
            endingsOutput = aiClient.generate(
                    SYSTEM_PROMPT_ENDINGS,
                    baseContext + "\n\nnodes:\n" + compactJson(nodeListOutput.path("nodes"))
            );
            stepOutputs.set("endings", endingsOutput);
        } else {
            throw new ConflictException("Unsupported QUEST_GRAPH step: " + step);
        }

        return assembleGraph(nodeListOutput, detailsOutput, edgesOutput, endingsOutput);
    }

    @Override
    public boolean isStepCompleted(String step, JsonNode currentOutput) {
        ObjectNode stepOutputs = currentStepOutputs(currentOutput);
        return stepOutputs.has(step);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("QUEST_GRAPH generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private String buildBaseContext(
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
                Контекст проекта:
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
                """.formatted(
                project.getName(),
                style,
                compactJson(mysteryJson),
                compactJson(worldJson),
                compactJson(npcJson),
                compactJson(factsJson)
        );
    }

    private JsonNode assembleGraph(
            JsonNode nodeListOutput,
            JsonNode detailsOutput,
            JsonNode edgesOutput,
            JsonNode endingsOutput
    ) {
        Map<String, JsonNode> detailsById = mapById(detailsOutput.path("node_details"));
        Map<String, JsonNode> edgesById = mapById(edgesOutput.path("edges"));

        ArrayNode nodesResult = objectMapper.createArrayNode();
        JsonNode nodes = nodeListOutput.path("nodes");
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                String id = node.path("id").asText("");
                if (id.isBlank()) {
                    continue;
                }

                ObjectNode merged = objectMapper.createObjectNode();
                merged.put("id", id);
                merged.put("title", node.path("title").asText(""));
                merged.put("purpose", node.path("purpose").asText(""));

                JsonNode details = detailsById.get(id);
                merged.set("required_facts", copyArray(details == null ? null : details.path("required_facts")));
                merged.set("revealed_facts", copyArray(details == null ? null : details.path("revealed_facts")));
                merged.set("participants", copyArray(details == null ? null : details.path("participants")));

                JsonNode edge = edgesById.get(id);
                merged.set("choices", copyArray(edge == null ? null : edge.path("choices")));
                nodesResult.add(merged);
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.set("nodes", nodesResult);
        result.set("ending_nodes", copyArray(endingsOutput.path("ending_nodes")));
        return result;
    }

    private ObjectNode currentStepOutputs(JsonNode currentOutput) {
        if (currentOutput != null && currentOutput.path("nodes").isArray()) {
            return deriveStepOutputsFromGraph(currentOutput);
        }
        if (currentOutput != null && currentOutput.path("step_outputs").isObject()) {
            return (ObjectNode) currentOutput.path("step_outputs").deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private ObjectNode deriveStepOutputsFromGraph(JsonNode currentOutput) {
        ObjectNode stepOutputs = objectMapper.createObjectNode();
        JsonNode nodes = currentOutput.path("nodes");

        ArrayNode nodeList = objectMapper.createArrayNode();
        ArrayNode nodeDetails = objectMapper.createArrayNode();
        ArrayNode edges = objectMapper.createArrayNode();

        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                String id = node.path("id").asText("");
                if (id.isBlank()) {
                    continue;
                }

                ObjectNode listNode = objectMapper.createObjectNode();
                listNode.put("id", id);
                listNode.put("title", node.path("title").asText(""));
                listNode.put("purpose", node.path("purpose").asText(""));
                nodeList.add(listNode);

                ObjectNode detailNode = objectMapper.createObjectNode();
                detailNode.put("id", id);
                detailNode.set("required_facts", copyArray(node.path("required_facts")));
                detailNode.set("revealed_facts", copyArray(node.path("revealed_facts")));
                detailNode.set("participants", copyArray(node.path("participants")));
                nodeDetails.add(detailNode);

                ObjectNode edgeNode = objectMapper.createObjectNode();
                edgeNode.put("id", id);
                edgeNode.set("choices", copyArray(node.path("choices")));
                edges.add(edgeNode);
            }
        }

        ObjectNode nodeListNode = objectMapper.createObjectNode();
        nodeListNode.set("nodes", nodeList);
        stepOutputs.set("node_list", nodeListNode);

        ObjectNode detailsNode = objectMapper.createObjectNode();
        detailsNode.set("node_details", nodeDetails);
        stepOutputs.set("node_details", detailsNode);

        ObjectNode edgesNode = objectMapper.createObjectNode();
        edgesNode.set("edges", edges);
        stepOutputs.set("edges", edgesNode);

        ObjectNode endingsNode = objectMapper.createObjectNode();
        endingsNode.set("ending_nodes", copyArray(currentOutput.path("ending_nodes")));
        stepOutputs.set("endings", endingsNode);
        return stepOutputs;
    }

    private void ensureNodesPresent(JsonNode nodeListOutput) {
        if (!nodeListOutput.path("nodes").isArray() || nodeListOutput.path("nodes").isEmpty()) {
            throw new ConflictException("QUEST_GRAPH step requires generated node_list first");
        }
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

    private ArrayNode copyArray(JsonNode node) {
        ArrayNode copy = objectMapper.createArrayNode();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                copy.add(item);
            }
        }
        return copy;
    }

    private String compactJson(JsonNode json) {
        String raw = json == null ? "{}" : json.toString();
        if (raw.length() <= MAX_CONTEXT_CHARS) {
            return raw;
        }
        return raw.substring(0, MAX_CONTEXT_CHARS) + "...";
    }
}
