package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;
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
public class WriterStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Writer Stage Generator for a quest generation pipeline.

            Your task is to produce the final quest design JSON artifact from approved stages.
            Inputs are approved mystery, world, npc, facts, and flow artifacts.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - This is final JSON artifact for quest content, but not DSL export.

            Return JSON with this schema:
            {
              "quest_title": "",
              "quest_summary": "",
              "narrative_tone": "",
              "chapters": [
                {
                  "chapter": "",
                  "purpose": "",
                  "player_objective": "",
                  "core_conflict": "",
                  "possible_outcomes": [""]
                }
              ],
              "role_contributions": [
                {
                  "role": "",
                  "function_in_reveal_arc": ""
                }
              ],
              "ending_variants": [""],
              "final_reveal_statement": ""
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.WRITER;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.MYSTERY);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.NPC);
        QuestStage factsStage = requiredApprovedStage(project, StageType.FACTS);
        QuestStage flowStage = requiredApprovedStage(project, StageType.FLOW);

        String userPrompt = buildUserPrompt(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                npcStage.getCurrentRevision().outputJson(),
                factsStage.getCurrentRevision().outputJson(),
                flowStage.getCurrentRevision().outputJson()
        );
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("WRITER generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private String buildUserPrompt(
            QuestProject project,
            JsonNode mysteryJson,
            JsonNode worldJson,
            JsonNode npcJson,
            JsonNode factsJson,
            JsonNode flowJson
    ) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Build WRITER stage final quest artifact from approved mystery, world, npc, facts, and flow data.

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

                approved_flow_json:
                %s

                Requirements:
                - produce coherent final quest design artifact
                - keep consistency with prior stage constraints
                - do not output DSL and do not reference implementation details
                - all text in Russian
                """.formatted(
                project.getName(),
                style,
                mysteryJson.toPrettyString(),
                worldJson.toPrettyString(),
                npcJson.toPrettyString(),
                factsJson.toPrettyString(),
                flowJson.toPrettyString()
        );
    }
}

