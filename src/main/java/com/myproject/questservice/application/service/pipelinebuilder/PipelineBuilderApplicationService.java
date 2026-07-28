package com.myproject.questservice.application.service.pipelinebuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.PipelineProjectView;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.PipelineStageDependencyRequest;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.PipelineStageRevisionView;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.PipelineStageView;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.StagePromptPreviewView;
import com.myproject.questservice.application.port.in.pipelinebuilder.PipelineBuilderUseCase;
import com.myproject.questservice.application.port.out.generator.AiClient;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.application.service.ConflictException;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.domain.pipelinebuilder.PipelineMemoryMode;
import com.myproject.questservice.domain.pipelinebuilder.PipelineProject;
import com.myproject.questservice.domain.pipelinebuilder.PipelineStage;
import com.myproject.questservice.domain.pipelinebuilder.PipelineStageDependency;
import com.myproject.questservice.domain.pipelinebuilder.PipelineStageRevision;
import com.myproject.questservice.domain.pipelinebuilder.PipelineStageStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PipelineBuilderApplicationService implements PipelineBuilderUseCase {
    private final PipelineProjectRepository repository;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    public PipelineBuilderApplicationService(PipelineProjectRepository repository, AiClient aiClient, ObjectMapper objectMapper) {
        this.repository = repository;
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public PipelineProjectView createProject(String name) {
        String normalized = normalizeName(name);
        PipelineProject project = PipelineProject.create(normalized);
        repository.save(project);
        return toView(project);
    }

    @Override
    public List<PipelineProjectView> listProjects() {
        return repository.findAll().stream().map(this::toView).toList();
    }

    @Override
    public PipelineProjectView getProject(UUID projectId) {
        return toView(getProjectOrThrow(projectId));
    }

    @Override
    public PipelineProjectView addStage(
            UUID projectId,
            String stageId,
            String name,
            String systemPromptTemplate,
            String userPromptTemplate,
            Object args,
            String memoryMode,
            List<String> memorySources,
            List<PipelineStageDependencyRequest> dependencies
    ) {
        PipelineProject project = getProjectOrThrow(projectId);
        String normalizedStageId = normalizeStageId(stageId, project.getStages().size() + 1);
        if (project.findStage(normalizedStageId).isPresent()) {
            throw new ConflictException("Stage already exists: " + normalizedStageId);
        }

        PipelineStage stage = new PipelineStage(
                normalizedStageId,
                nonBlankOrDefault(name, normalizedStageId),
                true,
                nonBlankOrDefault(systemPromptTemplate, ""),
                nonBlankOrDefault(userPromptTemplate, ""),
                objectMapper.valueToTree(args == null ? Map.of() : args),
                parseMemoryMode(memoryMode),
                memorySources == null ? List.of() : memorySources,
                parseDependencies(dependencies)
        );
        stage.setStatus(PipelineStageStatus.READY);
        project.getStages().add(stage);
        repository.save(project);
        return toView(project);
    }

    @Override
    public PipelineProjectView updateStage(
            UUID projectId,
            String stageId,
            String name,
            Boolean enabled,
            String systemPromptTemplate,
            String userPromptTemplate,
            Object args,
            String memoryMode,
            List<String> memorySources,
            List<PipelineStageDependencyRequest> dependencies
    ) {
        PipelineProject project = getProjectOrThrow(projectId);
        PipelineStage stage = getStageOrThrow(project, stageId);

        if (name != null) {
            stage.setName(nonBlankOrDefault(name, stage.getName()));
        }
        if (enabled != null) {
            stage.setEnabled(enabled);
        }
        if (systemPromptTemplate != null) {
            stage.setSystemPromptTemplate(systemPromptTemplate);
        }
        if (userPromptTemplate != null) {
            stage.setUserPromptTemplate(userPromptTemplate);
        }
        if (args != null) {
            stage.setArgs(objectMapper.valueToTree(args));
        }
        if (memoryMode != null) {
            stage.setMemoryMode(parseMemoryMode(memoryMode));
        }
        if (memorySources != null) {
            stage.setMemorySources(new ArrayList<>(memorySources));
        }
        if (dependencies != null) {
            stage.setDependencies(parseDependencies(dependencies));
        }

        repository.save(project);
        return toView(project);
    }

    @Override
    public PipelineProjectView deleteStage(UUID projectId, String stageId) {
        PipelineProject project = getProjectOrThrow(projectId);
        int before = project.getStages().size();
        project.setStages(project.getStages().stream()
                .filter(stage -> !stage.getId().equalsIgnoreCase(stageId))
                .collect(Collectors.toCollection(ArrayList::new)));
        if (project.getStages().size() == before) {
            throw new NotFoundException("Stage not found: " + stageId);
        }
        repository.save(project);
        return toView(project);
    }

    @Override
    public StagePromptPreviewView previewStagePrompt(UUID projectId, String stageId, String systemPromptOverride, String userPromptOverride, Object argsOverride) {
        PipelineProject project = getProjectOrThrow(projectId);
        PipelineStage stage = getStageOrThrow(project, stageId);
        JsonNode args = argsOverride == null ? stage.getArgs() : objectMapper.valueToTree(argsOverride);
        String systemPrompt = renderPrompt(nonBlankOrDefault(systemPromptOverride, stage.getSystemPromptTemplate()), args);
        String userPrompt = renderPrompt(nonBlankOrDefault(userPromptOverride, stage.getUserPromptTemplate()), args);
        String memory = buildMemoryContext(project, stage);
        if (!memory.isBlank()) {
            userPrompt = userPrompt + "\n\nMEMORY_CONTEXT_JSON:\n" + memory;
        }
        String safeSystemPrompt = ensureJsonMention(systemPrompt);
        String safeUserPrompt = ensureJsonMention(userPrompt);
        return new StagePromptPreviewView(safeSystemPrompt, safeUserPrompt, toPlainJson(args), memory);
    }

    @Override
    public PipelineProjectView runStage(UUID projectId, String stageId, String systemPromptOverride, String userPromptOverride, Object argsOverride) {
        PipelineProject project = getProjectOrThrow(projectId);
        PipelineStage stage = getStageOrThrow(project, stageId);
        ensureRunnable(project, stage);

        stage.setStatus(PipelineStageStatus.RUNNING);
        StagePromptPreviewView preview = previewStagePrompt(projectId, stageId, systemPromptOverride, userPromptOverride, argsOverride);
        JsonNode generated = aiClient.generate(preview.systemPrompt(), preview.userPrompt());

        int revisionNumber = stage.getCurrentRevision() == null ? 1 : stage.getCurrentRevision().revisionNumber() + 1;
        PipelineStageRevision revision = new PipelineStageRevision(
                revisionNumber,
                generated,
                Instant.now(),
                preview.systemPrompt(),
                preview.userPrompt()
        );
        stage.setCurrentRevision(revision);
        stage.getRevisions().add(revision);
        stage.setApproved(false);
        stage.setStatus(PipelineStageStatus.REVIEW);
        repository.save(project);
        return toView(project);
    }

    @Override
    public PipelineProjectView approveStage(UUID projectId, String stageId) {
        PipelineProject project = getProjectOrThrow(projectId);
        PipelineStage stage = getStageOrThrow(project, stageId);
        if (stage.getCurrentRevision() == null) {
            throw new ConflictException("Stage has no generated revision: " + stageId);
        }
        stage.setApproved(true);
        stage.setStatus(PipelineStageStatus.APPROVED);
        repository.save(project);
        return toView(project);
    }

    @Override
    public Object exportProject(UUID projectId) {
        PipelineProject project = getProjectOrThrow(projectId);
        return toSnapshot(project);
    }

    @Override
    public PipelineProjectView importProject(UUID projectId, Object snapshot) {
        PipelineProject project = getProjectOrThrow(projectId);
        JsonNode root = objectMapper.valueToTree(snapshot);
        if (!root.isObject()) {
            throw new BadRequestException("snapshot must be object");
        }
        JsonNode stages = root.path("stages");
        if (!stages.isArray()) {
            throw new BadRequestException("snapshot.stages must be array");
        }
        List<PipelineStage> importedStages = new ArrayList<>();
        for (JsonNode node : stages) {
            String stageId = text(node.path("id"));
            if (stageId.isBlank()) {
                continue;
            }
            PipelineStage imported = new PipelineStage(
                    stageId,
                    nonBlankOrDefault(text(node.path("name")), stageId),
                    node.path("enabled").asBoolean(true),
                    text(node.path("systemPromptTemplate")),
                    text(node.path("userPromptTemplate")),
                    node.path("args"),
                    parseMemoryMode(text(node.path("memoryMode"))),
                    toStringList(node.path("memorySources")),
                    parseDependencies(node.path("dependencies"))
            );
            imported.setStatus(parseStatus(text(node.path("status"))));
            imported.setApproved(node.path("approved").asBoolean(false));
            JsonNode revisions = node.path("revisions");
            if (revisions.isArray()) {
                for (JsonNode revisionNode : revisions) {
                    PipelineStageRevision revision = new PipelineStageRevision(
                            revisionNode.path("revisionNumber").asInt(1),
                            revisionNode.path("outputJson"),
                            parseInstant(text(revisionNode.path("createdAt"))),
                            text(revisionNode.path("systemPromptUsed")),
                            text(revisionNode.path("userPromptUsed"))
                    );
                    imported.getRevisions().add(revision);
                }
            }
            if (!imported.getRevisions().isEmpty()) {
                imported.setCurrentRevision(imported.getRevisions().get(imported.getRevisions().size() - 1));
            }
            importedStages.add(imported);
        }
        project.setStages(importedStages);
        repository.save(project);
        return toView(project);
    }

    private PipelineProject getProjectOrThrow(UUID projectId) {
        return repository.findById(projectId).orElseThrow(() -> new NotFoundException("Pipeline project not found: " + projectId));
    }

    private PipelineStage getStageOrThrow(PipelineProject project, String stageId) {
        return project.findStage(stageId).orElseThrow(() -> new NotFoundException("Stage not found: " + stageId));
    }

    private void ensureRunnable(PipelineProject project, PipelineStage stage) {
        if (!stage.isEnabled()) {
            throw new ConflictException("Stage is disabled: " + stage.getId());
        }
        for (PipelineStageDependency dependency : stage.getDependencies()) {
            PipelineStage required = project.findStage(dependency.stageId())
                    .orElseThrow(() -> new ConflictException("Dependency stage not found: " + dependency.stageId()));
            if (required.getStatus() != dependency.requiredStatus()) {
                throw new ConflictException("Dependency is not satisfied: " + dependency.stageId() + " must be " + dependency.requiredStatus());
            }
        }
    }

    private String buildMemoryContext(PipelineProject project, PipelineStage stage) {
        if (stage.getMemoryMode() == PipelineMemoryMode.NONE) {
            return "";
        }
        List<PipelineStage> sourceStages;
        if (stage.getMemoryMode() == PipelineMemoryMode.ALL_PREVIOUS) {
            sourceStages = new ArrayList<>();
            for (PipelineStage candidate : project.getStages()) {
                if (candidate.getId().equalsIgnoreCase(stage.getId())) {
                    break;
                }
                sourceStages.add(candidate);
            }
        } else {
            Set<String> ids = stage.getMemorySources().stream().map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            sourceStages = project.getStages().stream()
                    .filter(candidate -> ids.contains(candidate.getId().toLowerCase(Locale.ROOT)))
                    .toList();
        }

        ObjectNode root = objectMapper.createObjectNode();
        for (PipelineStage source : sourceStages) {
            if (source.getCurrentRevision() == null) {
                continue;
            }
            root.set(source.getId(), source.getCurrentRevision().outputJson());
        }
        return root.toString();
    }

    private String renderPrompt(String template, JsonNode args) {
        String rendered = nonBlankOrDefault(template, "");
        if (args == null || !args.isObject()) {
            return rendered;
        }
        for (IteratorEntry entry : iterateFields(args)) {
            String token = "{{" + entry.key + "}}";
            rendered = rendered.replace(token, entry.value);
        }
        return rendered;
    }

    private List<IteratorEntry> iterateFields(JsonNode args) {
        List<IteratorEntry> entries = new ArrayList<>();
        args.fields().forEachRemaining(field -> entries.add(new IteratorEntry(field.getKey(), field.getValue().asText(""))));
        return entries;
    }

    private PipelineMemoryMode parseMemoryMode(String memoryMode) {
        if (memoryMode == null || memoryMode.isBlank()) {
            return PipelineMemoryMode.NONE;
        }
        try {
            return PipelineMemoryMode.valueOf(memoryMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown memoryMode: " + memoryMode);
        }
    }

    private List<PipelineStageDependency> parseDependencies(List<PipelineStageDependencyRequest> dependencies) {
        if (dependencies == null) {
            return new ArrayList<>();
        }
        List<PipelineStageDependency> out = new ArrayList<>();
        for (PipelineStageDependencyRequest item : dependencies) {
            if (item == null || item.stageId() == null || item.stageId().isBlank()) {
                continue;
            }
            out.add(new PipelineStageDependency(item.stageId().trim(), parseStatus(item.requiredStatus())));
        }
        return out;
    }

    private List<PipelineStageDependency> parseDependencies(JsonNode dependencies) {
        if (dependencies == null || !dependencies.isArray()) {
            return new ArrayList<>();
        }
        List<PipelineStageDependency> out = new ArrayList<>();
        for (JsonNode node : dependencies) {
            String stageId = text(node.path("stageId"));
            if (stageId.isBlank()) {
                continue;
            }
            out.add(new PipelineStageDependency(stageId, parseStatus(text(node.path("requiredStatus")))));
        }
        return out;
    }

    private PipelineStageStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return PipelineStageStatus.READY;
        }
        try {
            return PipelineStageStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown status: " + value);
        }
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new BadRequestException("Project name is required");
        }
        return normalized;
    }

    private String normalizeStageId(String stageId, int index) {
        String normalized = stageId == null ? "" : stageId.trim();
        if (normalized.isBlank()) {
            return "stage_" + index;
        }
        return normalized;
    }

    private String text(JsonNode node) {
        return node == null ? "" : node.asText("").trim();
    }

    private String nonBlankOrDefault(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    private String ensureJsonMention(String prompt) {
        String value = nonBlankOrDefault(prompt, "");
        if (value.toLowerCase(Locale.ROOT).contains("json")) {
            return value;
        }
        return value + "\n\nReturn strictly valid JSON object.";
    }

    private Object toPlainJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private List<String> toStringList(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            String value = text(item);
            if (!value.isBlank()) {
                out.add(value);
            }
        }
        return out;
    }

    private PipelineProjectView toView(PipelineProject project) {
        return new PipelineProjectView(
                project.getId(),
                project.getName(),
                project.getCreatedAt(),
                project.getStages().stream().map(this::toStageView).toList()
        );
    }

    private PipelineStageView toStageView(PipelineStage stage) {
        return new PipelineStageView(
                stage.getId(),
                stage.getName(),
                stage.isEnabled(),
                stage.getSystemPromptTemplate(),
                stage.getUserPromptTemplate(),
                toPlainJson(stage.getArgs()),
                stage.getMemoryMode().name(),
                List.copyOf(stage.getMemorySources()),
                stage.getDependencies().stream()
                        .map(dependency -> new PipelineStageDependencyRequest(dependency.stageId(), dependency.requiredStatus().name()))
                        .toList(),
                stage.getStatus().name(),
                stage.isApproved(),
                stage.getCurrentRevision() == null ? null : new PipelineStageRevisionView(
                        stage.getCurrentRevision().revisionNumber(),
                        stage.getCurrentRevision().outputJson(),
                        stage.getCurrentRevision().createdAt(),
                        stage.getCurrentRevision().systemPromptUsed(),
                        stage.getCurrentRevision().userPromptUsed()
                )
        );
    }

    private Object toSnapshot(PipelineProject project) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", project.getId().toString());
        root.put("name", project.getName());
        root.put("createdAt", project.getCreatedAt().toString());
        ArrayNode stages = objectMapper.createArrayNode();
        for (PipelineStage stage : project.getStages()) {
            ObjectNode stageNode = objectMapper.createObjectNode();
            stageNode.put("id", stage.getId());
            stageNode.put("name", stage.getName());
            stageNode.put("enabled", stage.isEnabled());
            stageNode.put("systemPromptTemplate", stage.getSystemPromptTemplate());
            stageNode.put("userPromptTemplate", stage.getUserPromptTemplate());
            stageNode.set("args", stage.getArgs() == null ? objectMapper.createObjectNode() : stage.getArgs());
            stageNode.put("memoryMode", stage.getMemoryMode().name());
            ArrayNode memorySources = objectMapper.createArrayNode();
            for (String source : stage.getMemorySources()) {
                memorySources.add(source);
            }
            stageNode.set("memorySources", memorySources);
            ArrayNode dependencies = objectMapper.createArrayNode();
            for (PipelineStageDependency dependency : stage.getDependencies()) {
                ObjectNode dep = objectMapper.createObjectNode();
                dep.put("stageId", dependency.stageId());
                dep.put("requiredStatus", dependency.requiredStatus().name());
                dependencies.add(dep);
            }
            stageNode.set("dependencies", dependencies);
            stageNode.put("status", stage.getStatus().name());
            stageNode.put("approved", stage.isApproved());
            ArrayNode revisions = objectMapper.createArrayNode();
            for (PipelineStageRevision revision : stage.getRevisions()) {
                ObjectNode rev = objectMapper.createObjectNode();
                rev.put("revisionNumber", revision.revisionNumber());
                rev.set("outputJson", revision.outputJson());
                rev.put("createdAt", revision.createdAt().toString());
                rev.put("systemPromptUsed", revision.systemPromptUsed());
                rev.put("userPromptUsed", revision.userPromptUsed());
                revisions.add(rev);
            }
            stageNode.set("revisions", revisions);
            stages.add(stageNode);
        }
        root.set("stages", stages);
        return root;
    }

    private record IteratorEntry(String key, String value) {}
}
