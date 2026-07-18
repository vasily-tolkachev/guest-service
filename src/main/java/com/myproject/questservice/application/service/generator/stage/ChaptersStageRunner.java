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
            
             Goal:
             Expand one quest chapter into a set of investigation scenes.
            
             The purpose of this stage is to create the gameplay structure of the chapter.
             Generate only scene structure.
             Do not write full scenes.
            
             A chapter represents a major investigation phase.
             Each scene inside the chapter should represent a different gameplay situation, discovery, obstacle, or decision.
            
             IMPORTANT:
             - Output MUST be valid JSON only.
             - All JSON string values MUST be in Russian.
             - No stage is allowed to rewrite or retell data from previous stages.
             - Do NOT create new mysteries.
             - Do NOT create new facts.
             - Do NOT create new NPCs.
             - Do NOT create new locations.
             - Use only IDs from approved artifacts.
             - No dialogues.
             - No artistic prose.
             - No scene text.
             - Structure only.
            
            
             Input:
             - Approved QUEST_DESCRIPTION.
             - Approved WORLD.
             - Approved NPC.
             - Approved FACTS.
             - One QUEST_OUTLINE chapter.
            
            
             Your task:
             Create 3-6 scenes inside this chapter.
            
             Each scene must:
             - have a clear investigation purpose;
             - happen in a specific location;
             - involve existing NPCs;
             - use existing facts;
             - reveal or connect evidence;
             - create progression toward understanding the mystery.
            
            
             Output schema:
            
             {
               "chapterId": "CH01",
            
               "scenes": [
                 {
                   "id": "SC01",
            
                   "title": "",
            
                   "situation": "",
            
                   "objective": "",
            
                   "location": "L01",
            
                   "participants": [
                     "NPC01"
                   ],
            
                   "requiredFacts": [
                     "F01"
                   ],
            
                   "revealedFacts": [
                     "F02"
                   ]
                 }
               ]
             }
            
            
             Field rules:
            
            
             title:
             - Name the gameplay episode.
             - Should describe a specific event, conflict, discovery, or turning point.
            
             Good:
             "Следы, которых не должно было быть"
             "Чужой доступ в закрытом журнале"
             "Свидетель меняет показания"
            
             Bad:
             "Осмотр архива"
             "Проверка данных"
            
            
             situation:
             - Describe the current situation at the beginning of the scene.
             - Explain what problem exists now.
             - Explain why player needs to act.
            
             Do not write atmosphere or cinematic description.
            
            
             objective:
             - The player's investigation goal in this scene.
            
             Good:
             "Определить, кто получил доступ к хранилищу перед исчезновением."
            
             Bad:
             "Осмотреть помещение."
            
            
             requiredFacts:
             - Facts already known before this scene.
             - Use only FACT ids.
            
            
             revealedFacts:
             - Facts that become available after this scene.
             - Use only FACT ids.
            
            
             Scene design rules:
            
             A chapter should not be a list of locations.
            
             Bad structure:
             Scene 1: Visit archive
             Scene 2: Visit laboratory
             Scene 3: Visit office
            
             Good structure:
             Scene 1:
             Discover that official timeline is impossible.
            
             Scene 2:
             Find the person who benefited from the false timeline.
            
             Scene 3:
             Discover evidence that changes interpretation of previous events.
            
            
             Every chapter should contain:
             - initial investigation,
             - complication,
             - new information,
             - escalation toward next chapter.
            
            
             Do not create:
             - steps,
             - actions,
             - choices,
             - endings,
             - dialogue,
             - DSL.
            
            
             Generate only JSON. Values should be in russian.
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

        QuestStage mysteryStage = requiredApprovedStage(project, StageType.QUEST_DESCRIPTION);
        QuestStage worldStage = requiredApprovedStage(project, StageType.WORLD);
        QuestStage npcStage = requiredApprovedStage(project, StageType.ACHIEVEMENT_REALISATION);
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
                - each scene must include: id, title, situation, objective, location, participants, requiredFacts, revealedFacts, player_actions, obstacles
                - scene title must be an in-world dramatic situation, not an operational task
                - scene situation must describe what is happening now in the world and why it is tense
                - forbidden style for title/situation: process workflow like "Проверка", "Запрос", "Формализация", "Анализ", "Первичный осмотр"
                - location must reference chapter.locations only
                - participants must reference chapter.participants only
                - requiredFacts and revealedFacts must reference chapter.facts only
                - keep player_actions and obstacles concise, structural, and playable
                - do not include dialogue or prose blocks

                Example scene format:
                {
                  "id": "SC01",
                  "title": "Осмотр энергомодуля",
                  "situation": "После инцидента доступ к энергомодулю ограничен; инженер нервничает и пытается свернуть осмотр, пока оборудование продолжает сбоить.",
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
