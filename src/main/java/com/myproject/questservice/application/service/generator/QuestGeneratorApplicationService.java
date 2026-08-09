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
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.application.service.generator.stage.StageRunner;
import com.myproject.questservice.application.service.generator.stage.StageRunnerRegistry;
import com.myproject.questservice.application.service.generator.stage.StepStageRunner;
import com.myproject.questservice.application.service.generator.stage.ChapterStageRunner;
import com.myproject.questservice.application.service.generator.stage.SceneStageRunner;
import com.myproject.questservice.application.service.generator.stage.AchievementSceneStageRunner;
import com.myproject.questservice.application.service.generator.stage.KnowledgeChainWayStageRunner;
import com.myproject.questservice.application.service.generator.stage.ActionQuestStageRunner;
import com.myproject.questservice.application.service.generator.stage.PromptPreviewStageRunner;
import com.myproject.questservice.application.service.generator.stage.StagePromptPreview;
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.QuestStage;
import com.myproject.questservice.domain.generator.StageRevision;
import com.myproject.questservice.domain.generator.StageStatus;
import com.myproject.questservice.domain.generator.StageType;
import com.myproject.questservice.domain.generator.NodeWorkspace;
import com.myproject.questservice.domain.generator.WorkspaceAction;
import com.myproject.questservice.domain.generator.WorkspaceAiRequestLog;
import com.myproject.questservice.domain.generator.WorkspaceExpansionSuggestion;
import com.myproject.questservice.domain.generator.WorkspaceNode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.UUID;

@Service
public class QuestGeneratorApplicationService implements QuestGeneratorUseCase {
    private static final String STORY_BIBLE_SPIKE_CONTEXT = """
            Story Bible context (hardcoded spike):
            - title: Тень старого маяка
            - logline: Смотритель маяка пропал три ночи назад; его записи намекают на охоту, и теперь угроза переходит к игроку.
            - protagonist_goal: Найти смотрителя маяка и выяснить, что произошло.
            - true_stakes: Маяк скрывает контрабандный груз; смотритель инсценировал исчезновение, чтобы уйти от долгов.
            - opposing_force: Местный рыбак, который выглядит союзником, но мешает раскрытию правды.
            - key_facts:
              1) Маяк не работал последние три ночи.
              2) В судовом журнале есть вырванные страницы.
              3) Рыбак появляется каждый раз, когда игрок находит новую улику.
            - next_unrevealed_twist: Помогающий игроку рыбак на самом деле работает против него и заметает следы.
            - tone: Мрачный прибрежный триллер с элементами тайны, без сверхъестественного.
            Use this context to keep tension and push story progression.
            """;

    private final ProjectRepository projectRepository;
    private final StageRunnerRegistry stageRunnerRegistry;
    private final ObjectMapper objectMapper;
    private final AiClient aiClient;

    public QuestGeneratorApplicationService(
            ProjectRepository projectRepository,
            StageRunnerRegistry stageRunnerRegistry,
            ObjectMapper objectMapper,
            AiClient aiClient
    ) {
        this.projectRepository = projectRepository;
        this.stageRunnerRegistry = stageRunnerRegistry;
        this.objectMapper = objectMapper;
        this.aiClient = aiClient;
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
    public QuestProjectView generateStage(UUID projectId, StageType stageType, String systemPromptOverride, String userPromptOverride) {
        if (stageType == StageType.KNOWLEDGE_CHAIN) {
            throw new ConflictException("KNOWLEDGE_CHAIN supports only per-way generation: /stages/KNOWLEDGE_CHAIN/ways/{wayId}/generate");
        }
        if (stageType == StageType.ACTION_QUESTS) {
            throw new ConflictException("ACTION_QUESTS supports only per-way generation: /stages/ACTION_QUESTS/ways/{wayId}/generate");
        }
        StageRunner runner = stageRunnerRegistry.find(stageType)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for " + stageType));
        if (runner instanceof StepStageRunner stepRunner) {
            return generateStageStep(projectId, stageType, nextStep(stepRunner, getRequiredProject(projectId), stageType));
        }

        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, stageType);
        unlockStageIfEligible(project, stageType, stage);
        if (stage.getStatus() != StageStatus.READY
                && stage.getStatus() != StageStatus.REVIEW
                && stage.getStatus() != StageStatus.APPROVED) {
            throw new ConflictException("Stage is not ready for generation: " + stageType);
        }
        StageStatus previousStatus = stage.getStatus();

        stage.setStatus(StageStatus.GENERATING);
        JsonNode output;
        try {
            output = generateStageOutput(projectId, stageType, runner, systemPromptOverride, userPromptOverride);
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

    private JsonNode generateStageOutput(
            UUID projectId,
            StageType stageType,
            StageRunner runner,
            String systemPromptOverride,
            String userPromptOverride
    ) {
        boolean hasOverride = (systemPromptOverride != null && !systemPromptOverride.trim().isBlank())
                || (userPromptOverride != null && !userPromptOverride.trim().isBlank());
        if (!hasOverride) {
            return runner.generate(projectId);
        }
        // Deterministic validator should stay deterministic.
        if (stageType == StageType.LOGIC_VALIDATION) {
            return runner.generate(projectId);
        }
        if (!(runner instanceof PromptPreviewStageRunner previewRunner)) {
            throw new ConflictException("Stage does not support prompt overrides: " + stageType);
        }
        StagePromptPreview preview = previewRunner.previewPrompt(projectId);
        String systemPrompt = nonBlankOrDefault(systemPromptOverride, preview.systemPrompt());
        String userPrompt = nonBlankOrDefault(userPromptOverride, preview.userPrompt());
        return aiClient.generate(systemPrompt, userPrompt);
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
    public StagePromptPreview previewStagePrompt(UUID projectId, StageType stageType) {
        StageRunner runner = stageRunnerRegistry.find(stageType)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for " + stageType));
        if (!(runner instanceof PromptPreviewStageRunner previewRunner)) {
            throw new ConflictException("Stage does not support prompt preview: " + stageType);
        }
        return previewRunner.previewPrompt(projectId);
    }

    @Override
    public StagePromptPreview previewKnowledgeChainPrompt(UUID projectId, String wayId) {
        StageRunner runner = stageRunnerRegistry.find(StageType.KNOWLEDGE_CHAIN)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for KNOWLEDGE_CHAIN"));
        if (!(runner instanceof KnowledgeChainWayStageRunner knowledgeChainRunner)) {
            throw new ConflictException("KNOWLEDGE_CHAIN runner does not support per-way preview");
        }
        return knowledgeChainRunner.previewKnowledgeChainPrompt(projectId, wayId);
    }

    @Override
    public StagePromptPreview previewAchievementScenePrompt(UUID projectId, String wayId) {
        StageRunner runner = stageRunnerRegistry.find(StageType.ACHIEVEMENT_SCENES)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for ACHIEVEMENT_SCENES"));
        if (!(runner instanceof AchievementSceneStageRunner achievementSceneRunner)) {
            throw new ConflictException("ACHIEVEMENT_SCENES runner does not support per-way preview");
        }
        return achievementSceneRunner.previewAchievementPrompt(projectId, wayId);
    }

    @Override
    public StagePromptPreview previewActionQuestPrompt(UUID projectId, String wayId) {
        StageRunner runner = stageRunnerRegistry.find(StageType.ACTION_QUESTS)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for ACTION_QUESTS"));
        if (!(runner instanceof ActionQuestStageRunner actionQuestRunner)) {
            throw new ConflictException("ACTION_QUESTS runner does not support per-way preview");
        }
        return actionQuestRunner.previewActionQuestPrompt(projectId, wayId);
    }

    @Override
    public StagePromptPreview previewActionResolutionPrompt(UUID projectId, String wayId, String sceneId, String actionId) {
        StageRunner runner = stageRunnerRegistry.find(StageType.ACTION_QUESTS)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for ACTION_QUESTS"));
        if (!(runner instanceof ActionQuestStageRunner actionQuestRunner)) {
            throw new ConflictException("ACTION_QUESTS runner does not support action-level preview");
        }
        return actionQuestRunner.previewActionResolutionPrompt(projectId, wayId, sceneId, actionId);
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
    public QuestProjectView generateAchievementScene(UUID projectId, String wayId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, StageType.ACHIEVEMENT_SCENES);
        if (stage.getStatus() == StageStatus.NOT_STARTED) {
            if (hasAtLeastOneApprovedKnowledgeChain(project)) {
                stage.setStatus(StageStatus.READY);
            }
        }
        if (stage.getStatus() != StageStatus.READY && stage.getStatus() != StageStatus.REVIEW && stage.getStatus() != StageStatus.APPROVED) {
            throw new ConflictException("ACHIEVEMENT_SCENES stage is not ready for achievement generation");
        }

        StageRunner runner = stageRunnerRegistry.find(StageType.ACHIEVEMENT_SCENES)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for ACHIEVEMENT_SCENES"));
        if (!(runner instanceof AchievementSceneStageRunner achievementSceneRunner)) {
            throw new ConflictException("ACHIEVEMENT_SCENES runner does not support achievement generation");
        }

        StageStatus previousStatus = stage.getStatus();
        stage.setStatus(StageStatus.GENERATING);
        JsonNode currentOutput = stage.getCurrentRevision() == null ? null : stage.getCurrentRevision().outputJson();
        JsonNode output;
        try {
            output = achievementSceneRunner.generateAchievement(projectId, wayId, currentOutput);
        } catch (RuntimeException ex) {
            stage.setStatus(previousStatus);
            projectRepository.save(project);
            throw ex;
        }

        int nextRevisionNumber = stage.getCurrentRevision() == null
                ? 1
                : stage.getCurrentRevision().revisionNumber() + 1;
        stage.setCurrentRevision(new StageRevision(nextRevisionNumber, markAchievementSceneReview(output, wayId), Instant.now()));
        stage.setApproved(false);
        stage.setStatus(StageStatus.REVIEW);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView approveAchievementScene(UUID projectId, String wayId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, StageType.ACHIEVEMENT_SCENES);
        if (stage.getCurrentRevision() == null) {
            throw new ConflictException("ACHIEVEMENT_SCENES stage has no revision");
        }
        JsonNode updatedOutput = markAchievementSceneApproved(stage.getCurrentRevision().outputJson(), wayId);
        int nextRevisionNumber = stage.getCurrentRevision().revisionNumber() + 1;
        stage.setCurrentRevision(new StageRevision(nextRevisionNumber, updatedOutput, Instant.now()));
        boolean allApproved = areAllAchievementScenesApproved(project, updatedOutput);
        stage.setApproved(allApproved);
        stage.setStatus(allApproved ? StageStatus.APPROVED : StageStatus.REVIEW);

        project.nextStage(StageType.ACHIEVEMENT_SCENES).ifPresent(nextStage -> {
            if (nextStage.getType() == StageType.ACTION_QUESTS && nextStage.getStatus() == StageStatus.NOT_STARTED) {
                nextStage.setStatus(StageStatus.READY);
            }
        });

        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView generateKnowledgeChain(UUID projectId, String wayId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, StageType.KNOWLEDGE_CHAIN);
        if (stage.getStatus() == StageStatus.NOT_STARTED) {
            stage.setStatus(StageStatus.READY);
        }
        if (stage.getStatus() != StageStatus.READY && stage.getStatus() != StageStatus.REVIEW && stage.getStatus() != StageStatus.APPROVED) {
            throw new ConflictException("KNOWLEDGE_CHAIN stage is not ready for per-way generation");
        }

        StageRunner runner = stageRunnerRegistry.find(StageType.KNOWLEDGE_CHAIN)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for KNOWLEDGE_CHAIN"));
        if (!(runner instanceof KnowledgeChainWayStageRunner knowledgeChainRunner)) {
            throw new ConflictException("KNOWLEDGE_CHAIN runner does not support per-way generation");
        }

        StageStatus previousStatus = stage.getStatus();
        stage.setStatus(StageStatus.GENERATING);
        JsonNode currentOutput = stage.getCurrentRevision() == null ? null : stage.getCurrentRevision().outputJson();
        JsonNode output;
        try {
            output = knowledgeChainRunner.generateKnowledgeChain(projectId, wayId, currentOutput);
        } catch (RuntimeException ex) {
            stage.setStatus(previousStatus);
            projectRepository.save(project);
            throw ex;
        }

        int nextRevisionNumber = stage.getCurrentRevision() == null
                ? 1
                : stage.getCurrentRevision().revisionNumber() + 1;
        stage.setCurrentRevision(new StageRevision(nextRevisionNumber, markKnowledgeChainReview(output, wayId), Instant.now()));
        stage.setApproved(false);
        stage.setStatus(StageStatus.REVIEW);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView approveKnowledgeChain(UUID projectId, String wayId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, StageType.KNOWLEDGE_CHAIN);
        if (stage.getCurrentRevision() == null) {
            throw new ConflictException("KNOWLEDGE_CHAIN stage has no revision");
        }
        JsonNode updatedOutput = markKnowledgeChainApproved(stage.getCurrentRevision().outputJson(), wayId);
        int nextRevisionNumber = stage.getCurrentRevision().revisionNumber() + 1;
        stage.setCurrentRevision(new StageRevision(nextRevisionNumber, updatedOutput, Instant.now()));
        boolean allApproved = areAllKnowledgeChainsApproved(project, updatedOutput);
        stage.setApproved(allApproved);
        stage.setStatus(allApproved ? StageStatus.APPROVED : StageStatus.REVIEW);

        project.nextStage(StageType.KNOWLEDGE_CHAIN).ifPresent(nextStage -> {
            if (nextStage.getType() == StageType.ACHIEVEMENT_SCENES && nextStage.getStatus() == StageStatus.NOT_STARTED) {
                nextStage.setStatus(StageStatus.READY);
            }
        });

        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView generateActionResolution(UUID projectId, String wayId, String sceneId, String actionId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, StageType.ACTION_QUESTS);
        if (stage.getStatus() == StageStatus.NOT_STARTED) {
            if (hasAtLeastOneApprovedAchievementScene(project)) {
                stage.setStatus(StageStatus.READY);
            }
        }
        if (stage.getStatus() != StageStatus.READY && stage.getStatus() != StageStatus.REVIEW && stage.getStatus() != StageStatus.APPROVED) {
            throw new ConflictException("ACTION_QUESTS stage is not ready for action resolution generation");
        }

        StageRunner runner = stageRunnerRegistry.find(StageType.ACTION_QUESTS)
                .orElseThrow(() -> new NotImplementedException("StageRunner is not implemented for ACTION_QUESTS"));
        if (!(runner instanceof ActionQuestStageRunner actionQuestRunner)) {
            throw new ConflictException("ACTION_QUESTS runner does not support action-level generation");
        }

        StageStatus previousStatus = stage.getStatus();
        stage.setStatus(StageStatus.GENERATING);
        JsonNode currentOutput = stage.getCurrentRevision() == null ? null : stage.getCurrentRevision().outputJson();
        JsonNode output;
        try {
            output = actionQuestRunner.generateActionResolution(projectId, wayId, sceneId, actionId, currentOutput);
        } catch (RuntimeException ex) {
            stage.setStatus(previousStatus);
            projectRepository.save(project);
            throw ex;
        }

        int nextRevisionNumber = stage.getCurrentRevision() == null
                ? 1
                : stage.getCurrentRevision().revisionNumber() + 1;
        stage.setCurrentRevision(new StageRevision(nextRevisionNumber, markActionResolutionReview(output, wayId, sceneId, actionId), Instant.now()));
        stage.setApproved(false);
        stage.setStatus(StageStatus.REVIEW);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView approveActionResolution(UUID projectId, String wayId, String sceneId, String actionId) {
        QuestProject project = getRequiredProject(projectId);
        QuestStage stage = getRequiredStage(project, StageType.ACTION_QUESTS);
        if (stage.getCurrentRevision() == null) {
            throw new ConflictException("ACTION_QUESTS stage has no revision");
        }
        JsonNode updatedOutput = markActionResolutionApproved(project, stage.getCurrentRevision().outputJson(), wayId, sceneId, actionId);
        int nextRevisionNumber = stage.getCurrentRevision().revisionNumber() + 1;
        stage.setCurrentRevision(new StageRevision(nextRevisionNumber, updatedOutput, Instant.now()));
        boolean allApproved = areAllActionQuestsApproved(project, updatedOutput);
        stage.setApproved(allApproved);
        stage.setStatus(allApproved ? StageStatus.APPROVED : StageStatus.REVIEW);
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

        String importedName = snapshotNode.path("name").asText("").trim();
        if (!importedName.isBlank()) {
            project.setName(importedName);
        }
        String importedQuestStyle = snapshotNode.path("questStyle").asText("").trim();
        if (!importedQuestStyle.isBlank()) {
            project.setQuestStyle(importedQuestStyle);
        }
        String importedProjectStatus = snapshotNode.path("status").asText("").trim();
        if (!importedProjectStatus.isBlank()) {
            try {
                project.setStatus(com.myproject.questservice.domain.generator.QuestProjectStatus.valueOf(importedProjectStatus));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Unknown project.status: " + importedProjectStatus);
            }
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

        if (!importedStagesByType.containsKey(StageType.QUEST_CONSTRAINTS)
                && importedStagesByType.containsKey(StageType.QUEST_DESCRIPTION)) {
            importedStagesByType.put(
                    StageType.QUEST_CONSTRAINTS,
                    new ImportedStage(objectMapper.createObjectNode(), StageStatus.APPROVED, true)
            );
        }
        if (!importedStagesByType.containsKey(StageType.ACHIEVEMENT_RESOURCE_ANALYSIS)
                && importedStagesByType.containsKey(StageType.QUEST_DESCRIPTION)
                && importedStagesByType.containsKey(StageType.QUEST_CONSTRAINTS)) {
            importedStagesByType.put(
                    StageType.ACHIEVEMENT_RESOURCE_ANALYSIS,
                    new ImportedStage(objectMapper.createObjectNode(), StageStatus.APPROVED, true)
            );
        }
        if (!importedStagesByType.containsKey(StageType.ACHIEVEMENT_INFORMATION_FLOW)
                && importedStagesByType.containsKey(StageType.ACHIEVEMENT_REALISATION)) {
            importedStagesByType.put(
                    StageType.ACHIEVEMENT_INFORMATION_FLOW,
                    new ImportedStage(objectMapper.createObjectNode(), StageStatus.APPROVED, true)
            );
        }
        if (!importedStagesByType.containsKey(StageType.KNOWLEDGE_CHAIN)
                && importedStagesByType.containsKey(StageType.ACHIEVEMENT_INFORMATION_FLOW)) {
            importedStagesByType.put(
                    StageType.KNOWLEDGE_CHAIN,
                    new ImportedStage(objectMapper.createObjectNode(), StageStatus.APPROVED, true)
            );
        }

        if (!importedStagesByType.containsKey(StageType.QUEST_DESCRIPTION)
                || !importedStagesByType.containsKey(StageType.QUEST_CONSTRAINTS)
                || !importedStagesByType.containsKey(StageType.ACHIEVEMENT_RESOURCE_ANALYSIS)
                || !importedStagesByType.containsKey(StageType.WORLD)
                || !importedStagesByType.containsKey(StageType.ACHIEVEMENT_REALISATION)) {
            throw new BadRequestException("Import must include QUEST_DESCRIPTION, QUEST_CONSTRAINTS, ACHIEVEMENT_RESOURCE_ANALYSIS, WORLD, ACHIEVEMENT_REALISATION stages");
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
            stage.setStatus(StageStatus.NOT_STARTED);
        }

        JsonNode workspaceNode = snapshotNode.path("nodeWorkspace");
        if (workspaceNode != null && !workspaceNode.isMissingNode() && !workspaceNode.isNull()) {
            NodeWorkspace importedWorkspace = objectMapper.convertValue(workspaceNode, NodeWorkspace.class);
            project.setNodeWorkspace(importedWorkspace == null ? NodeWorkspace.createEmpty() : importedWorkspace);
        } else {
            project.setNodeWorkspace(NodeWorkspace.createEmpty());
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
        unlockStageIfEligible(project, stageType, stage);
        if (stage.getStatus() != StageStatus.READY
                && stage.getStatus() != StageStatus.REVIEW
                && stage.getStatus() != StageStatus.APPROVED) {
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

    @Override
    public QuestProjectView createWorkspaceNode(UUID projectId, String sourceNodeId, String sourceActionId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        String nodeId = "N" + workspace.getNextNodeIndex();
        workspace.setNextNodeIndex(workspace.getNextNodeIndex() + 1);

        WorkspaceNode node = WorkspaceNode.create(
                nodeId,
                normalizeNullable(sourceNodeId),
                normalizeNullable(sourceActionId)
        );
        workspace.getNodes().add(node);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView getWorkspaceNode(UUID projectId, String nodeId) {
        QuestProject project = getRequiredProject(projectId);
        findWorkspaceNode(requiredWorkspace(project), nodeId);
        return toView(project);
    }

    @Override
    public QuestProjectView listWorkspaceNodes(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        requiredWorkspace(project);
        return toView(project);
    }

    @Override
    public QuestProjectView updateWorkspaceNodeDescription(UUID projectId, String nodeId, String actionDescription, String stateDescription) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);
        String action = actionDescription == null ? "" : actionDescription.trim();
        String state = stateDescription == null ? "" : stateDescription.trim();
        node.setActionDescription(action);
        node.setStateDescription(state);
        node.setDescription(joinNodeDescription(action, state));
        node.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView addWorkspaceNodeAction(UUID projectId, String nodeId, String text) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);

        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isBlank()) {
            throw new BadRequestException("Action text is required");
        }
        String actionId = "A" + workspace.getNextActionIndex();
        workspace.setNextActionIndex(workspace.getNextActionIndex() + 1);
        node.getActions().add(new WorkspaceAction(actionId, normalizedText));
        node.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView updateWorkspaceNodeAction(UUID projectId, String nodeId, String actionId, String text) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);
        WorkspaceAction action = findWorkspaceAction(node, actionId);

        String normalizedText = text == null ? "" : text.trim();
        if (normalizedText.isBlank()) {
            throw new BadRequestException("Action text is required");
        }
        action.setText(normalizedText);
        node.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView deleteWorkspaceNodeAction(UUID projectId, String nodeId, String actionId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);
        findWorkspaceAction(node, actionId);

        node.getActions().removeIf(action -> action.getId() != null && action.getId().equalsIgnoreCase(actionId));
        node.setUpdatedAt(Instant.now());

        List<WorkspaceNode> branchRoots = workspace.getNodes().stream()
                .filter(candidate -> candidate.getSourceNodeId() != null && candidate.getSourceActionId() != null)
                .filter(candidate -> node.getId().equalsIgnoreCase(candidate.getSourceNodeId()) && actionId.equalsIgnoreCase(candidate.getSourceActionId()))
                .toList();
        Set<String> idsToDelete = new HashSet<>();
        for (WorkspaceNode root : branchRoots) {
            idsToDelete.addAll(collectNodeSubtreeIds(workspace, root.getId()));
        }
        workspace.getNodes().removeIf(candidate -> idsToDelete.contains(candidate.getId()));
        workspace.getExpansionSuggestions().removeIf(suggestion -> idsToDelete.contains(suggestion.getNodeId()));
        workspace.getAiRequests().removeIf(request -> request.getNodeId() != null && idsToDelete.contains(request.getNodeId()));

        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView createNextWorkspaceNode(UUID projectId, String nodeId, String actionId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode sourceNode = findWorkspaceNode(workspace, nodeId);
        findWorkspaceAction(sourceNode, actionId);

        String nextNodeId = "N" + workspace.getNextNodeIndex();
        workspace.setNextNodeIndex(workspace.getNextNodeIndex() + 1);
        workspace.getNodes().add(WorkspaceNode.create(nextNodeId, sourceNode.getId(), actionId));
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView deleteWorkspaceNode(UUID projectId, String nodeId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode root = findWorkspaceNode(workspace, nodeId);

        Set<String> idsToDelete = collectNodeSubtreeIds(workspace, root.getId());
        workspace.getNodes().removeIf(node -> idsToDelete.contains(node.getId()));
        workspace.getExpansionSuggestions().removeIf(suggestion -> idsToDelete.contains(suggestion.getNodeId()));
        workspace.getAiRequests().removeIf(request -> request.getNodeId() != null && idsToDelete.contains(request.getNodeId()));

        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView generateWorkspaceNodeDescription(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);

        StagePromptPreview preview = buildWorkspaceDescriptionPreview(project, workspace, node);
        String systemPrompt = nonBlankOrDefault(systemPromptOverride, preview.systemPrompt());
        String userPrompt = nonBlankOrDefault(userPromptOverride, preview.userPrompt());
        logAiRequest(workspace, "GENERATE_DESCRIPTION", node.getId(), systemPrompt, userPrompt);
        JsonNode generated = aiClient.generate(systemPrompt, userPrompt);
        String description = generated.path("description").asText("").trim();
        String actionDescription = generated.path("action_description").asText("").trim();
        String stateDescription = generated.path("state_description").asText("").trim();
        if (actionDescription.isBlank() && stateDescription.isBlank() && description.isBlank()) {
            throw new ConflictException("AI generated empty description");
        }
        node.setGeneratedDescriptionDraft(description);
        if (actionDescription.isBlank() && !description.isBlank()) {
            actionDescription = description;
        }
        if (stateDescription.isBlank() && !description.isBlank()) {
            stateDescription = description;
        }
        node.setGeneratedActionDescriptionDraft(actionDescription);
        node.setGeneratedStateDescriptionDraft(stateDescription);
        node.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView extractWorkspaceNodeKnowledge(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);
        String actionDescription = node.getActionDescription() == null ? "" : node.getActionDescription().trim();
        String stateDescription = node.getStateDescription() == null ? "" : node.getStateDescription().trim();
        String description = joinNodeDescription(actionDescription, stateDescription);
        if (description.isBlank()) {
            throw new ConflictException("Node description is empty");
        }

        StagePromptPreview preview = buildWorkspaceKnowledgePreview(actionDescription, stateDescription);
        String systemPrompt = nonBlankOrDefault(systemPromptOverride, preview.systemPrompt());
        String userPrompt = nonBlankOrDefault(userPromptOverride, preview.userPrompt());
        logAiRequest(workspace, "EXTRACT_KNOWLEDGE", node.getId(), systemPrompt, userPrompt);
        JsonNode generated = aiClient.generate(systemPrompt, userPrompt);
        node.setExtractedKnowledgeDraft(readStringArray(generated.path("knowledge")));
        node.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView generateWorkspaceNodeActions(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);
        String actionDescription = node.getActionDescription() == null ? "" : node.getActionDescription().trim();
        String stateDescription = node.getStateDescription() == null ? "" : node.getStateDescription().trim();
        String description = joinNodeDescription(actionDescription, stateDescription);
        if (description.isBlank()) {
            throw new ConflictException("Node description is empty");
        }

        StagePromptPreview preview = buildWorkspaceActionsPreview(node, workspace, actionDescription, stateDescription);
        String systemPrompt = nonBlankOrDefault(systemPromptOverride, preview.systemPrompt());
        String userPrompt = nonBlankOrDefault(userPromptOverride, preview.userPrompt());
        logAiRequest(workspace, "GENERATE_ACTIONS", node.getId(), systemPrompt, userPrompt);
        JsonNode generated = aiClient.generate(systemPrompt, userPrompt);

        List<String> draftActions = new ArrayList<>();
        JsonNode actionsNode = generated.path("actions");
        if (actionsNode.isArray()) {
            for (JsonNode item : actionsNode) {
                String text = item.path("text").asText("").trim();
                if (!text.isBlank()) {
                    draftActions.add(text);
                }
            }
        }
        if (draftActions.isEmpty()) {
            draftActions = readStringArray(generated.path("actions"));
        }
        draftActions = sanitizeGeneratedActions(node, workspace, actionDescription, stateDescription, draftActions);
        node.setGeneratedActionsDraft(draftActions);
        node.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView addWorkspaceGlobalKnowledge(UUID projectId, String text) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        String normalized = normalizeKnowledgeText(text);
        if (workspace.getGlobalKnowledge().stream().noneMatch(item -> item.equalsIgnoreCase(normalized))) {
            workspace.getGlobalKnowledge().add(normalized);
        }
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView removeWorkspaceGlobalKnowledge(UUID projectId, String text) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        String normalized = normalizeKnowledgeText(text);
        boolean removed = workspace.getGlobalKnowledge().removeIf(item -> item.equalsIgnoreCase(normalized));
        if (!removed) {
            throw new NotFoundException("Global knowledge item not found");
        }
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView addNodeKnowledgeToGlobal(UUID projectId, String nodeId, String text) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);
        String normalized = normalizeKnowledgeText(text);
        boolean existsInDraft = node.getExtractedKnowledgeDraft().stream()
                .anyMatch(item -> item.equalsIgnoreCase(normalized));
        if (!existsInDraft) {
            throw new NotFoundException("Knowledge item not found in node draft");
        }
        if (workspace.getGlobalKnowledge().stream().noneMatch(item -> item.equalsIgnoreCase(normalized))) {
            workspace.getGlobalKnowledge().add(normalized);
        }
        node.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView getWorkspaceGlobalKnowledge(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        requiredWorkspace(project);
        return toView(project);
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeDescriptionPrompt(UUID projectId, String nodeId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);
        return buildWorkspaceDescriptionPreview(project, workspace, node);
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeKnowledgePrompt(UUID projectId, String nodeId) {
        QuestProject project = getRequiredProject(projectId);
        WorkspaceNode node = findWorkspaceNode(requiredWorkspace(project), nodeId);
        String actionDescription = node.getActionDescription() == null ? "" : node.getActionDescription().trim();
        String stateDescription = node.getStateDescription() == null ? "" : node.getStateDescription().trim();
        String description = joinNodeDescription(actionDescription, stateDescription);
        if (description.isBlank()) {
            throw new ConflictException("Node description is empty");
        }
        return buildWorkspaceKnowledgePreview(actionDescription, stateDescription);
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeActionsPrompt(UUID projectId, String nodeId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceNode node = findWorkspaceNode(workspace, nodeId);
        String actionDescription = node.getActionDescription() == null ? "" : node.getActionDescription().trim();
        String stateDescription = node.getStateDescription() == null ? "" : node.getStateDescription().trim();
        String description = joinNodeDescription(actionDescription, stateDescription);
        if (description.isBlank()) {
            throw new ConflictException("Node description is empty");
        }
        return buildWorkspaceActionsPreview(node, workspace, actionDescription, stateDescription);
    }

    @Override
    public QuestProjectView runWorkspaceExpansion(UUID projectId, List<String> knowledge) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        List<String> scopedKnowledge = normalizeKnowledgeScope(workspace, knowledge);
        if (scopedKnowledge.isEmpty()) {
            throw new ConflictException("No knowledge provided for expansion");
        }

        for (WorkspaceNode node : workspace.getNodes()) {
            String actionDescription = node.getActionDescription() == null ? "" : node.getActionDescription().trim();
            String stateDescription = node.getStateDescription() == null ? "" : node.getStateDescription().trim();
            String description = joinNodeDescription(actionDescription, stateDescription);
            if (description.isBlank()) {
                continue;
            }
            String systemPrompt = """
                    You decide if new world knowledge unlocks new player actions for one quest node.
                    Return valid JSON only.
                    All text values must be in Russian.
                    Output schema:
                    {
                      "has_new_actions": true,
                      "suggestions": [
                        { "action_text": "", "reason": "" }
                      ]
                    }
                    Rules:
                    - Suggest only truly new actions.
                    - Avoid duplicates against existing actions.
                    - 0-3 suggestions max.
                    """;
            String userPrompt = """
                    Node id: %s
                    Node description:
                    %s

                    Existing actions:
                    %s

                    New knowledge:
                    %s
                    """.formatted(
                            node.getId(),
                            description,
                            node.getActions().stream().map(WorkspaceAction::getText).toList(),
                            scopedKnowledge
                    );
            logAiRequest(workspace, "RUN_EXPANSION", node.getId(), systemPrompt, userPrompt);
            JsonNode generated = aiClient.generate(systemPrompt, userPrompt);

            boolean hasNew = generated.path("has_new_actions").asBoolean(false);
            JsonNode suggestionsNode = generated.path("suggestions");
            if (!hasNew || !suggestionsNode.isArray()) {
                continue;
            }
            for (JsonNode suggestionNode : suggestionsNode) {
                String actionText = suggestionNode.path("action_text").asText("").trim();
                String reason = suggestionNode.path("reason").asText("").trim();
                if (actionText.isBlank()) {
                    continue;
                }
                boolean existsAction = node.getActions().stream()
                        .anyMatch(action -> action.getText() != null && action.getText().equalsIgnoreCase(actionText));
                if (existsAction) {
                    continue;
                }
                boolean existsPending = workspace.getExpansionSuggestions().stream()
                        .anyMatch(s -> "PENDING".equalsIgnoreCase(s.getStatus())
                                && node.getId().equalsIgnoreCase(s.getNodeId())
                                && actionText.equalsIgnoreCase(s.getActionText()));
                if (existsPending) {
                    continue;
                }
                String suggestionId = "E" + workspace.getNextSuggestionIndex();
                workspace.setNextSuggestionIndex(workspace.getNextSuggestionIndex() + 1);
                workspace.getExpansionSuggestions().add(new WorkspaceExpansionSuggestion(
                        suggestionId,
                        node.getId(),
                        actionText,
                        reason,
                        "PENDING",
                        new ArrayList<>(scopedKnowledge)
                ));
            }
        }

        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView acceptWorkspaceExpansionSuggestion(UUID projectId, String suggestionId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceExpansionSuggestion suggestion = findExpansionSuggestion(workspace, suggestionId);
        if (!"PENDING".equalsIgnoreCase(suggestion.getStatus())) {
            throw new ConflictException("Suggestion is not pending: " + suggestionId);
        }
        WorkspaceNode node = findWorkspaceNode(workspace, suggestion.getNodeId());
        boolean exists = node.getActions().stream()
                .anyMatch(action -> action.getText() != null && action.getText().equalsIgnoreCase(suggestion.getActionText()));
        if (!exists) {
            String actionId = "A" + workspace.getNextActionIndex();
            workspace.setNextActionIndex(workspace.getNextActionIndex() + 1);
            node.getActions().add(new WorkspaceAction(actionId, suggestion.getActionText()));
            node.setUpdatedAt(Instant.now());
        }
        suggestion.setStatus("ACCEPTED");
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public QuestProjectView dismissWorkspaceExpansionSuggestion(UUID projectId, String suggestionId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        WorkspaceExpansionSuggestion suggestion = findExpansionSuggestion(workspace, suggestionId);
        if (!"PENDING".equalsIgnoreCase(suggestion.getStatus())) {
            throw new ConflictException("Suggestion is not pending: " + suggestionId);
        }
        suggestion.setStatus("DISMISSED");
        projectRepository.save(project);
        return toView(project);
    }

    private QuestProject getRequiredProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    private NodeWorkspace requiredWorkspace(QuestProject project) {
        if (project.getNodeWorkspace() == null) {
            project.setNodeWorkspace(NodeWorkspace.createEmpty());
        }
        NodeWorkspace workspace = project.getNodeWorkspace();
        if (workspace.getNodes() == null) {
            workspace.setNodes(new ArrayList<>());
        }
        for (WorkspaceNode node : workspace.getNodes()) {
            if (node.getActions() == null) {
                node.setActions(new ArrayList<>());
            }
            if (node.getActionDescription() == null) {
                node.setActionDescription("");
            }
            if (node.getStateDescription() == null) {
                node.setStateDescription("");
            }
            if ((node.getActionDescription().isBlank() && node.getStateDescription().isBlank())
                    && node.getDescription() != null && !node.getDescription().isBlank()) {
                node.setStateDescription(node.getDescription().trim());
            }
            node.setDescription(joinNodeDescription(node.getActionDescription(), node.getStateDescription()));
            if (node.getGeneratedDescriptionDraft() == null) {
                node.setGeneratedDescriptionDraft("");
            }
            if (node.getGeneratedActionDescriptionDraft() == null) {
                node.setGeneratedActionDescriptionDraft("");
            }
            if (node.getGeneratedStateDescriptionDraft() == null) {
                node.setGeneratedStateDescriptionDraft("");
            }
            if (node.getExtractedKnowledgeDraft() == null) {
                node.setExtractedKnowledgeDraft(new ArrayList<>());
            }
            if (node.getGeneratedActionsDraft() == null) {
                node.setGeneratedActionsDraft(new ArrayList<>());
            }
        }
        if (workspace.getGlobalKnowledge() == null) {
            workspace.setGlobalKnowledge(new ArrayList<>());
        }
        if (workspace.getExpansionSuggestions() == null) {
            workspace.setExpansionSuggestions(new ArrayList<>());
        }
        if (workspace.getAiRequests() == null) {
            workspace.setAiRequests(new ArrayList<>());
        }
        if (workspace.getNextNodeIndex() <= 0) {
            workspace.setNextNodeIndex(1);
        }
        if (workspace.getNextActionIndex() <= 0) {
            workspace.setNextActionIndex(1);
        }
        if (workspace.getNextSuggestionIndex() <= 0) {
            workspace.setNextSuggestionIndex(1);
        }
        if (workspace.getNextAiRequestIndex() <= 0) {
            workspace.setNextAiRequestIndex(1);
        }
        return workspace;
    }

    private void logAiRequest(NodeWorkspace workspace, String stage, String nodeId, String systemPrompt, String userPrompt) {
        String requestId = "R" + workspace.getNextAiRequestIndex();
        workspace.setNextAiRequestIndex(workspace.getNextAiRequestIndex() + 1);
        workspace.getAiRequests().add(new WorkspaceAiRequestLog(
                requestId,
                stage,
                nodeId,
                systemPrompt,
                userPrompt,
                Instant.now()
        ));
    }

    private WorkspaceNode findWorkspaceNode(NodeWorkspace workspace, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new BadRequestException("nodeId is required");
        }
        return workspace.getNodes().stream()
                .filter(node -> nodeId.equalsIgnoreCase(node.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Node not found: " + nodeId));
    }

    private WorkspaceAction findWorkspaceAction(WorkspaceNode node, String actionId) {
        if (actionId == null || actionId.isBlank()) {
            throw new BadRequestException("actionId is required");
        }
        return node.getActions().stream()
                .filter(action -> actionId.equalsIgnoreCase(action.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Action not found: " + actionId));
    }

    private WorkspaceExpansionSuggestion findExpansionSuggestion(NodeWorkspace workspace, String suggestionId) {
        if (suggestionId == null || suggestionId.isBlank()) {
            throw new BadRequestException("suggestionId is required");
        }
        return workspace.getExpansionSuggestions().stream()
                .filter(s -> suggestionId.equalsIgnoreCase(s.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Expansion suggestion not found: " + suggestionId));
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeKnowledgeText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            throw new BadRequestException("Knowledge text is required");
        }
        return normalized;
    }

    private Set<String> collectNodeSubtreeIds(NodeWorkspace workspace, String rootNodeId) {
        Set<String> result = new HashSet<>();
        LinkedList<String> queue = new LinkedList<>();
        queue.add(rootNodeId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            for (WorkspaceNode node : workspace.getNodes()) {
                String parentId = node.getSourceNodeId();
                if (parentId != null && parentId.equalsIgnoreCase(current)) {
                    queue.add(node.getId());
                }
            }
        }
        return result;
    }

    private List<String> normalizeKnowledgeScope(NodeWorkspace workspace, List<String> knowledge) {
        if (knowledge == null || knowledge.isEmpty()) {
            return new ArrayList<>(workspace.getGlobalKnowledge());
        }
        Set<String> requested = new LinkedHashSet<>();
        for (String item : knowledge) {
            if (item == null) {
                continue;
            }
            String normalized = item.trim();
            if (!normalized.isBlank()) {
                requested.add(normalized);
            }
        }
        if (requested.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String global : workspace.getGlobalKnowledge()) {
            for (String requestedItem : requested) {
                if (global.equalsIgnoreCase(requestedItem)) {
                    result.add(global);
                    break;
                }
            }
        }
        return result;
    }

    private StagePromptPreview buildWorkspaceDescriptionPreview(QuestProject project, NodeWorkspace workspace, WorkspaceNode node) {
        boolean firstScene = node.getSourceNodeId() == null || node.getSourceNodeId().isBlank();
        WorkspaceNode sourceNode = null;
        if (node.getSourceNodeId() != null && !node.getSourceNodeId().isBlank()) {
            sourceNode = workspace.getNodes().stream()
                    .filter(candidate -> node.getSourceNodeId().equalsIgnoreCase(candidate.getId()))
                    .findFirst()
                    .orElse(null);
        }
        String sourceActionDescription = sourceNode == null || sourceNode.getActionDescription() == null
                ? ""
                : sourceNode.getActionDescription().trim();
        String sourceStateDescription = sourceNode == null || sourceNode.getStateDescription() == null
                ? ""
                : sourceNode.getStateDescription().trim();

        String selectedActionText = "";
        if (sourceNode != null && node.getSourceActionId() != null && !node.getSourceActionId().isBlank()) {
            selectedActionText = sourceNode.getActions().stream()
                    .filter(action -> node.getSourceActionId().equalsIgnoreCase(action.getId()))
                    .map(WorkspaceAction::getText)
                    .findFirst()
                    .orElse("");
        }

        String systemPrompt = """
                You are a text quest scene writer.
                Return valid JSON only.
                All text values must be in Russian.
                Output schema:
                {
                  "action_description": "",
                  "state_description": ""
                }
                Description rules:
                - action_description: 1-3 short sentences describing what player does now
                - state_description: 2-5 short sentences describing resulting state after action, this state should not describe anything that realated to this specific periond of time, what player see, so that the player can back to this scene again and again.
                - both parts concrete, atmospheric, interactive
                - no branching logic, no action list
                - do not reveal large world chunks in one scene
                - avoid plans, conclusions and future assumptions
                - actions and objects must be local and directly observable
                - for first scene keep density low: one focused location, 2-3 interactive objects, no more than 4 key facts
                """;
        String userPrompt = """
                Project:
                - name: %s
                - style: %s

                Node:
                - id: %s
                - source_node_id: %s
                - source_action_id: %s

                Previous node action_description:
                %s

                Previous node state_description:
                %s

                Chosen action text from previous node:
                %s

                Global knowledge:
                %s

                Is first scene: %s

                Write both action_description and state_description for this node.
                """.formatted(
                project.getName(),
                project.getQuestStyle(),
                node.getId(),
                node.getSourceNodeId() == null ? "" : node.getSourceNodeId(),
                node.getSourceActionId() == null ? "" : node.getSourceActionId(),
                sourceActionDescription.isBlank() ? "(none)" : sourceActionDescription,
                sourceStateDescription.isBlank() ? "(none)" : sourceStateDescription,
                selectedActionText.isBlank() ? "(none)" : selectedActionText,
                workspace.getGlobalKnowledge() == null ? "[]" : workspace.getGlobalKnowledge().toString(),
                firstScene ? "yes" : "no"
        );
        return new StagePromptPreview(systemPrompt, userPrompt);
    }

    private StagePromptPreview buildWorkspaceKnowledgePreview(String actionDescription, String stateDescription) {
        String systemPrompt = """
                You extract world knowledge facts from a quest scene description.
                Return valid JSON only.
                All text values must be in Russian.
                Output schema:
                {
                  "knowledge": ["", ""]
                }
                Rules:
                - each item is one short factual statement
                - no assumptions beyond given text
                - no duplicates
                """;
        String userPrompt = """
                Node action_description:
                %s

                Node state_description:
                %s

                Extract factual world knowledge list.
                """.formatted(
                actionDescription.isBlank() ? "(none)" : actionDescription,
                stateDescription.isBlank() ? "(none)" : stateDescription
        );
        return new StagePromptPreview(systemPrompt, userPrompt);
    }

    private StagePromptPreview buildWorkspaceActionsPreview(WorkspaceNode node, NodeWorkspace workspace, String actionDescription, String stateDescription) {
        boolean firstScene = node.getSourceNodeId() == null || node.getSourceNodeId().isBlank();
        String systemPrompt = """
                You generate player actions for one quest node.
                Return valid JSON only.
                All text values must be in Russian.
                Output schema:
                {
                  "actions": [
                    { "text": "" }
                  ]
                }
                Rules:
                - 3-6 short actionable options
                - each option starts with a verb
                - avoid duplicates and vague options
                - action can rely only on facts already known from current scene text and global knowledge
                - do not propose actions that require hidden resources, tools or destinations that were not revealed yet
                - if information is missing, prefer micro-actions: inspect, ask, listen, check, look around
                - for first scene generate 3-5 low-risk micro-actions; avoid large strategic actions
                """;
        String userPrompt = """
                Node action_description:
                %s

                Node state_description:
                %s

                Global knowledge:
                %s

                Existing node actions:
                %s

                Is first scene: %s

                Generate candidate actions for this node.
                """.formatted(
                actionDescription.isBlank() ? "(none)" : actionDescription,
                stateDescription.isBlank() ? "(none)" : stateDescription,
                workspace.getGlobalKnowledge() == null ? "[]" : workspace.getGlobalKnowledge().toString(),
                node.getActions().stream().map(WorkspaceAction::getText).toList(),
                firstScene ? "yes" : "no"
        );
        userPrompt = userPrompt + "\n\n" + STORY_BIBLE_SPIKE_CONTEXT;
        return new StagePromptPreview(systemPrompt, userPrompt);
    }

    private List<String> sanitizeGeneratedActions(
            WorkspaceNode node,
            NodeWorkspace workspace,
            String actionDescription,
            String stateDescription,
            List<String> draftActions
    ) {
        if (draftActions == null || draftActions.isEmpty()) {
            return new ArrayList<>();
        }

        String sceneText = ((actionDescription == null ? "" : actionDescription) + " " + (stateDescription == null ? "" : stateDescription)).toLowerCase(Locale.ROOT);
        String knowledgeText = (workspace.getGlobalKnowledge() == null ? "" : String.join(" ", workspace.getGlobalKnowledge())).toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>();
        List<String> filtered = new ArrayList<>();
        boolean firstScene = node.getSourceNodeId() == null || node.getSourceNodeId().isBlank();

        for (String raw : draftActions) {
            if (raw == null) {
                continue;
            }
            String text = raw.trim();
            if (text.isBlank()) {
                continue;
            }
            String key = text.toLowerCase(Locale.ROOT);
            if (seen.contains(key)) {
                continue;
            }
            if (!isActionGroundedInKnownFacts(text, sceneText, knowledgeText, firstScene)) {
                continue;
            }
            seen.add(key);
            filtered.add(text);
        }

        if (firstScene && filtered.size() > 5) {
            filtered = new ArrayList<>(filtered.subList(0, 5));
        }
        if (!firstScene && filtered.size() > 6) {
            filtered = new ArrayList<>(filtered.subList(0, 6));
        }
        return filtered;
    }

    private boolean isActionGroundedInKnownFacts(String actionText, String sceneText, String knowledgeText, boolean firstScene) {
        String text = actionText.toLowerCase(Locale.ROOT);
        if (firstScene) {
            if (text.startsWith("осмотреть")
                    || text.startsWith("проверить")
                    || text.startsWith("поговорить")
                    || text.startsWith("прислушаться")
                    || text.startsWith("выглянуть")
                    || text.startsWith("подойти")) {
                return true;
            }
        }
        String[] words = text.split("[^\\p{L}\\p{N}]+");
        for (String word : words) {
            if (word.length() < 5) {
                continue;
            }
            if (sceneText.contains(word) || knowledgeText.contains(word)) {
                return true;
            }
        }
        return !firstScene;
    }

    private String joinNodeDescription(String actionDescription, String stateDescription) {
        String action = actionDescription == null ? "" : actionDescription.trim();
        String state = stateDescription == null ? "" : stateDescription.trim();
        if (action.isBlank()) {
            return state;
        }
        if (state.isBlank()) {
            return action;
        }
        return action + "\n\n" + state;
    }

    private QuestStage getRequiredStage(QuestProject project, StageType type) {
        return project.findStage(type)
                .orElseThrow(() -> new NotFoundException("Stage not found: " + type));
    }

    private void unlockStageIfEligible(QuestProject project, StageType stageType, QuestStage stage) {
        if (stage.getStatus() != StageStatus.NOT_STARTED) {
            return;
        }
        int index = -1;
        List<QuestStage> stages = project.getStages();
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getType() == stageType) {
                index = i;
                break;
            }
        }
        if (index <= 0) {
            return;
        }
        QuestStage previous = stages.get(index - 1);
        if (previous.getStatus() == StageStatus.APPROVED) {
            stage.setStatus(StageStatus.READY);
        }
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
        if ("NPC".equals(rawType.trim())) {
            return StageType.ACHIEVEMENT_REALISATION;
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
        root.set("nodeWorkspace", objectMapper.valueToTree(requiredWorkspace(project)));
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
                stages,
                objectMapper.convertValue(requiredWorkspace(project), Object.class)
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

    private JsonNode markAchievementSceneReview(JsonNode stageOutput, String wayId) {
        var root = objectMapper.createObjectNode();
        var ways = objectMapper.createArrayNode();
        JsonNode existing = stageOutput == null ? null : stageOutput.path("ways");
        if (existing != null && existing.isArray()) {
            for (JsonNode way : existing) {
                String id = way.path("way_id").asText("");
                var wayNode = way.deepCopy();
                if (id.equalsIgnoreCase(wayId)) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).put("status", StageStatus.REVIEW.name());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).put("approved", false);
                }
                ways.add(wayNode);
            }
        }
        root.set("ways", ways);
        return root;
    }

    private JsonNode markAchievementSceneApproved(JsonNode stageOutput, String wayId) {
        if (stageOutput == null || !stageOutput.path("ways").isArray()) {
            throw new ConflictException("No generated achievement scenes to approve");
        }
        var root = objectMapper.createObjectNode();
        var ways = objectMapper.createArrayNode();
        boolean found = false;
        for (JsonNode way : stageOutput.path("ways")) {
            String id = way.path("way_id").asText("");
            var wayNode = way.deepCopy();
            if (id.equalsIgnoreCase(wayId)) {
                found = true;
                ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).put("status", StageStatus.APPROVED.name());
                ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).put("approved", true);
            }
            ways.add(wayNode);
        }
        if (!found) {
            throw new NotFoundException("Generated achievement scene block not found: " + wayId);
        }
        root.set("ways", ways);
        return root;
    }

    private boolean areAllAchievementScenesApproved(QuestProject project, JsonNode achievementScenesOutput) {
        QuestStage realisationStage = getRequiredStage(project, StageType.ACHIEVEMENT_REALISATION);
        JsonNode realisations = realisationStage.getCurrentRevision() == null
                ? null
                : realisationStage.getCurrentRevision().outputJson().path("achievement_realisations");
        if (realisations == null || !realisations.isArray() || realisations.isEmpty()) {
            return false;
        }
        Set<String> requiredWayIds = new HashSet<>();
        for (JsonNode realisation : realisations) {
            JsonNode ways = realisation.path("ways");
            if (ways.isArray()) {
                for (JsonNode way : ways) {
                    String id = way.path("id").asText("").toUpperCase();
                    if (!id.isBlank()) {
                        requiredWayIds.add(id);
                    }
                }
            }
        }
        if (requiredWayIds.isEmpty()) {
            return false;
        }

        Set<String> approvedWayIds = new HashSet<>();
        JsonNode generatedWays = achievementScenesOutput.path("ways");
        if (generatedWays.isArray()) {
            for (JsonNode way : generatedWays) {
                if (way.path("approved").asBoolean(false)) {
                    String id = way.path("way_id").asText("").toUpperCase();
                    if (!id.isBlank()) {
                        approvedWayIds.add(id);
                    }
                }
            }
        }
        return approvedWayIds.containsAll(requiredWayIds);
    }

    private JsonNode markKnowledgeChainReview(JsonNode stageOutput, String wayId) {
        var root = objectMapper.createObjectNode();
        var chains = objectMapper.createArrayNode();
        JsonNode existing = stageOutput == null ? null : stageOutput.path("knowledge_chains");
        if (existing != null && existing.isArray()) {
            for (JsonNode chain : existing) {
                String id = chain.path("way_id").asText("");
                var chainNode = chain.deepCopy();
                if (id.equalsIgnoreCase(wayId)) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) chainNode).put("status", StageStatus.REVIEW.name());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) chainNode).put("approved", false);
                }
                chains.add(chainNode);
            }
        }
        root.set("knowledge_chains", chains);
        return root;
    }

    private JsonNode markKnowledgeChainApproved(JsonNode stageOutput, String wayId) {
        if (stageOutput == null || !stageOutput.path("knowledge_chains").isArray()) {
            throw new ConflictException("No generated knowledge chains to approve");
        }
        var root = objectMapper.createObjectNode();
        var chains = objectMapper.createArrayNode();
        boolean found = false;
        for (JsonNode chain : stageOutput.path("knowledge_chains")) {
            String id = chain.path("way_id").asText("");
            var chainNode = chain.deepCopy();
            if (id.equalsIgnoreCase(wayId)) {
                found = true;
                ((com.fasterxml.jackson.databind.node.ObjectNode) chainNode).put("status", StageStatus.APPROVED.name());
                ((com.fasterxml.jackson.databind.node.ObjectNode) chainNode).put("approved", true);
            }
            chains.add(chainNode);
        }
        if (!found) {
            throw new NotFoundException("Generated knowledge chain block not found: " + wayId);
        }
        root.set("knowledge_chains", chains);
        return root;
    }

    private boolean areAllKnowledgeChainsApproved(QuestProject project, JsonNode knowledgeChainOutput) {
        QuestStage informationFlowStage = getRequiredStage(project, StageType.ACHIEVEMENT_INFORMATION_FLOW);
        JsonNode flows = informationFlowStage.getCurrentRevision() == null
                ? null
                : informationFlowStage.getCurrentRevision().outputJson().path("achievement_information_flow");
        if (flows == null || !flows.isArray() || flows.isEmpty()) {
            return false;
        }

        Set<String> requiredWayIds = new HashSet<>();
        for (JsonNode flow : flows) {
            String wayId = flow.path("way_id").asText("").toUpperCase();
            if (!wayId.isBlank()) {
                requiredWayIds.add(wayId);
            }
        }
        if (requiredWayIds.isEmpty()) {
            return false;
        }

        Set<String> approvedWayIds = new HashSet<>();
        JsonNode chains = knowledgeChainOutput.path("knowledge_chains");
        if (chains.isArray()) {
            for (JsonNode chain : chains) {
                if (chain.path("approved").asBoolean(false)) {
                    String wayId = chain.path("way_id").asText("").toUpperCase();
                    if (!wayId.isBlank()) {
                        approvedWayIds.add(wayId);
                    }
                }
            }
        }
        return approvedWayIds.containsAll(requiredWayIds);
    }

    private boolean hasAtLeastOneApprovedKnowledgeChain(QuestProject project) {
        QuestStage knowledgeChainStage = getRequiredStage(project, StageType.KNOWLEDGE_CHAIN);
        JsonNode output = knowledgeChainStage.getCurrentRevision() == null ? null : knowledgeChainStage.getCurrentRevision().outputJson();
        if (output == null || !output.path("knowledge_chains").isArray()) {
            return false;
        }
        for (JsonNode chain : output.path("knowledge_chains")) {
            if (chain.path("approved").asBoolean(false)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode markActionQuestReview(JsonNode stageOutput, String wayId) {
        var root = objectMapper.createObjectNode();
        var ways = objectMapper.createArrayNode();
        JsonNode existing = stageOutput == null ? null : stageOutput.path("ways");
        if (existing != null && existing.isArray()) {
            for (JsonNode way : existing) {
                String id = way.path("way_id").asText("");
                var wayNode = way.deepCopy();
                if (id.equalsIgnoreCase(wayId)) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).put("status", StageStatus.REVIEW.name());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).put("approved", false);
                }
                ways.add(wayNode);
            }
        }
        root.set("ways", ways);
        return root;
    }

    private JsonNode markActionQuestApproved(JsonNode stageOutput, String wayId) {
        if (stageOutput == null || !stageOutput.path("ways").isArray()) {
            throw new ConflictException("No generated action quests to approve");
        }
        var root = objectMapper.createObjectNode();
        var ways = objectMapper.createArrayNode();
        boolean found = false;
        for (JsonNode way : stageOutput.path("ways")) {
            String id = way.path("way_id").asText("");
            var wayNode = way.deepCopy();
            if (id.equalsIgnoreCase(wayId)) {
                found = true;
                ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).put("status", StageStatus.APPROVED.name());
                ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).put("approved", true);
            }
            ways.add(wayNode);
        }
        if (!found) {
            throw new NotFoundException("Generated action quest block not found: " + wayId);
        }
        root.set("ways", ways);
        return root;
    }

    private boolean areAllActionQuestsApproved(QuestProject project, JsonNode actionQuestsOutput) {
        JsonNode requiredActionsByWay = requiredActionKeysByWay(project);
        if (requiredActionsByWay.isEmpty()) {
            return false;
        }

        Set<String> requiredWayIds = new HashSet<>();
        requiredActionsByWay.fieldNames().forEachRemaining(requiredWayIds::add);

        Set<String> fullyApprovedWays = new HashSet<>();
        JsonNode ways = actionQuestsOutput.path("ways");
        if (ways.isArray()) {
            for (JsonNode way : ways) {
                String wayId = way.path("way_id").asText("").toUpperCase();
                if (wayId.isBlank()) {
                    continue;
                }
                Set<String> requiredActionKeys = new HashSet<>();
                JsonNode requiredArray = requiredActionsByWay.path(wayId);
                if (requiredArray.isArray()) {
                    for (JsonNode key : requiredArray) {
                        String value = key.asText("").toUpperCase();
                        if (!value.isBlank()) {
                            requiredActionKeys.add(value);
                        }
                    }
                }
                if (requiredActionKeys.isEmpty()) {
                    continue;
                }

                Set<String> approvedActionKeys = new HashSet<>();
                JsonNode resolutions = way.path("resolutions");
                if (resolutions.isArray()) {
                    for (JsonNode resolution : resolutions) {
                        if (!resolution.path("approved").asBoolean(false)) {
                            continue;
                        }
                        String key = resolution.path("scene_id").asText("").toUpperCase() + "::" + resolution.path("action_id").asText("").toUpperCase();
                        if (!key.equals("::")) {
                            approvedActionKeys.add(key);
                        }
                    }
                }
                if (approvedActionKeys.containsAll(requiredActionKeys)) {
                    fullyApprovedWays.add(wayId);
                }
            }
        }
        return fullyApprovedWays.containsAll(requiredWayIds);
    }

    private boolean hasAtLeastOneApprovedAchievementScene(QuestProject project) {
        QuestStage scenesStage = getRequiredStage(project, StageType.ACHIEVEMENT_SCENES);
        JsonNode output = scenesStage.getCurrentRevision() == null ? null : scenesStage.getCurrentRevision().outputJson();
        if (output == null || !output.path("ways").isArray()) {
            return false;
        }
        for (JsonNode way : output.path("ways")) {
            if (way.path("approved").asBoolean(false)) {
                return true;
            }
        }
        return false;
    }

    private JsonNode markActionResolutionReview(JsonNode stageOutput, String wayId, String sceneId, String actionId) {
        var root = objectMapper.createObjectNode();
        var ways = objectMapper.createArrayNode();
        JsonNode existingWays = stageOutput == null ? null : stageOutput.path("ways");
        if (existingWays != null && existingWays.isArray()) {
            for (JsonNode way : existingWays) {
                var wayNode = way.deepCopy();
                if (wayId.equalsIgnoreCase(way.path("way_id").asText("")) && wayNode.path("resolutions").isArray()) {
                    var resolutions = objectMapper.createArrayNode();
                    for (JsonNode resolution : wayNode.path("resolutions")) {
                        var resolutionNode = resolution.deepCopy();
                        boolean same = sceneId.equalsIgnoreCase(resolution.path("scene_id").asText(""))
                                && actionId.equalsIgnoreCase(resolution.path("action_id").asText(""));
                        if (same) {
                            ((com.fasterxml.jackson.databind.node.ObjectNode) resolutionNode).put("status", StageStatus.REVIEW.name());
                            ((com.fasterxml.jackson.databind.node.ObjectNode) resolutionNode).put("approved", false);
                        }
                        resolutions.add(resolutionNode);
                    }
                    ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).set("resolutions", resolutions);
                }
                ways.add(wayNode);
            }
        }
        root.set("ways", ways);
        return root;
    }

    private JsonNode markActionResolutionApproved(QuestProject project, JsonNode stageOutput, String wayId, String sceneId, String actionId) {
        if (stageOutput == null || !stageOutput.path("ways").isArray()) {
            throw new ConflictException("No generated action quest ways to approve");
        }
        var root = objectMapper.createObjectNode();
        var ways = objectMapper.createArrayNode();
        boolean found = false;
        for (JsonNode way : stageOutput.path("ways")) {
            var wayNode = way.deepCopy();
            if (wayId.equalsIgnoreCase(way.path("way_id").asText("")) && wayNode.path("resolutions").isArray()) {
                var resolutions = objectMapper.createArrayNode();
                for (JsonNode resolution : wayNode.path("resolutions")) {
                    var resolutionNode = resolution.deepCopy();
                    boolean same = sceneId.equalsIgnoreCase(resolution.path("scene_id").asText(""))
                            && actionId.equalsIgnoreCase(resolution.path("action_id").asText(""));
                    if (same) {
                        found = true;
                        ((com.fasterxml.jackson.databind.node.ObjectNode) resolutionNode).put("status", StageStatus.APPROVED.name());
                        ((com.fasterxml.jackson.databind.node.ObjectNode) resolutionNode).put("approved", true);
                    }
                    resolutions.add(resolutionNode);
                }
                ((com.fasterxml.jackson.databind.node.ObjectNode) wayNode).set("resolutions", resolutions);
            }
            ways.add(wayNode);
        }
        if (!found) {
            throw new NotFoundException("Generated action resolution not found: " + wayId + "/" + sceneId + "/" + actionId);
        }

        JsonNode requiredActionsByWay = requiredActionKeysByWay(project);
        for (JsonNode way : ways) {
            if (!(way instanceof com.fasterxml.jackson.databind.node.ObjectNode wayNode)) {
                continue;
            }
            String candidateWayId = wayNode.path("way_id").asText("").toUpperCase();
            if (candidateWayId.isBlank()) {
                continue;
            }
            Set<String> required = new HashSet<>();
            JsonNode requiredArray = requiredActionsByWay.path(candidateWayId);
            if (requiredArray.isArray()) {
                for (JsonNode key : requiredArray) {
                    String value = key.asText("").toUpperCase();
                    if (!value.isBlank()) {
                        required.add(value);
                    }
                }
            }
            Set<String> approved = new HashSet<>();
            JsonNode resolutions = wayNode.path("resolutions");
            if (resolutions.isArray()) {
                for (JsonNode resolution : resolutions) {
                    if (!resolution.path("approved").asBoolean(false)) {
                        continue;
                    }
                    String key = resolution.path("scene_id").asText("").toUpperCase() + "::" + resolution.path("action_id").asText("").toUpperCase();
                    if (!key.equals("::")) {
                        approved.add(key);
                    }
                }
            }
            boolean wayApproved = !required.isEmpty() && approved.containsAll(required);
            wayNode.put("approved", wayApproved);
            wayNode.put("status", wayApproved ? StageStatus.APPROVED.name() : StageStatus.REVIEW.name());
        }
        root.set("ways", ways);
        return root;
    }

    private JsonNode requiredActionKeysByWay(QuestProject project) {
        var root = objectMapper.createObjectNode();
        QuestStage scenesStage = getRequiredStage(project, StageType.ACHIEVEMENT_SCENES);
        JsonNode ways = scenesStage.getCurrentRevision() == null ? null : scenesStage.getCurrentRevision().outputJson().path("ways");
        if (ways == null || !ways.isArray()) {
            return root;
        }
        for (JsonNode way : ways) {
            if (!way.path("approved").asBoolean(false)) {
                continue;
            }
            String wayId = way.path("way_id").asText("").toUpperCase();
            if (wayId.isBlank()) {
                continue;
            }
            var required = objectMapper.createArrayNode();
            JsonNode scenes = way.path("scenes");
            if (scenes.isArray()) {
                for (JsonNode scene : scenes) {
                    String sceneId = scene.path("id").asText("").toUpperCase();
                    JsonNode actions = scene.path("available_actions");
                    if (sceneId.isBlank() || !actions.isArray()) {
                        continue;
                    }
                    for (JsonNode action : actions) {
                        String actionId = action.path("id").asText("").toUpperCase();
                        if (!actionId.isBlank()) {
                            required.add(sceneId + "::" + actionId);
                        }
                    }
                }
            }
            if (!required.isEmpty()) {
                root.set(wayId, required);
            }
        }
        return root;
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

    private String nonBlankOrDefault(String override, String defaultValue) {
        if (override == null) {
            return defaultValue;
        }
        String normalized = override.trim();
        return normalized.isBlank() ? defaultValue : normalized;
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
            case FIRST_SCENE -> "First Scene";
            case QUEST_DESCRIPTION -> "Quest Description";
            case QUEST_CONSTRAINTS -> "Quest Constraints";
            case ACHIEVEMENT_RESOURCE_ANALYSIS -> "Achievement Resource Analysis";
            case WORLD -> "World";
            case ACHIEVEMENT_REALISATION -> "Achievement Realisation";
            case ACHIEVEMENT_INFORMATION_FLOW -> "Achievement Information Flow";
            case KNOWLEDGE_CHAIN -> "Knowledge Chain";
            case ACHIEVEMENT_SCENES -> "Achievement Scenes";
            case ACTION_QUESTS -> "Action Quests";
            case FACTS -> "Facts";
            case QUEST_OUTLINE -> "Quest Outline";
            case CHAPTERS -> "Chapters";
            case SCENES -> "Scenes";
            case QUEST_GRAPH -> "Quest Graph";
            case LOGIC_VALIDATION -> "Logic Validation";
        };
    }
}
