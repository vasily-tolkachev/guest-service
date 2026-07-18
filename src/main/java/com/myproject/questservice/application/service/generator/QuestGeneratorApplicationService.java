package com.myproject.questservice.application.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myproject.questservice.adapter.in.rest.dto.generator.QuestProjectView;
import com.myproject.questservice.adapter.in.rest.dto.generator.QuestStageView;
import com.myproject.questservice.adapter.in.rest.dto.generator.StageRevisionView;
import com.myproject.questservice.application.port.in.generator.QuestGeneratorUseCase;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.application.service.ConflictException;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.NotImplementedException;
import com.myproject.questservice.application.service.generator.stage.StageRunner;
import com.myproject.questservice.application.service.generator.stage.StageRunnerRegistry;
import com.myproject.questservice.application.service.generator.stage.StepStageRunner;
import com.myproject.questservice.application.service.generator.stage.ChapterStageRunner;
import com.myproject.questservice.application.service.generator.stage.SceneStageRunner;
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.QuestStage;
import com.myproject.questservice.domain.generator.StageRevision;
import com.myproject.questservice.domain.generator.StageStatus;
import com.myproject.questservice.domain.generator.StageType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class QuestGeneratorApplicationService implements QuestGeneratorUseCase {

    private final ProjectRepository projectRepository;
    private final StageRunnerRegistry stageRunnerRegistry;
    private final ObjectMapper objectMapper;

    public QuestGeneratorApplicationService(
            ProjectRepository projectRepository,
            StageRunnerRegistry stageRunnerRegistry,
            ObjectMapper objectMapper
    ) {
        this.projectRepository = projectRepository;
        this.stageRunnerRegistry = stageRunnerRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public QuestProjectView createProject(String name, String questStyle) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new BadRequestException("Project name is required");
        }
        String normalizedQuestStyle = questStyle == null || questStyle.trim().isBlank()
                ? "classic-adventure"
                : questStyle.trim();
        QuestProject created = projectRepository.save(QuestProject.create(normalizedName, normalizedQuestStyle));
        return toView(created);
    }

    @Override
    public QuestProjectView getProject(UUID id) {
        return toView(getRequiredProject(id));
    }

    @Override
    public List<QuestProjectView> listProjects() {
        return projectRepository.findAll().stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public QuestProjectView generateStage(UUID projectId, StageType stageType) {
        StageRunner runner = stageRunnerRegistry.find(stageType)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for " + stageType));
        if (runner instanceof StepStageRunner stepRunner) {
            return generateStageStep(projectId, stageType, nextStep(stepRunner, getRequiredProject(projectId), stageType));
        }

        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, stageType);
        if (stage.getStatus() != StageStatus.READY && stage.getStatus() != StageStatus.REVIEW) {
            throw new ConflictException("Stage is not ready for generation: " + stageType);
        }
        StageStatus previousStatus = stage.getStatus();

        stage.setStatus(StageStatus.GENERATING);
        JsonNode output;
        try {
            output = runner.generate(projectId);
        } catch (RuntimeException ex) {
            stage.setStatus(previousStatus);
            projectRepository.save(project);
            throw ex;
        }

        int nextRevisionNumber = stage.getCurrentRevision() == null
                ? 1
                : stage.getCurrentRevision().revisionNumber() + 1;
        stage.setCurrentRevision(new StageRevision(nextRevisionNumber, output, Instant.now()));
        stage.setApproved(false);
        stage.setStatus(StageStatus.REVIEW);

        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView approveStage(UUID projectId, StageType stageType) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, stageType);

        if (stage.getStatus() != StageStatus.REVIEW) {
            throw new ConflictException("Only REVIEW stage can be approved: " + stageType);
        }
        if (stage.getCurrentRevision() == null) {
            throw new ConflictException("Stage has no revision to approve: " + stageType);
        }

        stage.setApproved(true);
        stage.setStatus(StageStatus.APPROVED);

        if (stageType != StageType.SCENES) {
            project.nextStage(stageType).ifPresent(nextStage -> {
                if (nextStage.getStatus() == StageStatus.NOT_STARTED) {
                    nextStage.setStatus(StageStatus.READY);
                }
            });
        }

        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView generateChapter(UUID projectId, String chapterId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage chaptersStage = getRequiredStage(project, StageType.CHAPTERS);
        if (chaptersStage.getStatus() == StageStatus.NOT_STARTED) {
            chaptersStage.setStatus(StageStatus.READY);
        }
        if (chaptersStage.getStatus() != StageStatus.READY && chaptersStage.getStatus() != StageStatus.REVIEW) {
            throw new ConflictException("CHAPTERS stage is not ready for chapter generation");
        }

        StageRunner runner = stageRunnerRegistry.find(StageType.CHAPTERS)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for CHAPTERS"));
        if (!(runner instanceof ChapterStageRunner chapterRunner)) {
            throw new ConflictException("CHAPTERS stage runner does not support chapter generation");
        }

        StageStatus previousStatus = chaptersStage.getStatus();
        chaptersStage.setStatus(StageStatus.GENERATING);
        JsonNode currentOutput = chaptersStage.getCurrentRevision() == null ? null : chaptersStage.getCurrentRevision().outputJson();
        JsonNode output;
        try {
            output = chapterRunner.generateChapter(projectId, chapterId, currentOutput);
        } catch (RuntimeException ex) {
            chaptersStage.setStatus(previousStatus);
            projectRepository.save(project);
            throw ex;
        }

        int nextRevisionNumber = chaptersStage.getCurrentRevision() == null
                ? 1
                : chaptersStage.getCurrentRevision().revisionNumber() + 1;
        chaptersStage.setCurrentRevision(new StageRevision(nextRevisionNumber, markChapterReview(output, chapterId), Instant.now()));
        chaptersStage.setApproved(false);
        chaptersStage.setStatus(StageStatus.REVIEW);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView approveChapter(UUID projectId, String chapterId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage chaptersStage = getRequiredStage(project, StageType.CHAPTERS);
        if (chaptersStage.getCurrentRevision() == null) {
            throw new ConflictException("CHAPTERS stage has no revision");
        }
        JsonNode updatedOutput = markChapterApproved(chaptersStage.getCurrentRevision().outputJson(), chapterId);
        int nextRevisionNumber = chaptersStage.getCurrentRevision().revisionNumber() + 1;
        chaptersStage.setCurrentRevision(new StageRevision(nextRevisionNumber, updatedOutput, Instant.now()));
        boolean allApproved = areAllOutlineChaptersApproved(project, updatedOutput);
        chaptersStage.setApproved(allApproved);
        chaptersStage.setStatus(allApproved ? StageStatus.APPROVED : StageStatus.REVIEW);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView generateScene(UUID projectId, String sceneId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage scenesStage = getRequiredStage(project, StageType.SCENES);
        if (scenesStage.getStatus() == StageStatus.NOT_STARTED) {
            scenesStage.setStatus(StageStatus.READY);
        }
        if (scenesStage.getStatus() != StageStatus.READY && scenesStage.getStatus() != StageStatus.REVIEW) {
            throw new ConflictException("SCENES stage is not ready for scene generation");
        }

        StageRunner runner = stageRunnerRegistry.find(StageType.SCENES)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for SCENES"));
        if (!(runner instanceof SceneStageRunner sceneRunner)) {
            throw new ConflictException("SCENES stage runner does not support scene generation");
        }

        StageStatus previousStatus = scenesStage.getStatus();
        scenesStage.setStatus(StageStatus.GENERATING);
        JsonNode currentOutput = scenesStage.getCurrentRevision() == null ? null : scenesStage.getCurrentRevision().outputJson();
        JsonNode output;
        try {
            output = sceneRunner.generateScene(projectId, sceneId, currentOutput);
        } catch (RuntimeException ex) {
            scenesStage.setStatus(previousStatus);
            projectRepository.save(project);
            throw ex;
        }

        int nextRevisionNumber = scenesStage.getCurrentRevision() == null
                ? 1
                : scenesStage.getCurrentRevision().revisionNumber() + 1;
        scenesStage.setCurrentRevision(new StageRevision(nextRevisionNumber, markSceneReview(output, sceneId), Instant.now()));
        scenesStage.setApproved(false);
        scenesStage.setStatus(StageStatus.REVIEW);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView approveScene(UUID projectId, String sceneId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage scenesStage = getRequiredStage(project, StageType.SCENES);
        if (scenesStage.getCurrentRevision() == null) {
            throw new ConflictException("SCENES stage has no revision");
        }
        JsonNode updatedOutput = markSceneApproved(scenesStage.getCurrentRevision().outputJson(), sceneId);
        int nextRevisionNumber = scenesStage.getCurrentRevision().revisionNumber() + 1;
        scenesStage.setCurrentRevision(new StageRevision(nextRevisionNumber, updatedOutput, Instant.now()));
        boolean allApproved = areAllChapterScenesApproved(project, updatedOutput);
        scenesStage.setApproved(allApproved);
        scenesStage.setStatus(allApproved ? StageStatus.APPROVED : StageStatus.REVIEW);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public Object exportProjectJson(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        JsonNode snapshotJson = toSnapshotJson(project);
        return objectMapper.convertValue(snapshotJson, Object.class);
    }

    @Override
    public QuestProjectView importProjectJson(UUID projectId, Object snapshotJson) {
        if (snapshotJson == null) {
            throw new BadRequestException("snapshotJson is required");
        }
        JsonNode snapshotNode = objectMapper.valueToTree(snapshotJson);
        if (snapshotNode == null || snapshotNode.isNull()) {
            throw new BadRequestException("snapshotJson is required");
        }

        QuestProject project = getRequiredProject(projectId);
        JsonNode stagesNode = snapshotNode.path("stages");
        if (!stagesNode.isArray() || stagesNode.isEmpty()) {
            throw new BadRequestException("snapshotJson.stages must be a non-empty array");
        }

        Map<StageType, ImportedStage> importedStagesByType = new EnumMap<>(StageType.class);
        for (JsonNode stageNode : stagesNode) {
            StageType type = parseStageType(stageNode.path("type").asText(""));
            JsonNode outputJson = stageNode.path("outputJson");
            if (outputJson.isMissingNode() || outputJson.isNull()) {
                outputJson = objectMapper.createObjectNode();
            }
            StageStatus importedStatus = parseStageStatus(stageNode.path("status").asText("APPROVED"));
            boolean importedApproved = stageNode.path("approved").asBoolean(importedStatus == StageStatus.APPROVED);
            importedStagesByType.put(type, new ImportedStage(outputJson, importedStatus, importedApproved));
        }

        if (!importedStagesByType.containsKey(StageType.QUEST_DESCRIPTION)
                || !importedStagesByType.containsKey(StageType.WORLD)
                || !importedStagesByType.containsKey(StageType.NPC)
                || !importedStagesByType.containsKey(StageType.FACTS)) {
            throw new BadRequestException("Import must include QUEST_DESCRIPTION, WORLD, NPC, FACTS stages");
        }

        for (QuestStage stage : project.getStages()) {
            ImportedStage importedStage = importedStagesByType.get(stage.getType());
            if (importedStage != null) {
                stage.setCurrentRevision(new StageRevision(1, importedStage.outputJson(), Instant.now()));
                stage.setApproved(importedStage.approved());
                stage.setStatus(importedStage.status());
                continue;
            }
            stage.setApproved(false);
            stage.setCurrentRevision(null);
            if (stage.getType() == StageType.QUEST_OUTLINE) {
                stage.setStatus(StageStatus.READY);
            } else {
                stage.setStatus(StageStatus.NOT_STARTED);
            }
        }

        projectRepository.save(project);
        return toView(project);
    }

    private record ImportedStage(JsonNode outputJson, StageStatus status, boolean approved) {
    }

    @Override
    public QuestProjectView generateStageStep(UUID projectId, StageType stageType, String step) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, stageType);
        if (stage.getStatus() != StageStatus.READY && stage.getStatus() != StageStatus.REVIEW) {
            throw new ConflictException("Stage is not ready for generation: " + stageType);
        }

        StageRunner runner = stageRunnerRegistry.find(stageType)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for " + stageType));
        if (!(runner instanceof StepStageRunner stepRunner)) {
            throw new ConflictException("Stage does not support step generation: " + stageType);
        }
        if (!stepRunner.steps().contains(step)) {
            throw new ConflictException("Unknown step for stage " + stageType + ": " + step);
        }

        StageStatus previousStatus = stage.getStatus();
        stage.setStatus(StageStatus.GENERATING);

        JsonNode currentOutput = stage.getCurrentRevision() == null ? null : stage.getCurrentRevision().outputJson();
        JsonNode output;
        try {
            output = stepRunner.generateStep(projectId, step, currentOutput);
        } catch (RuntimeException ex) {
            stage.setStatus(previousStatus);
            projectRepository.save(project);
            throw ex;
        }

        int nextRevisionNumber = stage.getCurrentRevision() == null
                ? 1
                : stage.getCurrentRevision().revisionNumber() + 1;
        stage.setCurrentRevision(new StageRevision(nextRevisionNumber, output, Instant.now()));
        stage.setApproved(false);
        stage.setStatus(StageStatus.REVIEW);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public String exportDsl(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage scenesStage = getRequiredStage(project, StageType.SCENES);
        if (scenesStage.getCurrentRevision() == null) {
            throw new ConflictException("DSL export requires generated SCENES stage");
        }

        JsonNode scenesRoot = scenesStage.getCurrentRevision().outputJson();
        JsonNode scenes = scenesRoot.path("scenes");
        if (!scenes.isArray() || scenes.isEmpty()) {
            throw new ConflictException("DSL export requires non-empty SCENES output");
        }

        String questId = toQuestId(project.getName());
        String title = project.getName() == null || project.getName().isBlank() ? "Generated Quest" : project.getName().trim();
        StringBuilder dsl = new StringBuilder();
        dsl.append("quest ").append(questId).append('\n');
        dsl.append("title \"").append(escape(title)).append("\"\n\n");

        for (JsonNode scene : scenes) {
            String sceneId = scene.path("sceneId").asText("");
            if (sceneId.isBlank()) {
                continue;
            }
            String sceneTitle = scene.path("title").asText(sceneId);
            String location = scene.path("location").asText("");
            List<String> participants = readStringArray(scene.path("participants"));
            JsonNode steps = scene.path("steps");
            if (!steps.isArray()) {
                continue;
            }

            for (JsonNode step : steps) {
                String stepId = step.path("id").asText("");
                if (stepId.isBlank()) {
                    continue;
                }
                String nodeId = toNodeId(sceneId, stepId);
                dsl.append("node ").append(nodeId).append('\n');
                dsl.append("title \"").append(escape(sceneTitle)).append("\"\n");
                if (!location.isBlank()) {
                    dsl.append("@location(").append(location).append(")\n");
                }
                if (!participants.isEmpty()) {
                    dsl.append("@participants(").append(String.join(",", participants)).append(")\n");
                }
                for (String requiredFact : readStringArray(step.path("requiredFacts"))) {
                    dsl.append("@if hasFact(\"").append(escape(requiredFact)).append("\")\n");
                }
                for (String revealedFact : readStringArray(step.path("revealedFacts"))) {
                    dsl.append("@reveal addFact(\"").append(escape(revealedFact)).append("\")\n");
                }
                String text = step.path("purpose").asText("");
                dsl.append("\"").append(escape(text)).append("\"\n");

                JsonNode actions = step.path("actions");
                if (actions.isArray()) {
                    for (JsonNode action : actions) {
                        String actionText = action.path("text").asText("");
                        String nextStep = action.path("nextStep").asText("");
                        dsl.append("> \"").append(escape(actionText)).append("\"\n");
                        if (nextStep == null || nextStep.isBlank()) {
                            dsl.append("@end\n");
                        } else {
                            dsl.append("-> ").append(toNodeId(sceneId, nextStep)).append('\n');
                        }
                    }
                }
                dsl.append('\n');
            }
        }

        return dsl.toString();
    }

    @Override
    public String convertDsl(String projectName, JsonNode questGraphJson) {
        throw new NotImplementedException("DSL conversion is disabled for JSON-only pipeline");
    }

    private QuestProject getRequiredProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    private QuestStage getRequiredStage(QuestProject project, StageType type) {
        return project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
    }

    private String nextStep(StepStageRunner stepRunner, QuestProject project, StageType stageType) {
        QuestStage stage = getRequiredStage(project, stageType);
        JsonNode output = stage.getCurrentRevision() == null ? null : stage.getCurrentRevision().outputJson();
        for (String step : stepRunner.steps()) {
            if (!stepRunner.isStepCompleted(step, output)) {
                return step;
            }
        }
        return stepRunner.steps().get(stepRunner.steps().size() - 1);
    }

    private StageType parseStageType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new BadRequestException("stage.type is required");
        }
        if ("MYSTERY".equals(rawType.trim())) {
            return StageType.QUEST_DESCRIPTION;
        }
        try {
            return StageType.valueOf(rawType.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown stage.type: " + rawType);
        }
    }

    private StageStatus parseStageStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return StageStatus.APPROVED;
        }
        try {
            return StageStatus.valueOf(rawStatus.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown stage.status: " + rawStatus);
        }
    }

    private JsonNode toSnapshotJson(QuestProject project) {
        var root = objectMapper.createObjectNode();
        root.put("name", project.getName());
        root.put("questStyle", project.getQuestStyle());
        root.put("status", project.getStatus().name());

        var stagesArray = objectMapper.createArrayNode();
        for (QuestStage stage : project.getStages()) {
            var stageNode = objectMapper.createObjectNode();
            stageNode.put("type", stage.getType().name());
            stageNode.put("status", stage.getStatus().name());
            stageNode.put("approved", stage.isApproved());
            stageNode.set(
                    "outputJson",
                    stage.getCurrentRevision() == null
                            ? objectMapper.createObjectNode()
                            : stage.getCurrentRevision().outputJson()
            );
            stagesArray.add(stageNode);
        }
        root.set("stages", stagesArray);
        return root;
    }

    private QuestProjectView toView(QuestProject project) {
        List<QuestStageView> stages = project.getStages().stream()
                .map(stage -> new QuestStageView(
                        stage.getType().name(),
                        toStageDisplayName(stage.getType()),
                        stage.getStatus().name(),
                        stage.isApproved(),
                        toRevisionView(stage.getCurrentRevision())
                ))
                .toList();

        return new QuestProjectView(
                project.getId().toString(),
                project.getName(),
                project.getQuestStyle(),
                project.getStatus().name(),
                stages
        );
    }

    private StageRevisionView toRevisionView(StageRevision revision) {
        if (revision == null) {
            return null;
        }
        Object outputJson = objectMapper.convertValue(revision.outputJson(), Object.class);
        return new StageRevisionView(
                revision.revisionNumber(),
                outputJson,
                revision.createdAt()
        );
    }

    private JsonNode markChapterReview(JsonNode stageOutput, String chapterId) {
        var root = objectMapper.createObjectNode();
        var chapters = objectMapper.createArrayNode();
        JsonNode existing = stageOutput == null ? null : stageOutput.path("chapters");
        if (existing != null && existing.isArray()) {
            for (JsonNode chapter : existing) {
                String id = chapter.path("chapterId").asText("");
                var chapterNode = chapter.deepCopy();
                if (id.equalsIgnoreCase(chapterId)) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) chapterNode).put("status", StageStatus.REVIEW.name());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) chapterNode).put("approved", false);
                }
                chapters.add(chapterNode);
            }
        }
        root.set("chapters", chapters);
        return root;
    }

    private JsonNode markChapterApproved(JsonNode stageOutput, String chapterId) {
        if (stageOutput == null || !stageOutput.path("chapters").isArray()) {
            throw new ConflictException("No generated chapters to approve");
        }
        var root = objectMapper.createObjectNode();
        var chapters = objectMapper.createArrayNode();
        boolean found = false;
        for (JsonNode chapter : stageOutput.path("chapters")) {
            String id = chapter.path("chapterId").asText("");
            var chapterNode = chapter.deepCopy();
            if (id.equalsIgnoreCase(chapterId)) {
                found = true;
                ((com.fasterxml.jackson.databind.node.ObjectNode) chapterNode).put("status", StageStatus.APPROVED.name());
                ((com.fasterxml.jackson.databind.node.ObjectNode) chapterNode).put("approved", true);
            }
            chapters.add(chapterNode);
        }
        if (!found) {
            throw new NotFoundException("Generated chapter not found: " + chapterId);
        }
        root.set("chapters", chapters);
        return root;
    }

    private boolean areAllOutlineChaptersApproved(QuestProject project, JsonNode chaptersOutput) {
        QuestStage outlineStage = getRequiredStage(project, StageType.QUEST_OUTLINE);
        JsonNode outlineChapters = outlineStage.getCurrentRevision() == null ? null : outlineStage.getCurrentRevision().outputJson().path("chapters");
        if (outlineChapters == null || !outlineChapters.isArray() || outlineChapters.isEmpty()) {
            return false;
        }
        Set<String> approvedChapterIds = new HashSet<>();
        JsonNode generatedChapters = chaptersOutput.path("chapters");
        if (generatedChapters.isArray()) {
            for (JsonNode chapter : generatedChapters) {
                if (chapter.path("approved").asBoolean(false)) {
                    approvedChapterIds.add(chapter.path("chapterId").asText("").toUpperCase());
                }
            }
        }
        for (JsonNode chapter : outlineChapters) {
            String id = chapter.path("id").asText("").toUpperCase();
            if (!id.isBlank() && !approvedChapterIds.contains(id)) {
                return false;
            }
        }
        return true;
    }

    private JsonNode markSceneReview(JsonNode stageOutput, String sceneId) {
        var root = objectMapper.createObjectNode();
        var scenes = objectMapper.createArrayNode();
        JsonNode existing = stageOutput == null ? null : stageOutput.path("scenes");
        if (existing != null && existing.isArray()) {
            for (JsonNode scene : existing) {
                String id = scene.path("sceneId").asText("");
                var sceneNode = scene.deepCopy();
                if (id.equalsIgnoreCase(sceneId)) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) sceneNode).put("status", StageStatus.REVIEW.name());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) sceneNode).put("approved", false);
                }
                scenes.add(sceneNode);
            }
        }
        root.set("scenes", scenes);
        return root;
    }

    private JsonNode markSceneApproved(JsonNode stageOutput, String sceneId) {
        if (stageOutput == null || !stageOutput.path("scenes").isArray()) {
            throw new ConflictException("No generated scenes to approve");
        }
        var root = objectMapper.createObjectNode();
        var scenes = objectMapper.createArrayNode();
        boolean found = false;
        for (JsonNode scene : stageOutput.path("scenes")) {
            String id = scene.path("sceneId").asText("");
            var sceneNode = scene.deepCopy();
            if (id.equalsIgnoreCase(sceneId)) {
                found = true;
                ((com.fasterxml.jackson.databind.node.ObjectNode) sceneNode).put("status", StageStatus.APPROVED.name());
                ((com.fasterxml.jackson.databind.node.ObjectNode) sceneNode).put("approved", true);
            }
            scenes.add(sceneNode);
        }
        if (!found) {
            throw new NotFoundException("Generated scene not found: " + sceneId);
        }
        root.set("scenes", scenes);
        return root;
    }

    private boolean areAllChapterScenesApproved(QuestProject project, JsonNode scenesOutput) {
        QuestStage chaptersStage = getRequiredStage(project, StageType.CHAPTERS);
        JsonNode chapterRuns = chaptersStage.getCurrentRevision() == null ? null : chaptersStage.getCurrentRevision().outputJson().path("chapters");
        if (chapterRuns == null || !chapterRuns.isArray() || chapterRuns.isEmpty()) {
            return false;
        }

        Set<String> requiredSceneIds = new HashSet<>();
        for (JsonNode chapterRun : chapterRuns) {
            JsonNode scenes = chapterRun.path("scenes");
            if (scenes.isArray()) {
                for (JsonNode scene : scenes) {
                    String id = scene.path("id").asText("").toUpperCase();
                    if (!id.isBlank()) {
                        requiredSceneIds.add(id);
                    }
                }
            }
        }
        if (requiredSceneIds.isEmpty()) {
            return false;
        }

        Set<String> approvedSceneIds = new HashSet<>();
        JsonNode generatedScenes = scenesOutput.path("scenes");
        if (generatedScenes.isArray()) {
            for (JsonNode scene : generatedScenes) {
                if (scene.path("approved").asBoolean(false)) {
                    String id = scene.path("sceneId").asText("").toUpperCase();
                    if (!id.isBlank()) {
                        approvedSceneIds.add(id);
                    }
                }
            }
        }
        return approvedSceneIds.containsAll(requiredSceneIds);
    }

    private List<String> readStringArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode == null || !arrayNode.isArray()) {
            return result;
        }
        for (JsonNode item : arrayNode) {
            String value = item.asText("");
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private String toNodeId(String sceneId, String stepId) {
        return (sceneId + "_" + stepId).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_");
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

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toStageDisplayName(StageType stageType) {
        return switch (stageType) {
            case QUEST_DESCRIPTION -> "Quest Description";
            case WORLD -> "World";
            case NPC -> "NPC";
            case FACTS -> "Facts";
            case QUEST_OUTLINE -> "Quest Outline";
            case CHAPTERS -> "Chapters";
            case SCENES -> "Scenes";
            case QUEST_GRAPH -> "Quest Graph";
        };
    }
}
