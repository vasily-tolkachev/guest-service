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
public class QuestConstraintsStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Quest Constraints Generator for a quest generation pipeline.

            Your task is to create ONLY Stage 1.5: Quest Constraints.

            Input is approved QUEST_DESCRIPTION.
            This stage defines boundaries for WORLD generation.

            This is NOT world design.
            Do NOT create locations, NPCs, objects, scenes, dialogues, gameplay, or full story beats.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - No stage is allowed to rewrite or retell data from previous stages.

            Return JSON with this schema:
            {
              "resolution_model": {
                "path_type": "",
                "external_help": "",
                "civilization_level": "",
                "mobility_limit": ""
              },
              "world_constraints": {
                "setting_class": "",
                "tone": "",
                "forbidden_elements": [""],
                "required_elements": [""]
              }
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.QUEST_CONSTRAINTS;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage questDescriptionStage = project.findStage(StageType.QUEST_DESCRIPTION)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + StageType.QUEST_DESCRIPTION));

        if (questDescriptionStage.getStatus() != StageStatus.APPROVED || questDescriptionStage.getCurrentRevision() == null) {
            throw new ConflictException("QUEST_CONSTRAINTS generation requires APPROVED QUEST_DESCRIPTION stage");
        }

        String userPrompt = buildUserPrompt(questDescriptionStage.getCurrentRevision().outputJson());
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private String buildUserPrompt(JsonNode approvedQuestDescriptionJson) {
        return """
                Build QUEST_CONSTRAINTS artifact from approved QUEST_DESCRIPTION.

                approved_quest_description_json:
                %s

                Requirements:
                - keep constraints abstract and implementation-agnostic
                - do not invent concrete world entities
                - forbidden_elements and required_elements must contain 2-8 concise entries each
                - use constraints that reduce arbitrary world generation variance
                - all text in Russian
                """.formatted(approvedQuestDescriptionJson.toString());
    }
}
