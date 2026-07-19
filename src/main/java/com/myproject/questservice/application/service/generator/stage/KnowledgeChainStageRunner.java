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
public class KnowledgeChainStageRunner implements KnowledgeChainWayStageRunner, PromptPreviewStageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Knowledge Chain Generator for a quest generation pipeline.

            Task:
            Build a knowledge-acquisition chain for ONE realisation way.

            The chain answers:
            "How does the player understand what to do?"

            IMPORTANT:
            - Achievement remains high-level (for example: "Open bunker").
            - Chain steps are NOT achievement steps.
            - Chain steps are knowledge discovery steps.

            Output MUST be valid JSON only.
            All JSON string values MUST be in Russian.

            Return JSON with this schema:
            {
              "knowledge_chain": {
                "achievement_id": "A1",
                "way_id": "W1",
                "target_achievement": "",
                "knowledge_chain": [
                  {
                    "id": "K1",
                    "action": "",
                    "knowledge_gained": "",
                    "leads_to": "K2"
                  }
                ],
                "entry_point": "K1",
                "final_knowledge": ""
              }
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public StageType type() {
        return StageType.KNOWLEDGE_CHAIN;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        throw new ConflictException("KNOWLEDGE_CHAIN supports only per-way generation");
    }

    @Override
    public JsonNode generateKnowledgeChain(UUID projectId, String wayId, JsonNode currentOutput) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage informationFlowStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_INFORMATION_FLOW);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
        String normalizedWayId = wayId == null ? "" : wayId.trim();
        if (normalizedWayId.isBlank()) {
            throw new ConflictException("wayId is required");
        }
        JsonNode flowNode = findFlowNode(informationFlowStage.getCurrentRevision().outputJson(), normalizedWayId);
        if (flowNode == null) {
            throw new NotFoundException("Flow for way not found: " + normalizedWayId);
        }
        JsonNode wayNode = findWay(realisationStage.getCurrentRevision().outputJson(), normalizedWayId);
        if (wayNode == null) {
            throw new NotFoundException("Way not found in ACHIEVEMENT_REALISATION: " + normalizedWayId);
        }

        String userPrompt = """
                Build KNOWLEDGE_CHAIN for one way only.

                way_id: %s

                information_flow_for_way_json:
                %s

                world_json:
                %s

                way_json:
                %s

                Requirements:
                - produce exactly one knowledge_chain block
                - knowledge_chain must contain exactly 3 linked steps (K1 -> K2 -> K3)
                - each step must describe how new knowledge is obtained
                - chain should be playable and logically connected
                - do not turn chain into direct achievement checklist
                - all text in Russian
                """.formatted(
                normalizedWayId,
                compactJson(flowNode),
                compactJson(worldStage.getCurrentRevision().outputJson()),
                compactJson(wayNode)
        );

        JsonNode generated = aiClient.generate(SYSTEM_PROMPT, userPrompt);
        return mergeKnowledgeChainOutput(currentOutput, generated, normalizedWayId);
    }

    @Override
    public StagePromptPreview previewPrompt(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage informationFlowStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_INFORMATION_FLOW);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage realisationStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);

        JsonNode informationFlows = informationFlowStage.getCurrentRevision().outputJson().path("achievement_information_flow");
        JsonNode firstFlow = objectMapper.createObjectNode();
        JsonNode firstWay = objectMapper.createObjectNode();
        if (informationFlows.isArray() && !informationFlows.isEmpty()) {
            firstFlow = informationFlows.get(0);
            String wayId = firstFlow.path("way_id").asText("");
            if (!wayId.isBlank()) {
                firstWay = findWay(realisationStage.getCurrentRevision().outputJson(), wayId);
            }
        }

        String userPrompt = """
                Build KNOWLEDGE_CHAIN for one way only.

                way_id: %s

                information_flow_for_way_json:
                %s

                world_json:
                %s

                way_json:
                %s

                Requirements:
                - produce exactly one knowledge_chain block
                - knowledge_chain must contain exactly 3 linked steps (K1 -> K2 -> K3)
                - each step must describe how new knowledge is obtained
                - chain should be playable and logically connected
                - do not turn chain into direct achievement checklist
                - all text in Russian
                """.formatted(
                firstFlow.path("way_id").asText(""),
                compactJson(firstFlow),
                compactJson(worldStage.getCurrentRevision().outputJson()),
                compactJson(firstWay)
        );
        return new StagePromptPreview(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("KNOWLEDGE_CHAIN generation requires APPROVED " + type + " stage");
        }
        return stage;
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

    private JsonNode findFlowNode(JsonNode informationFlowJson, String wayId) {
        JsonNode flows = informationFlowJson.path("achievement_information_flow");
        if (!flows.isArray()) {
            return null;
        }
        for (JsonNode flow : flows) {
            if (wayId.equalsIgnoreCase(flow.path("way_id").asText(""))) {
                return flow;
            }
        }
        return null;
    }

    private JsonNode mergeKnowledgeChainOutput(JsonNode currentOutput, JsonNode generated, String wayId) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode chains = objectMapper.createArrayNode();
        if (currentOutput != null && currentOutput.path("knowledge_chains").isArray()) {
            for (JsonNode existing : currentOutput.path("knowledge_chains")) {
                if (!wayId.equalsIgnoreCase(existing.path("way_id").asText(""))) {
                    chains.add(existing);
                }
            }
        }

        JsonNode generatedChain = generated.path("knowledge_chain");
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("achievement_id", generatedChain.path("achievement_id").asText(""));
        normalized.put("way_id", wayId);
        normalized.put("target_achievement", generatedChain.path("target_achievement").asText(""));
        normalized.set("knowledge_chain", generatedChain.path("knowledge_chain").isArray()
                ? generatedChain.path("knowledge_chain")
                : objectMapper.createArrayNode());
        normalized.put("entry_point", generatedChain.path("entry_point").asText("K1"));
        normalized.put("final_knowledge", generatedChain.path("final_knowledge").asText(""));
        chains.add(normalized);

        root.set("knowledge_chains", chains);
        return root;
    }

    private String compactJson(JsonNode json) {
        String raw = json == null ? "{}" : json.toString();
        int max = 9_000;
        if (raw.length() <= max) {
            return raw;
        }
        return raw.substring(0, max) + "...";
    }
}
