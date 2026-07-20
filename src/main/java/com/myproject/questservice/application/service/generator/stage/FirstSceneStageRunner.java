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
public class FirstSceneStageRunner implements StageRunner, PromptPreviewStageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a quest scene generator.

            Generate only the first scene of a text quest.

            IMPORTANT:
            - Output must be valid JSON only.
            - All JSON string values must be in Russian.
            - Keep it concrete and grounded.
            - Generate exactly one scene with short actionable choices.
            - No lore dumps, no full quest outline, no walkthrough.

            Output schema:
            {
              "scene": {
                "id": "S1",
                "text": "",
                "choices": [
                  { "id": "C1", "text": "" }
                ]
              }
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;

    @Override
    public StageType type() {
        return StageType.FIRST_SCENE;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        return aiClient.generate(SYSTEM_PROMPT, buildUserPrompt(project));
    }

    @Override
    public StagePromptPreview previewPrompt(UUID projectId) {
        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        return new StagePromptPreview(SYSTEM_PROMPT, buildUserPrompt(project));
    }

    private String buildUserPrompt(QuestProject project) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Project:
                - name: %s
                - style: %s

                Requirements:
                - produce one opening scene only
                - scene text: 3-6 short sentences
                - choices: 2-4 options
                - each choice text should describe a clear player action
                - use Russian language for all text fields
                """.formatted(project.getName(), style);
    }
}
