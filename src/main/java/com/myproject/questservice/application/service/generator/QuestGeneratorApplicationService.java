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
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.QuestStage;
import com.myproject.questservice.domain.generator.StageRevision;
import com.myproject.questservice.domain.generator.StageStatus;
import com.myproject.questservice.domain.generator.StageType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

        if (stageType != StageType.QUEST_OUTLINE) {
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
    public Object exportProjectJson(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        JsonNode snapshotJson = toSnapshotJson(project);
        return objectMapper.convertValue(snapshotJson, Object.class);
    }

    @Override
    public QuestProjectView importProjectJson(UUID projectId, JsonNode snapshotJson) {
        if (snapshotJson == null || snapshotJson.isNull()) {
            throw new BadRequestException("snapshotJson is required");
        }

        QuestProject project = getRequiredProject(projectId);
        JsonNode stagesNode = snapshotJson.path("stages");
        if (!stagesNode.isArray() || stagesNode.isEmpty()) {
            throw new BadRequestException("snapshotJson.stages must be a non-empty array");
        }

        List<QuestStage> importedStages = new ArrayList<>();
        for (JsonNode stageNode : stagesNode) {
            StageType type = parseStageType(stageNode.path("type").asText(""));
            JsonNode outputJson = stageNode.path("outputJson");
            if (outputJson.isMissingNode() || outputJson.isNull()) {
                outputJson = objectMapper.createObjectNode();
            }

            StageRevision revision = new StageRevision(
                    1,
                    outputJson,
                    Instant.now()
            );
            // REVIEW keeps generation and approval actions available in UI flows.
            importedStages.add(new QuestStage(type, StageStatus.REVIEW, false, revision));
        }

        project.setStages(importedStages);
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
}
