package com.myproject.questservice.domain.pipelinebuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PipelineStage {
    private String id;
    private String name;
    private boolean enabled;
    private String systemPromptTemplate;
    private String userPromptTemplate;
    private JsonNode args;
    private PipelineMemoryMode memoryMode;
    private List<String> memorySources;
    private List<PipelineStageDependency> dependencies;
    private PipelineStageStatus status;
    private boolean approved;
    private PipelineStageRevision currentRevision;
    private List<PipelineStageRevision> revisions;

    public PipelineStage(
            String id,
            String name,
            boolean enabled,
            String systemPromptTemplate,
            String userPromptTemplate,
            JsonNode args,
            PipelineMemoryMode memoryMode,
            List<String> memorySources,
            List<PipelineStageDependency> dependencies
    ) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = userPromptTemplate;
        this.args = args == null ? JsonNodeFactory.instance.objectNode() : args;
        this.memoryMode = memoryMode == null ? PipelineMemoryMode.NONE : memoryMode;
        this.memorySources = memorySources == null ? new ArrayList<>() : new ArrayList<>(memorySources);
        this.dependencies = dependencies == null ? new ArrayList<>() : new ArrayList<>(dependencies);
        this.status = PipelineStageStatus.NOT_STARTED;
        this.approved = false;
        this.currentRevision = null;
        this.revisions = new ArrayList<>();
    }
}
