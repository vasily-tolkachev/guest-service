package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.generator.ProjectRepository;
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.StageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MysteryStageRunner implements StageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a narrative quest designer.
            Return only valid JSON object with no markdown and no additional text.
            Build a mystery foundation for a quest.
            Keep it compact and production-oriented.
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.MYSTERY;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        String userPrompt = buildUserPrompt(project);
        return aiClient.generate(SYSTEM_PROMPT, userPrompt);
    }

    private String buildUserPrompt(QuestProject project) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Generate mystery stage JSON for a quest project.

                projectName: %s
                questStyle: %s

                Return JSON with this shape:
                {
                  "mysteryTitle": "string",
                  "coreQuestion": "string",
                  "incitingIncident": "string",
                  "stakes": ["string"],
                  "keyClues": ["string"],
                  "redHerrings": ["string"],
                  "tone": "string"
                }

                Constraints:
                - respond in russian
                - concise, practical content
                - each array should contain 3-6 items
                - do not include markdown
                """.formatted(project.getName(), style);
    }
}

