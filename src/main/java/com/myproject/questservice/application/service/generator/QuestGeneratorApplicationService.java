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
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.QuestStage;
import com.myproject.questservice.domain.generator.StageRevision;
import com.myproject.questservice.domain.generator.StageStatus;
import com.myproject.questservice.domain.generator.StageType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QuestGeneratorApplicationService implements QuestGeneratorUseCase {

    private final ProjectRepository projectRepository;
    private final StageRunnerRegistry stageRunnerRegistry;
    private final ObjectMapper objectMapper;
    private final FlowDslExportService flowDslExportService;

    public QuestGeneratorApplicationService(
            ProjectRepository projectRepository,
            StageRunnerRegistry stageRunnerRegistry,
            ObjectMapper objectMapper,
            FlowDslExportService flowDslExportService
    ) {
        this.projectRepository = projectRepository;
        this.stageRunnerRegistry = stageRunnerRegistry;
        this.objectMapper = objectMapper;
        this.flowDslExportService = flowDslExportService;
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
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, stageType);
        if (stage.getStatus() != StageStatus.READY && stage.getStatus() != StageStatus.REVIEW) {
            throw new ConflictException("Stage is not ready for generation: " + stageType);
        }
        StageStatus previousStatus = stage.getStatus();

        StageRunner runner = stageRunnerRegistry.find(stageType)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for " + stageType));

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

        if (stageType != StageType.QUEST_GRAPH) {
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
    public String exportDsl(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage graphStage = getRequiredStage(project, StageType.QUEST_GRAPH);
        if (graphStage.getStatus() != StageStatus.APPROVED || graphStage.getCurrentRevision() == null) {
            throw new ConflictException("DSL export requires APPROVED QUEST_GRAPH stage");
        }
        return flowDslExportService.toDsl(project.getName(), graphStage.getCurrentRevision().outputJson());
    }

    private QuestProject getRequiredProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    private QuestStage getRequiredStage(QuestProject project, StageType type) {
        return project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
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
