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

        Map<StageType, JsonNode> importedJsonByType = new EnumMap<>(StageType.class);
        for (JsonNode stageNode : stagesNode) {
            StageType type = parseStageType(stageNode.path("type").asText(""));
            if (type != StageType.MYSTERY && type != StageType.WORLD && type != StageType.NPC && type != StageType.FACTS) {
                continue;
            }
            JsonNode outputJson = stageNode.path("outputJson");
            if (outputJson.isMissingNode() || outputJson.isNull()) {
                outputJson = objectMapper.createObjectNode();
            }
            importedJsonByType.put(type, outputJson);
        }

        if (!importedJsonByType.containsKey(StageType.MYSTERY)
                || !importedJsonByType.containsKey(StageType.WORLD)
                || !importedJsonByType.containsKey(StageType.NPC)
                || !importedJsonByType.containsKey(StageType.FACTS)) {
            throw new BadRequestException("Import must include MYSTERY, WORLD, NPC, FACTS stages");
        }

        for (QuestStage stage : project.getStages()) {
            JsonNode importedOutput = importedJsonByType.get(stage.getType());
            if (importedOutput != null) {
                stage.setCurrentRevision(new StageRevision(1, importedOutput, Instant.now()));
                stage.setApproved(true);
                stage.setStatus(StageStatus.APPROVED);
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
        throw new NotImplementedException("DSL export is disabled for JSON-only pipeline");
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
        try {
            return StageType.valueOf(rawType.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown stage.type: " + rawType);
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
}
