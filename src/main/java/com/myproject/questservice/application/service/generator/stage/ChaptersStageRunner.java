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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChaptersStageRunner implements ChapterStageRunner {
    private static final String SYSTEM_PROMPT = """
            You are a Chapter Generator for a quest generation pipeline.

            Your task is to generate scenes for exactly one chapter.

            IMPORTANT:
            - Output MUST be valid JSON only.
            - All JSON string values MUST be in Russian.
            - No stage is allowed to rewrite or retell data from previous stages.
            - Do NOT generate dialogues.
            - Keep output compact and structural.

            Return JSON with this schema:
            {
              "chapterId": "CH01",
              "scenes": [
                {
                  "id": "SC01",
                  "title": "",
                  "objective": "",
                  "location": "L01",
                  "participants": ["NPC01", "NPC02"],
                  "requiredFacts": ["F01"],
                  "revealedFacts": ["F02"],
                  "player_actions": [""],
                  "obstacles": [""]
                }
              ]
            }
            """;

    private final ProjectRepository projectRepository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    public StageType type() {
        return StageType.CHAPTERS;
    }

    @Override
    public JsonNode generate(UUID projectId) {
        throw new ConflictException("CHAPTERS stage supports chapter-by-chapter generation only");
    }

    @Override
    public JsonNode generateChapter(UUID projectId, String chapterId, JsonNode currentOutput) {
        if (chapterId == null || chapterId.isBlank()) {
            throw new ConflictException("chapterId is required");
        }

        QuestProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.MYSTERY);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.NPC);
        QuestStage factsStage = requiredApprovedStage(project, StageType.FACTS);
        QuestStage outlineStage = requiredApprovedStage(project, StageType.QUEST_OUTLINE);

        JsonNode chapter = findChapter(outlineStage.getCurrentRevision().outputJson(), chapterId.trim());
        if (chapter == null) {
            throw new NotFoundException("Chapter not found in QUEST_OUTLINE: " + chapterId);
        }

        String userPrompt = buildUserPrompt(
                project,
                mysteryStage.getCurrentRevision().outputJson(),
                worldStage.getCurrentRevision().outputJson(),
                npcStage.getCurrentRevision().outputJson(),
                factsStage.getCurrentRevision().outputJson(),
                chapter
        );
        JsonNode generated = aiClient.generate(SYSTEM_PROMPT, userPrompt);

        return mergeChapterOutput(currentOutput, generated, chapterId.trim());
    }

    private QuestStage requiredApprovedStage(QuestProject project, StageType type) {
        QuestStage stage = project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
        if (stage.getStatus() != StageStatus.APPROVED || stage.getCurrentRevision() == null) {
            throw new ConflictException("CHAPTERS generation requires APPROVED " + type + " stage");
        }
        return stage;
    }

    private JsonNode findChapter(JsonNode outlineJson, String chapterId) {
        JsonNode chapters = outlineJson.path("chapters");
        if (!chapters.isArray()) {
            return null;
        }
        for (JsonNode chapter : chapters) {
            if (chapterId.equalsIgnoreCase(chapter.path("id").asText(""))) {
                return chapter;
            }
        }
        return null;
    }

    private String buildUserPrompt(
            QuestProject project,
            JsonNode mysteryJson,
            JsonNode worldJson,
            JsonNode npcJson,
            JsonNode factsJson,
            JsonNode chapterJson
    ) {
        String style = project.getQuestStyle() == null || project.getQuestStyle().isBlank()
                ? "classic-adventure"
                : project.getQuestStyle().trim();
        return """
                Generate scenes for one chapter only.

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

                chapter_json:
                %s

                Requirements:
                - generate 3-6 scenes for this chapter
                - scene ids must be SC01, SC02, ... inside this chapter output
                - each scene must include: id, title, objective, location, participants, requiredFacts, revealedFacts, player_actions, obstacles
                - location must reference chapter.locations only
                - participants must reference chapter.participants only
                - requiredFacts and revealedFacts must reference chapter.facts only
                - keep player_actions and obstacles concise, structural, and playable
                - do not include dialogue or prose blocks

                Example scene format:
                {
                  "id": "SC01",
                  "title": "Осмотр энергомодуля",
                  "objective": "Понять, является ли повреждение следствием аварии или вмешательства.",
                  "location": "L02",
                  "participants": ["NPC03", "NPC05"],
                  "requiredFacts": ["F02"],
                  "revealedFacts": ["F14"],
                  "player_actions": [
                    "Осмотреть генератор",
                    "Изучить журналы",
                    "Поговорить с инженером"
                  ],
                  "obstacles": [
                    "Повреждённое оборудование",
                    "Инженер не хочет сотрудничать"
                  ]
                }
                """.formatted(project.getName(), style, mysteryJson, worldJson, npcJson, factsJson, chapterJson);
    }

    private JsonNode mergeChapterOutput(JsonNode currentOutput, JsonNode generatedChapter, String chapterId) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode chapters = objectMapper.createArrayNode();

        Set<String> seen = new HashSet<>();
        if (currentOutput != null && currentOutput.path("chapters").isArray()) {
            for (JsonNode existing : currentOutput.path("chapters")) {
                String id = existing.path("chapterId").asText("");
                if (!id.equalsIgnoreCase(chapterId) && !id.isBlank()) {
                    chapters.add(existing);
                    seen.add(id.toUpperCase());
                }
            }
        }

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("chapterId", chapterId);
        normalized.set("scenes", generatedChapter.path("scenes").isArray() ? generatedChapter.path("scenes") : objectMapper.createArrayNode());
        chapters.add(normalized);
        seen.add(chapterId.toUpperCase());

        root.set("chapters", chapters);
        return root;
    }
}
