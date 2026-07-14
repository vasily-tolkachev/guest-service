package com.myproject.questservice.application.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.application.service.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class FlowDslExportService {

    public String toDsl(String projectName, JsonNode graphJson) {
        List<NodeDef> nodes = extractNodes(graphJson);
        validateGraph(nodes, graphJson.path("ending_nodes"));

        String questId = toQuestId(projectName);
        String title = escape(safeText(projectName, "Generated Quest"));
        StringBuilder dsl = new StringBuilder();
        dsl.append("quest ").append(questId).append('\n');
        dsl.append("title \"").append(title).append("\"\n\n");

        for (NodeDef node : nodes) {
            dsl.append("node ").append(node.id()).append('\n');
            dsl.append("title \"").append(escape(node.title())).append("\"\n");
            dsl.append("\"").append(escape(buildNodeText(node))).append("\"\n");
            for (ChoiceDef choice : node.choices()) {
                dsl.append("> \"").append(escape(choice.text())).append("\" -> ").append(choice.next()).append('\n');
            }
            dsl.append('\n');
        }

        return dsl.toString();
    }

    private List<NodeDef> extractNodes(JsonNode graphJson) {
        JsonNode nodesNode = graphJson.path("nodes");
        if (!nodesNode.isArray() || nodesNode.isEmpty()) {
            nodesNode = graphJson.path("step_outputs").path("node_list").path("nodes");
        }
        if (!nodesNode.isArray() || nodesNode.isEmpty()) {
            throw new BadRequestException("quest structure must contain non-empty nodes[]");
        }

        JsonNode edgesByNode = graphJson.path("step_outputs").path("edges").path("edges");
        JsonNode detailsByNode = graphJson.path("step_outputs").path("node_details").path("node_details");

        List<NodeDef> nodes = new ArrayList<>();
        for (JsonNode nodeNode : nodesNode) {
            String id = safeText(nodeNode.path("id").asText(null), null);
            if (id == null || id.isBlank()) {
                continue;
            }

            JsonNode detailsNode = nodeNode;
            if ((!nodeNode.has("required_facts") || !nodeNode.has("revealed_facts")) && detailsByNode.isArray()) {
                JsonNode found = findById(detailsByNode, id);
                if (found != null) {
                    detailsNode = found;
                }
            }

            String title = safeText(nodeNode.path("title").asText(null), null);
            if (title == null || title.isBlank()) {
                title = safeText(nodeNode.path("purpose").asText(null), "Узел");
            }

            ArrayList<String> requiredFacts = readStringArray(detailsNode.path("required_facts"));
            ArrayList<String> revealedFacts = readStringArray(detailsNode.path("revealed_facts"));
            ArrayList<String> participants = readStringArray(detailsNode.path("participants"));

            JsonNode choicesNode = nodeNode.path("choices");
            if ((!choicesNode.isArray() || choicesNode.isEmpty()) && edgesByNode.isArray()) {
                JsonNode edgeNode = findById(edgesByNode, id);
                if (edgeNode != null) {
                    choicesNode = edgeNode.path("choices");
                }
            }
            ArrayList<ChoiceDef> choices = readChoices(choicesNode);

            nodes.add(new NodeDef(
                    id,
                    title,
                    safeText(nodeNode.path("purpose").asText(null), ""),
                    requiredFacts,
                    revealedFacts,
                    participants,
                    choices
            ));
        }
        return nodes;
    }

    private ArrayList<ChoiceDef> readChoices(JsonNode choicesNode) {
        ArrayList<ChoiceDef> choices = new ArrayList<>();
        if (!choicesNode.isArray()) {
            return choices;
        }
        for (JsonNode choiceNode : choicesNode) {
            String text = safeText(choiceNode.path("text").asText(null), null);
            String next = safeText(choiceNode.path("next").asText(null), null);
            if (text != null && !text.isBlank() && next != null && !next.isBlank()) {
                choices.add(new ChoiceDef(text, next));
            }
        }
        return choices;
    }

    private ArrayList<String> readStringArray(JsonNode arrayNode) {
        ArrayList<String> result = new ArrayList<>();
        if (!arrayNode.isArray()) {
            return result;
        }
        for (JsonNode value : arrayNode) {
            String text = safeText(value.asText(null), null);
            if (text != null && !text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private void validateGraph(List<NodeDef> nodes, JsonNode endingNodes) {
        if (nodes.isEmpty()) {
            throw new BadRequestException("quest structure must contain at least one valid node");
        }

        Set<String> nodeIds = new HashSet<>();
        for (NodeDef node : nodes) {
            if (!nodeIds.add(node.id())) {
                throw new BadRequestException("duplicate node id: " + node.id());
            }
        }

        for (NodeDef node : nodes) {
            for (ChoiceDef choice : node.choices()) {
                if (!nodeIds.contains(choice.next())) {
                    throw new BadRequestException("choice target does not exist: " + choice.next());
                }
            }
        }

        if (endingNodes != null && endingNodes.isArray()) {
            for (JsonNode ending : endingNodes) {
                String id = safeText(ending.asText(null), null);
                if (id != null && !id.isBlank() && !nodeIds.contains(id)) {
                    throw new BadRequestException("ending_nodes contains unknown node id: " + id);
                }
            }
        }
    }

    private String buildNodeText(NodeDef node) {
        if (node.purpose() == null || node.purpose().isBlank()) {
            return node.title();
        }
        return node.purpose();
    }

    private JsonNode findById(JsonNode arrayNode, String id) {
        for (JsonNode node : arrayNode) {
            if (id.equals(node.path("id").asText())) {
                return node;
            }
        }
        return null;
    }

    private String toQuestId(String projectName) {
        String base = projectName == null ? "generated_quest" : projectName
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            return "generated_quest";
        }
        if (!Character.isLetter(base.charAt(0)) && base.charAt(0) != '_') {
            base = "q_" + base;
        }
        return base;
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record NodeDef(
            String id,
            String title,
            String purpose,
            List<String> requiredFacts,
            List<String> revealedFacts,
            List<String> participants,
            List<ChoiceDef> choices
    ) {
    }

    private record ChoiceDef(String text, String next) {
    }
}
