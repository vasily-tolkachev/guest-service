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

            Your task is to create ONLY the mystery foundation.

            You are the first stage of a multi-stage quest generation pipeline.

            Later stages will create:
            - world
            - locations
            - NPCs
            - items
            - facts
            - quest flow
            - dialogues

            Therefore, do NOT design these elements now.

            Your output must be a mystery blueprint, not a finished quest.

            IMPORTANT:
            The output MUST be written in Russian.
            All text fields must contain Russian text.

            ---

            Design principles:

            Create a mystery where:

            - the player investigates a situation with incomplete information;
            - there are multiple believable interpretations;
            - the truth is discovered gradually;
            - the final reveal changes the player's understanding of previous events.

            The player should feel:

            "I investigated a real situation, but my interpretation of the evidence was incomplete."

            ---

            Avoid:

            - overly detailed scenes;
            - specific locations;
            - detailed NPC biographies;
            - exact dialogue;
            - item lists;
            - gameplay solutions.

            Keep enough ambiguity so later stages can expand the idea.

            ---

            The mystery must contain:

            1. A clear initial incident.

            Example:
            - disappearance
            - unexplained event
            - strange behavior
            - missing object
            - suspicious accident

            2. A central question.

            The player should have a reason to investigate.

            3. A hidden truth.

            The truth should explain:
            - what actually happened;
            - why it was misunderstood;
            - why the evidence was misleading.

            Do not write the complete quest resolution.

            4. Two believable false theories.

            Each false theory must include:

            - what people believe;
            - why it seems reasonable;
            - what evidence supports it;
            - why it is ultimately wrong.

            False theories must be realistic enough that the player can believe them.

            5. Emotional or personal stake.

            Explain why solving this mystery matters.

            Examples:
            - someone may still be alive;
            - a dangerous truth may be hidden;
            - someone's reputation or future depends on the investigation;
            - the player has a personal reason to continue.

            6. Final recontextualization.

            The final reveal should make earlier events look different.

            It should not simply be:
            "The hidden object was found."

            It should be:
            "What the player thought happened was incomplete or based on a wrong assumption."

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
