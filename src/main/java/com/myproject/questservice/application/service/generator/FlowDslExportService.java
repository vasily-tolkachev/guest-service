package com.myproject.questservice.application.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FlowDslExportService {

    public String toDsl(String projectName, JsonNode graphJson) {
        String questId = toQuestId(projectName);
        String title = escape(safeText(projectName, "Generated Quest"));

        List<NodeDef> nodes = extractNodes(graphJson);
        if (nodes.isEmpty()) {
            nodes = defaultNodes();
        }

        StringBuilder dsl = new StringBuilder();
        dsl.append("quest ").append(questId).append('\n');
        dsl.append("title \"").append(title).append("\"\n\n");

        for (int i = 0; i < nodes.size(); i++) {
            NodeDef node = nodes.get(i);
            dsl.append("node ").append(node.id()).append('\n');
            dsl.append("title \"").append(escape(node.title())).append("\"\n");
            dsl.append("\"").append(escape(node.text())).append("\"\n");

            List<ChoiceDef> choices = node.choices().isEmpty() && i + 1 < nodes.size()
                    ? List.of(new ChoiceDef("Далее", nodes.get(i + 1).id()))
                    : node.choices();

            for (ChoiceDef choice : choices) {
                dsl.append("> \"").append(escape(choice.text())).append("\" -> ").append(choice.next()).append('\n');
            }
            dsl.append('\n');
        }

        return dsl.toString();
    }

    private List<NodeDef> extractNodes(JsonNode graphJson) {
        List<NodeDef> nodes = new ArrayList<>();
        JsonNode nodesNode = graphJson.path("nodes");
        if (!nodesNode.isArray() || nodesNode.isEmpty()) {
            nodesNode = graphJson.path("step_outputs").path("node_list").path("nodes");
        }
        if (!nodesNode.isArray()) {
            return nodes;
        }

        JsonNode edgesByNode = graphJson.path("step_outputs").path("edges").path("edges");
        JsonNode detailsByNode = graphJson.path("step_outputs").path("node_details").path("node_details");

        for (JsonNode nodeNode : nodesNode) {
            String id = safeText(nodeNode.path("id").asText(null), null);
            if (id == null || id.isBlank()) {
                continue;
            }

            String purpose = safeText(nodeNode.path("purpose").asText(null), "Узел квеста");
            String title = purpose;

            JsonNode detailsNode = nodeNode;
            if ((!nodeNode.has("required_facts") || !nodeNode.has("revealed_facts")) && detailsByNode.isArray()) {
                JsonNode found = findById(detailsByNode, id);
                if (found != null) {
                    detailsNode = found;
                }
            }

            String text = "Назначение узла: " + purpose + ".";
            if (detailsNode.path("required_facts").isArray()) {
                text += " Требуется фактов: " + detailsNode.path("required_facts").size() + ".";
            }
            if (detailsNode.path("revealed_facts").isArray()) {
                text += " Открывается фактов: " + detailsNode.path("revealed_facts").size() + ".";
            }

            List<ChoiceDef> choices = new ArrayList<>();
            JsonNode choicesNode = nodeNode.path("choices");
            if ((!choicesNode.isArray() || choicesNode.isEmpty()) && edgesByNode.isArray()) {
                JsonNode edgeNode = findById(edgesByNode, id);
                if (edgeNode != null) {
                    choicesNode = edgeNode.path("choices");
                }
            }

            if (choicesNode.isArray()) {
                for (JsonNode choiceNode : choicesNode) {
                    String choiceText = safeText(choiceNode.path("text").asText(null), null);
                    String next = safeText(choiceNode.path("next").asText(null), null);
                    if (choiceText != null && !choiceText.isBlank() && next != null && !next.isBlank()) {
                        choices.add(new ChoiceDef(choiceText, next));
                    }
                }
            }

            nodes.add(new NodeDef(id, title, text, choices));
        }

        return nodes;
    }

    private JsonNode findById(JsonNode arrayNode, String id) {
        for (JsonNode node : arrayNode) {
            if (id.equals(node.path("id").asText())) {
                return node;
            }
        }
        return null;
    }

    private List<NodeDef> defaultNodes() {
        return List.of(
                new NodeDef("n1", "Начало", "Назначение узла: Введение в загадку.", List.of(new ChoiceDef("Далее", "n2"))),
                new NodeDef("n2", "Развитие", "Назначение узла: Проверка ключевой версии.", List.of(new ChoiceDef("Далее", "n3"))),
                new NodeDef("n3", "Финал", "Назначение узла: Финальная переоценка ситуации.", List.of())
        );
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

    private record NodeDef(String id, String title, String text, List<ChoiceDef> choices) {
    }

    private record ChoiceDef(String text, String next) {
    }
}

