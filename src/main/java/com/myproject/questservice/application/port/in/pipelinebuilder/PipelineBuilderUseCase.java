package com.myproject.questservice.application.port.in.pipelinebuilder;

import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.PipelineProjectView;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.StagePromptPreviewView;

import java.util.List;
import java.util.UUID;

public interface PipelineBuilderUseCase {
    PipelineProjectView createProject(String name);

    List<PipelineProjectView> listProjects();

    PipelineProjectView getProject(UUID projectId);

    PipelineProjectView addStage(
            UUID projectId,
            String stageId,
            String name,
            String systemPromptTemplate,
            String userPromptTemplate,
            Object args,
            String memoryMode,
            List<String> memorySources,
            List<com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.PipelineStageDependencyRequest> dependencies
    );

    PipelineProjectView updateStage(
            UUID projectId,
            String stageId,
            String name,
            Boolean enabled,
            String systemPromptTemplate,
            String userPromptTemplate,
            Object args,
            String memoryMode,
            List<String> memorySources,
            List<com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.PipelineStageDependencyRequest> dependencies
    );

    PipelineProjectView deleteStage(UUID projectId, String stageId);

    StagePromptPreviewView previewStagePrompt(UUID projectId, String stageId, String systemPromptOverride, String userPromptOverride, Object argsOverride);

    PipelineProjectView runStage(UUID projectId, String stageId, String systemPromptOverride, String userPromptOverride, Object argsOverride);

    PipelineProjectView approveStage(UUID projectId, String stageId);

    Object exportProject(UUID projectId);

    PipelineProjectView importProject(UUID projectId, Object snapshot);
}
