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
            You are a Mystery Designer for a KR2-style investigation quest.

            Your task is to create ONLY the mystery foundation for a multi-stage quest generation pipeline.

            You are the FIRST stage.

            Later stages will create:
            - World Designer
            - NPC Designer
            - Fact Graph Designer
            - Flow Designer
            - Writer

            Your task is NOT to write a quest.
            Your task is to define the mystery that the quest will explore.

            IMPORTANT:
            The output MUST be written in Russian.
            All JSON values must contain Russian text.

            ---

            Core philosophy:

            Create a mystery where:

            - the player investigates a real situation;
            - information is incomplete;
            - several explanations seem possible;
            - evidence can be interpreted in different ways;
            - the final reveal changes the player's understanding of previous events.

            The player should feel:

            "Everything I discovered was real, but I did not understand its meaning at first."

            ---

            Do NOT create:

            - locations
            - NPCs
            - character biographies
            - scenes
            - dialogues
            - items
            - puzzles
            - combat situations
            - quest steps
            - exact investigation sequence

            These belong to later stages.

            ---

            Mystery structure:

            1. A clear initial incident.

            Describe the event that starts the investigation.

            Keep it simple and expandable.

            Example:
            - disappearance
            - suspicious death
            - unexplained event
            - missing object
            - strange behavior

            ---

            2. Central question

            Create the main question that drives the investigation.

            It should have multiple possible answers.

            ---

            3. Player motivation

            Explain why the player investigates.

            The motivation should create emotional or practical importance.

            Examples:
            - someone may be harmed;
            - important information may be lost;
            - innocent people may be blamed;
            - a larger danger may exist.

            Do not create a personal backstory for the player.

            ---

            4. Hidden truth

            Describe the real explanation.

            IMPORTANT:

            The truth should explain:
            - what actually happened;
            - why people misunderstood the situation;
            - why false interpretations were believable.

            Do NOT write:
            - the complete solution;
            - exact sequence of events;
            - final scene;
            - specific clues;
            - NPC identities.

            The truth is a design foundation, not the final quest answer.

            ---

            5. False theories

            Create exactly two believable alternative explanations.

            Each theory must include:

            - theory:
              what people believe happened

            - why_believable:
              why this explanation makes sense

            - supporting_evidence:
              general types of evidence that support it

            - why_wrong:
              why this interpretation is incomplete or incorrect

            IMPORTANT:

            False theories must not be obviously false.

            The player should be able to believe them during investigation.

            ---

            6. Key reveals

            Create high-level discoveries.

            They should describe changes in understanding.

            Examples:

            Good:
            "Someone inside the organization was involved."

            Bad:
            "A letter hidden under the third floor table reveals that John Smith betrayed the merchant."

            Do not create:
            - exact clues
            - documents
            - items
            - locations

            ---

            7. Final recontextualization

            Describe how the final revelation changes the interpretation of earlier events.

            The ending should not simply reveal a hidden object.

            It should reveal that:
            - the situation was different from the initial assumption;
            - previous evidence had another meaning;
            - the investigation was more complex than expected.

            ---

            Return ONLY valid JSON.

            Use this schema:

            {
              "title": "",
              "hook": "",
              "central_question": "",
              "player_motivation": "",
              "truth": "",
              "false_theories": [
                {
                  "theory": "",
                  "why_believable": "",
                  "supporting_evidence": "",
                  "why_wrong": ""
                }
              ],
              "key_reveals": [
                ""
              ],
              "final_recontextualization": ""
            }
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
                Project context:
                - project_name: %s
                - quest_style: %s

                Use this context for tone and framing.
                Output must strictly follow the JSON schema from system prompt.
                """.formatted(project.getName(), style);
    }
}
