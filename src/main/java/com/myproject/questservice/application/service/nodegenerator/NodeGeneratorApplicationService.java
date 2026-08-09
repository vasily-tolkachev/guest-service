package com.myproject.questservice.application.service.nodegenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myproject.questservice.adapter.in.rest.dto.UploadQuestResponse;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.FirstSceneIdeaView;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.FirstSceneIdeasResponse;
import com.myproject.questservice.adapter.in.rest.dto.nodegenerator.NodeGeneratorProjectView;
import com.myproject.questservice.application.port.in.generator.QuestGeneratorUseCase;
import com.myproject.questservice.application.port.in.nodegenerator.NodeGeneratorUseCase;
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.QuestImportService;
import com.myproject.questservice.application.service.generator.ProjectRepository;
import com.myproject.questservice.application.service.generator.stage.StagePromptPreview;
import com.myproject.questservice.config.OpenAiProperties;
import com.myproject.questservice.domain.generator.NodeWorkspace;
import com.myproject.questservice.domain.generator.WorkspaceAction;
import com.myproject.questservice.domain.generator.WorkspaceNode;
import com.myproject.questservice.domain.generator.QuestProject;
import com.myproject.questservice.domain.generator.QuestProjectStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class NodeGeneratorApplicationService implements NodeGeneratorUseCase {
    private static final int SCENE_IDEAS_LIMIT = 3;
    private static final long IDEAS_CACHE_TTL_MS = 10 * 60 * 1000;
    private static final String STORY_BIBLE_SPIKE_VERSION = "SB_SPIKE_V1";
    private static final String STORY_BIBLE_SPIKE_TONE_CONTEXT = """
            Story Bible guidance (first scene, minimal):
            - tone: Мрачный прибрежный триллер с элементами тайны, без сверхъестественного.
            - logline_hint: Смотритель маяка исчез; есть ощущение скрытой угрозы.
            Keep only atmosphere and immediate situation. Do not inject plot facts checklist.
            """;
    private static final String STORY_BIBLE_SPIKE_SOFT_CONTEXT = """
            Story Bible signals (soft guidance, not checklist):
            - title: Тень старого маяка
            - logline: Смотритель маяка пропал три ночи назад; его записи намекают на охоту.
            - protagonist_goal: Найти смотрителя маяка и выяснить, что произошло.
            - true_stakes: Маяк скрывает контрабандный груз; смотритель инсценировал исчезновение, чтобы уйти от долгов.
            - opposing_force: Местный рыбак, который выглядит союзником, но мешает раскрытию правды.
            - key_facts:
              1) Маяк не работал последние три ночи.
              2) В судовом журнале есть вырванные страницы.
              3) Рыбак появляется каждый раз, когда игрок находит новую улику.
            - next_unrevealed_twist: Помогающий игроку рыбак на самом деле работает против него и заметает следы.
            Usage rules:
            - Use at most ONE signal from this block in a single idea.
            - If none fits naturally, use none.
            - Do not dump multiple facts/twists in one scene.
            - Do not introduce opposing_force unless scene logic naturally motivates it.
            """;

    private final ProjectRepository projectRepository;
    private final QuestGeneratorUseCase questGeneratorUseCase;
    private final QuestImportService questImportService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;
    private final ConcurrentMap<String, CachedIdeas> sceneIdeasCache = new ConcurrentHashMap<>();

    public NodeGeneratorApplicationService(
            ProjectRepository projectRepository,
            QuestGeneratorUseCase questGeneratorUseCase,
            QuestImportService questImportService,
            AiClient aiClient,
            ObjectMapper objectMapper,
            OpenAiProperties openAiProperties
    ) {
        this.projectRepository = projectRepository;
        this.questGeneratorUseCase = questGeneratorUseCase;
        this.questImportService = questImportService;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
    }

    @Override
    public NodeGeneratorProjectView createProject(String name, String questStyle) {
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
    public List<NodeGeneratorProjectView> listProjects() {
        return projectRepository.findAll().stream().map(this::toView).toList();
    }

    @Override
    public NodeGeneratorProjectView getProject(UUID id) {
        return toView(getRequiredProject(id));
    }

    @Override
    public NodeGeneratorProjectView renameProject(UUID id, String name) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw new BadRequestException("Project name is required");
        }
        QuestProject project = getRequiredProject(id);
        project.setName(normalizedName);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public void deleteProject(UUID id) {
        getRequiredProject(id);
        projectRepository.deleteById(id);
    }

    @Override
    public NodeGeneratorProjectView createWorkspaceNode(UUID projectId, String sourceNodeId, String sourceActionId) {
        questGeneratorUseCase.createWorkspaceNode(projectId, sourceNodeId, sourceActionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView deleteWorkspaceNode(UUID projectId, String nodeId) {
        questGeneratorUseCase.deleteWorkspaceNode(projectId, nodeId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView updateWorkspaceNodeDescription(UUID projectId, String nodeId, String actionDescription, String stateDescription) {
        questGeneratorUseCase.updateWorkspaceNodeDescription(projectId, nodeId, actionDescription, stateDescription);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView addWorkspaceNodeAction(UUID projectId, String nodeId, String text) {
        questGeneratorUseCase.addWorkspaceNodeAction(projectId, nodeId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView updateWorkspaceNodeAction(UUID projectId, String nodeId, String actionId, String text) {
        questGeneratorUseCase.updateWorkspaceNodeAction(projectId, nodeId, actionId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView deleteWorkspaceNodeAction(UUID projectId, String nodeId, String actionId) {
        questGeneratorUseCase.deleteWorkspaceNodeAction(projectId, nodeId, actionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView createNextWorkspaceNode(UUID projectId, String nodeId, String actionId) {
        questGeneratorUseCase.createNextWorkspaceNode(projectId, nodeId, actionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeDescriptionPrompt(UUID projectId, String nodeId) {
        return questGeneratorUseCase.previewWorkspaceNodeDescriptionPrompt(projectId, nodeId);
    }

    @Override
    public NodeGeneratorProjectView generateWorkspaceNodeDescription(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride) {
        questGeneratorUseCase.generateWorkspaceNodeDescription(projectId, nodeId, systemPromptOverride, userPromptOverride);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeKnowledgePrompt(UUID projectId, String nodeId) {
        return questGeneratorUseCase.previewWorkspaceNodeKnowledgePrompt(projectId, nodeId);
    }

    @Override
    public NodeGeneratorProjectView extractWorkspaceNodeKnowledge(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride) {
        questGeneratorUseCase.extractWorkspaceNodeKnowledge(projectId, nodeId, systemPromptOverride, userPromptOverride);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public StagePromptPreview previewWorkspaceNodeActionsPrompt(UUID projectId, String nodeId) {
        return questGeneratorUseCase.previewWorkspaceNodeActionsPrompt(projectId, nodeId);
    }

    @Override
    public NodeGeneratorProjectView generateWorkspaceNodeActions(UUID projectId, String nodeId, String systemPromptOverride, String userPromptOverride) {
        questGeneratorUseCase.generateWorkspaceNodeActions(projectId, nodeId, systemPromptOverride, userPromptOverride);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView addWorkspaceGlobalKnowledge(UUID projectId, String text) {
        questGeneratorUseCase.addWorkspaceGlobalKnowledge(projectId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView removeWorkspaceGlobalKnowledge(UUID projectId, String text) {
        questGeneratorUseCase.removeWorkspaceGlobalKnowledge(projectId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView addNodeKnowledgeToGlobal(UUID projectId, String nodeId, String text) {
        questGeneratorUseCase.addNodeKnowledgeToGlobal(projectId, nodeId, text);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView runWorkspaceExpansion(UUID projectId, List<String> knowledge) {
        questGeneratorUseCase.runWorkspaceExpansion(projectId, knowledge);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView acceptWorkspaceExpansionSuggestion(UUID projectId, String suggestionId) {
        questGeneratorUseCase.acceptWorkspaceExpansionSuggestion(projectId, suggestionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public NodeGeneratorProjectView dismissWorkspaceExpansionSuggestion(UUID projectId, String suggestionId) {
        questGeneratorUseCase.dismissWorkspaceExpansionSuggestion(projectId, suggestionId);
        return toView(getRequiredProject(projectId));
    }

    @Override
    public Object exportProjectJson(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        var root = objectMapper.createObjectNode();
        root.put("name", project.getName());
        root.put("questStyle", project.getQuestStyle());
        root.put("status", project.getStatus().name());
        root.set("workspace", objectMapper.valueToTree(requiredWorkspace(project)));
        return objectMapper.convertValue(root, Object.class);
    }

    @Override
    public NodeGeneratorProjectView importProjectJson(UUID projectId, Object snapshotJson) {
        if (snapshotJson == null) {
            throw new BadRequestException("snapshotJson is required");
        }
        JsonNode snapshotNode = objectMapper.valueToTree(snapshotJson);
        if (snapshotNode == null || snapshotNode.isNull()) {
            throw new BadRequestException("snapshotJson is required");
        }

        QuestProject project = getRequiredProject(projectId);
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
                project.setStatus(QuestProjectStatus.valueOf(importedProjectStatus));
            } catch (IllegalArgumentException ignored) {
            }
        }

        JsonNode workspaceNode = snapshotNode.path("workspace");
        if (workspaceNode == null || workspaceNode.isMissingNode() || workspaceNode.isNull()) {
            throw new BadRequestException("snapshotJson.workspace is required");
        }
        NodeWorkspace workspace = objectMapper.convertValue(workspaceNode, NodeWorkspace.class);
        project.setNodeWorkspace(workspace == null ? NodeWorkspace.createEmpty() : workspace);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public NodeGeneratorProjectView importProjectJson(Object snapshotJson) {
        if (snapshotJson == null) {
            throw new BadRequestException("snapshotJson is required");
        }
        JsonNode snapshotNode = objectMapper.valueToTree(snapshotJson);
        if (snapshotNode == null || snapshotNode.isNull()) {
            throw new BadRequestException("snapshotJson is required");
        }

        String importedName = snapshotNode.path("name").asText("").trim();
        if (importedName.isBlank()) {
            throw new BadRequestException("snapshotJson.name is required");
        }

        QuestProject project = projectRepository.findByName(importedName)
                .orElseGet(() -> {
                    String questStyle = snapshotNode.path("questStyle").asText("").trim();
                    if (questStyle.isBlank()) {
                        questStyle = "classic-adventure";
                    }
                    return QuestProject.create(importedName, questStyle);
                });
        project.setName(importedName);

        String importedQuestStyle = snapshotNode.path("questStyle").asText("").trim();
        if (!importedQuestStyle.isBlank()) {
            project.setQuestStyle(importedQuestStyle);
        }
        String importedProjectStatus = snapshotNode.path("status").asText("").trim();
        if (!importedProjectStatus.isBlank()) {
            try {
                project.setStatus(QuestProjectStatus.valueOf(importedProjectStatus));
            } catch (IllegalArgumentException ignored) {
            }
        }

        JsonNode workspaceNode = snapshotNode.path("workspace");
        if (workspaceNode == null || workspaceNode.isMissingNode() || workspaceNode.isNull()) {
            throw new BadRequestException("snapshotJson.workspace is required");
        }
        NodeWorkspace workspace = objectMapper.convertValue(workspaceNode, NodeWorkspace.class);
        project.setNodeWorkspace(workspace == null ? NodeWorkspace.createEmpty() : workspace);
        projectRepository.save(project);
        return toView(project);
    }

    @Override
    public UploadQuestResponse createQuestFromProject(UUID projectId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);
        if (workspace.getNodes() == null || workspace.getNodes().isEmpty()) {
            throw new BadRequestException("Project has no scenes to build a quest");
        }
        String dsl = toQuestDsl(project, workspace);
        return questImportService.uploadQuest(dsl);
    }

    @Override
    public FirstSceneIdeasResponse generateFirstSceneIdeas(String prompt) {
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        String cacheKey = "FIRST::" + STORY_BIBLE_SPIKE_VERSION + "::" + normalizedPrompt;
        FirstSceneIdeasResponse cached = getCachedIdeas(cacheKey);
        if (cached != null) {
            return cached;
        }

        String systemPrompt = """
                Ты сценарист интерактивных квестов.
                Сгенерируй 3 разных идеи стартовой ситуации для первой сцены.
                Верни ТОЛЬКО JSON формата:
                {
                  "ideas": [
                    { "title": "Короткий заголовок", "scenarioText": "Описание стартовой ситуации (2-4 предложения)" }
                  ]
                }
                Ограничения:
                - Только русский язык.
                - Без markdown.
                - title: 2-8 слов.
                - scenarioText: конкретная ситуация, без абстракций.
                """;
        String userPrompt = normalizedPrompt.isBlank()
                ? "Пользователь не дал тему. Предложи универсальные идеи для приключенческого квеста."
                : "Тема и ситуация от пользователя:\n" + normalizedPrompt;

        systemPrompt = systemPrompt + """
                Additional rules:
                - Keep first scene focused on one immediate situation.
                - Avoid plot-dump and checklist-like insertion of facts.
                - Do not reveal multiple major facts or twists at once.
                """;
        userPrompt = userPrompt + "\n\n" + STORY_BIBLE_SPIKE_TONE_CONTEXT;

        FirstSceneIdeasResponse response = parseIdeas(
                aiClient.generate(systemPrompt, userPrompt, draftModel()),
                "AI did not return first scene ideas",
                "AI returned empty first scene ideas"
        );
        putCachedIdeas(cacheKey, response);
        return response;
    }

    @Override
    public FirstSceneIdeasResponse generateNextSceneIdeas(UUID projectId, String nodeId, String actionId) {
        QuestProject project = getRequiredProject(projectId);
        NodeWorkspace workspace = requiredWorkspace(project);

        WorkspaceNode sourceNode = workspace.getNodes().stream()
                .filter(node -> node.getId() != null && nodeId.equalsIgnoreCase(node.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Node not found: " + nodeId));

        WorkspaceAction action = (sourceNode.getActions() == null ? List.<WorkspaceAction>of() : sourceNode.getActions()).stream()
                .filter(candidate -> candidate.getId() != null && actionId.equalsIgnoreCase(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Action not found: " + actionId));

        String sourceActionDescription = trimToEmpty(sourceNode.getActionDescription());
        String sourceStateDescription = trimToEmpty(sourceNode.getStateDescription());
        String sourceDescription = sourceStateDescription.isBlank()
                ? sourceActionDescription
                : sourceActionDescription + "\n" + sourceStateDescription;
        if (sourceDescription.isBlank()) {
            sourceDescription = trimToEmpty(sourceNode.getDescription());
        }

        String systemPrompt = """
                Ты сценарист интерактивных квестов.
                Сгенерируй 5 разных вариантов описания следующей сцены.
                Верни ТОЛЬКО JSON формата:
                {
                  "ideas": [
                    { "title": "Короткий заголовок", "scenarioText": "Описание следующей сцены (2-4 предложения)" }
                  ]
                }
                Ограничения:
                - Только русский язык.
                - Без markdown.
                - Учитывай контекст предыдущей сцены и выбранное действие.
                - title: 2-8 слов.
                - scenarioText: конкретная ситуация, которую можно сразу использовать как описание новой сцены.
                """;

        String userPrompt = """
                Название проекта: %s
                Предыдущая сцена (%s):
                %s

                Выбранное действие:
                %s
                """.formatted(
                trimToEmpty(project.getName()).isBlank() ? "Квест" : trimToEmpty(project.getName()),
                sourceNode.getId(),
                sourceDescription.isBlank() ? "Контекст сцены отсутствует." : sourceDescription,
                trimToEmpty(action.getText()).isBlank() ? action.getId() : trimToEmpty(action.getText())
        );

        systemPrompt = systemPrompt + """
                Additional rules:
                - Use at most one Story Bible signal per generated idea.
                - If Story Bible signals do not fit the current situation, use none.
                - Do not reveal multiple key facts or twists at once.
                - Do not introduce opposing_force without explicit scene motivation.
                """;
        userPrompt = userPrompt + "\n\n" + STORY_BIBLE_SPIKE_SOFT_CONTEXT;

        String cacheKey = "NEXT::%s::%s::%s::%s::%s::%s".formatted(
                STORY_BIBLE_SPIKE_VERSION,
                projectId,
                trimToEmpty(sourceNode.getId()),
                trimToEmpty(action.getId()),
                sourceDescription,
                trimToEmpty(action.getText())
        );
        FirstSceneIdeasResponse cached = getCachedIdeas(cacheKey);
        if (cached != null) {
            return cached;
        }

        FirstSceneIdeasResponse response = parseIdeas(
                aiClient.generate(systemPrompt, userPrompt, draftModel()),
                "AI did not return next scene ideas",
                "AI returned empty next scene ideas"
        );
        putCachedIdeas(cacheKey, response);
        return response;
    }

    private FirstSceneIdeasResponse parseIdeas(JsonNode root, String missingError, String emptyError) {
        JsonNode ideasNode = root.path("ideas");
        if (!ideasNode.isArray() || ideasNode.isEmpty()) {
            throw new BadRequestException(missingError);
        }

        List<FirstSceneIdeaView> ideas = new ArrayList<>();
        for (JsonNode ideaNode : ideasNode) {
            String title = ideaNode.path("title").asText("").trim();
            String scenarioText = ideaNode.path("scenarioText").asText("").trim();
            if (title.isBlank() || scenarioText.isBlank()) {
                continue;
            }
            ideas.add(new FirstSceneIdeaView(title, scenarioText));
            if (ideas.size() >= SCENE_IDEAS_LIMIT) {
                break;
            }
        }
        if (ideas.isEmpty()) {
            throw new BadRequestException(emptyError);
        }
        return new FirstSceneIdeasResponse(ideas);
    }

    private FirstSceneIdeasResponse getCachedIdeas(String key) {
        CachedIdeas cached = sceneIdeasCache.get(key);
        if (cached == null) {
            return null;
        }
        if (System.currentTimeMillis() > cached.expiresAtEpochMs()) {
            sceneIdeasCache.remove(key);
            return null;
        }
        return cached.response();
    }

    private void putCachedIdeas(String key, FirstSceneIdeasResponse response) {
        sceneIdeasCache.put(key, new CachedIdeas(response, System.currentTimeMillis() + IDEAS_CACHE_TTL_MS));
    }

    private String draftModel() {
        String model = openAiProperties.draftModel();
        if (model == null || model.isBlank()) {
            model = openAiProperties.model();
        }
        if (model == null || model.isBlank()) {
            return "gpt-5-mini";
        }
        return model;
    }

    private QuestProject getRequiredProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    private NodeWorkspace requiredWorkspace(QuestProject project) {
        if (project.getNodeWorkspace() == null) {
            project.setNodeWorkspace(NodeWorkspace.createEmpty());
        }
        return project.getNodeWorkspace();
    }

    private NodeGeneratorProjectView toView(QuestProject project) {
        return new NodeGeneratorProjectView(
                project.getId().toString(),
                project.getName(),
                project.getQuestStyle(),
                project.getStatus().name(),
                objectMapper.convertValue(requiredWorkspace(project), Object.class)
        );
    }

    private String toQuestDsl(QuestProject project, NodeWorkspace workspace) {
        List<WorkspaceNode> sourceNodes = workspace.getNodes() == null ? List.of() : workspace.getNodes();
        if (sourceNodes.isEmpty()) {
            throw new BadRequestException("Project has no scenes to build a quest");
        }

        Map<String, String> nodeIdMap = buildNodeIdMap(sourceNodes);
        String startSourceNodeId = resolveStartSourceNodeId(sourceNodes);
        List<WorkspaceNode> orderedNodes = orderNodes(sourceNodes, startSourceNodeId);
        Map<String, WorkspaceNode> nodeBySourceId = indexNodesBySourceId(sourceNodes);
        Map<String, String> edgeBySourceAndAction = indexEdgesBySourceAndAction(sourceNodes, nodeIdMap);

        String questId = toQuestId(project.getName(), project.getId());
        String title = normalizeTitle(project.getName());
        StringBuilder dsl = new StringBuilder();
        dsl.append("quest ").append(questId).append("\n\n");
        dsl.append("title ").append(quote(title)).append("\n\n");

        for (int i = 0; i < orderedNodes.size(); i++) {
            WorkspaceNode node = orderedNodes.get(i);
            String sourceNodeId = node.getId() == null ? "" : node.getId();
            String dslNodeId = nodeIdMap.getOrDefault(sourceNodeId.toUpperCase(Locale.ROOT), toNodeId(sourceNodeId, i + 1));
            dsl.append("node ").append(dslNodeId).append("\n");
            dsl.append(normalizeNodeText(node, dslNodeId)).append("\n");

            List<WorkspaceAction> actions = node.getActions() == null ? List.of() : node.getActions();
            for (int actionIndex = 0; actionIndex < actions.size(); actionIndex++) {
                WorkspaceAction action = actions.get(actionIndex);
                String actionId = action.getId() == null ? "" : action.getId();
                String actionText = normalizeActionText(action, actionIndex + 1);
                dsl.append("> ").append(actionText).append("\n");

                String edgeKey = edgeKey(sourceNodeId, actionId);
                String targetId = edgeBySourceAndAction.get(edgeKey);
                if (targetId != null && nodeBySourceId.containsKey(targetId.toUpperCase(Locale.ROOT))) {
                    dsl.append("-> ").append(nodeIdMap.get(targetId.toUpperCase(Locale.ROOT))).append("\n");
                } else {
                    dsl.append("@end\n");
                }
            }

            if (i < orderedNodes.size() - 1) {
                dsl.append("\n");
            }
        }
        return dsl.toString();
    }

    private Map<String, String> buildNodeIdMap(List<WorkspaceNode> nodes) {
        Map<String, String> map = new HashMap<>();
        Set<String> used = new HashSet<>();
        int fallbackIndex = 1;
        for (WorkspaceNode node : nodes) {
            String sourceId = node.getId() == null ? "" : node.getId();
            String key = sourceId.toUpperCase(Locale.ROOT);
            if (map.containsKey(key)) {
                continue;
            }
            String candidate = toNodeId(sourceId, fallbackIndex);
            while (used.contains(candidate.toUpperCase(Locale.ROOT))) {
                fallbackIndex++;
                candidate = toNodeId(sourceId, fallbackIndex);
            }
            map.put(key, candidate);
            used.add(candidate.toUpperCase(Locale.ROOT));
            fallbackIndex++;
        }
        return map;
    }

    private String resolveStartSourceNodeId(List<WorkspaceNode> nodes) {
        for (WorkspaceNode node : nodes) {
            String sourceNodeId = node.getSourceNodeId() == null ? "" : node.getSourceNodeId().trim();
            if (sourceNodeId.isBlank()) {
                return node.getId();
            }
        }
        return nodes.getFirst().getId();
    }

    private List<WorkspaceNode> orderNodes(List<WorkspaceNode> nodes, String startSourceNodeId) {
        List<WorkspaceNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparing(WorkspaceNode::getId, Comparator.nullsLast(String::compareToIgnoreCase)));
        int startIndex = -1;
        for (int i = 0; i < ordered.size(); i++) {
            String id = ordered.get(i).getId();
            if (id != null && id.equalsIgnoreCase(startSourceNodeId)) {
                startIndex = i;
                break;
            }
        }
        if (startIndex > 0) {
            WorkspaceNode start = ordered.remove(startIndex);
            ordered.addFirst(start);
        }
        return ordered;
    }

    private Map<String, WorkspaceNode> indexNodesBySourceId(List<WorkspaceNode> nodes) {
        Map<String, WorkspaceNode> map = new HashMap<>();
        for (WorkspaceNode node : nodes) {
            String nodeId = node.getId() == null ? "" : node.getId();
            map.put(nodeId.toUpperCase(Locale.ROOT), node);
        }
        return map;
    }

    private Map<String, String> indexEdgesBySourceAndAction(List<WorkspaceNode> nodes, Map<String, String> nodeIdMap) {
        Map<String, String> map = new HashMap<>();
        for (WorkspaceNode node : nodes) {
            String sourceNodeId = node.getSourceNodeId() == null ? "" : node.getSourceNodeId().trim();
            String sourceActionId = node.getSourceActionId() == null ? "" : node.getSourceActionId().trim();
            if (sourceNodeId.isBlank() || sourceActionId.isBlank()) {
                continue;
            }
            String targetSourceNodeId = node.getId() == null ? "" : node.getId().trim();
            if (targetSourceNodeId.isBlank()) {
                continue;
            }
            if (!nodeIdMap.containsKey(targetSourceNodeId.toUpperCase(Locale.ROOT))) {
                continue;
            }
            map.putIfAbsent(edgeKey(sourceNodeId, sourceActionId), targetSourceNodeId);
        }
        return map;
    }

    private String normalizeNodeText(WorkspaceNode node, String fallbackNodeId) {
        String state = trimToEmpty(node.getStateDescription());
        String action = trimToEmpty(node.getActionDescription());
        String legacy = trimToEmpty(node.getDescription());
        if (!state.isBlank()) {
            return state;
        }
        if (!action.isBlank()) {
            return action;
        }
        if (!legacy.isBlank()) {
            return legacy;
        }
        return "Сцена " + fallbackNodeId;
    }

    private String normalizeActionText(WorkspaceAction action, int fallbackIndex) {
        String text = action == null ? "" : trimToEmpty(action.getText());
        if (!text.isBlank()) {
            return text;
        }
        return "Действие " + fallbackIndex;
    }

    private String toQuestId(String name, UUID projectId) {
        String slug = slugify(name);
        if (slug.isBlank()) {
            slug = "quest";
        }
        String suffix = projectId.toString().replace("-", "").substring(0, 8);
        String candidate = slug + "_" + suffix;
        if (!Character.isLetter(candidate.charAt(0)) && candidate.charAt(0) != '_') {
            candidate = "q_" + candidate;
        }
        return candidate;
    }

    private String toNodeId(String sourceId, int fallbackIndex) {
        String slug = slugify(sourceId);
        if (slug.isBlank()) {
            slug = "node_" + fallbackIndex;
        }
        if (!Character.isLetter(slug.charAt(0)) && slug.charAt(0) != '_') {
            slug = "n_" + slug;
        }
        return slug;
    }

    private String normalizeTitle(String name) {
        String title = trimToEmpty(name);
        if (title.isBlank()) {
            return "Новый квест";
        }
        return title;
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String edgeKey(String sourceNodeId, String sourceActionId) {
        return sourceNodeId.trim().toUpperCase(Locale.ROOT) + "::" + sourceActionId.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String slugify(String value) {
        String raw = trimToEmpty(value).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        boolean lastDash = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            boolean allowed = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-';
            if (allowed) {
                out.append(ch);
                lastDash = false;
                continue;
            }
            if (!lastDash) {
                out.append('_');
                lastDash = true;
            }
        }
        String slug = out.toString();
        while (slug.startsWith("_")) {
            slug = slug.substring(1);
        }
        while (slug.endsWith("_")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug;
    }

    private record CachedIdeas(FirstSceneIdeasResponse response, long expiresAtEpochMs) {
    }
}
