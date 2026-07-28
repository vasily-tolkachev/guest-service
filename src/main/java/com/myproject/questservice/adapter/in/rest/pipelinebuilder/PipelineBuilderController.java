package com.myproject.questservice.adapter.in.rest.pipelinebuilder;

import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.CreatePipelineProjectRequest;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.CreatePipelineStageRequest;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.ImportPipelineProjectRequest;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.PipelineProjectView;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.RunPipelineStageRequest;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.StagePromptPreviewView;
import com.myproject.questservice.adapter.in.rest.dto.pipelinebuilder.UpdatePipelineStageRequest;
import com.myproject.questservice.application.port.in.pipelinebuilder.PipelineBuilderUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pipeline-builder/projects")
public class PipelineBuilderController {
    private final PipelineBuilderUseCase pipelineBuilderUseCase;

    public PipelineBuilderController(PipelineBuilderUseCase pipelineBuilderUseCase) {
        this.pipelineBuilderUseCase = pipelineBuilderUseCase;
    }

    @PostMapping
    public PipelineProjectView createProject(@Valid @RequestBody CreatePipelineProjectRequest request) {
        return pipelineBuilderUseCase.createProject(request.name());
    }

    @GetMapping
    public List<PipelineProjectView> listProjects() {
        return pipelineBuilderUseCase.listProjects();
    }

    @GetMapping("/{id}")
    public PipelineProjectView getProject(@PathVariable UUID id) {
        return pipelineBuilderUseCase.getProject(id);
    }

    @PostMapping("/{id}/stages")
    public PipelineProjectView addStage(@PathVariable UUID id, @RequestBody(required = false) CreatePipelineStageRequest request) {
        CreatePipelineStageRequest payload = request == null
                ? new CreatePipelineStageRequest(null, null, null, null, null, null, null, null)
                : request;
        return pipelineBuilderUseCase.addStage(
                id,
                payload.stageId(),
                payload.name(),
                payload.systemPromptTemplate(),
                payload.userPromptTemplate(),
                payload.args(),
                payload.memoryMode(),
                payload.memorySources(),
                payload.dependencies()
        );
    }

    @PutMapping("/{id}/stages/{stageId}")
    public PipelineProjectView updateStage(
            @PathVariable UUID id,
            @PathVariable String stageId,
            @RequestBody(required = false) UpdatePipelineStageRequest request
    ) {
        UpdatePipelineStageRequest payload = request == null
                ? new UpdatePipelineStageRequest(null, null, null, null, null, null, null, null)
                : request;
        return pipelineBuilderUseCase.updateStage(
                id,
                stageId,
                payload.name(),
                payload.enabled(),
                payload.systemPromptTemplate(),
                payload.userPromptTemplate(),
                payload.args(),
                payload.memoryMode(),
                payload.memorySources(),
                payload.dependencies()
        );
    }

    @DeleteMapping("/{id}/stages/{stageId}")
    public PipelineProjectView deleteStage(@PathVariable UUID id, @PathVariable String stageId) {
        return pipelineBuilderUseCase.deleteStage(id, stageId);
    }

    @PostMapping("/{id}/stages/{stageId}/preview")
    public StagePromptPreviewView previewStage(
            @PathVariable UUID id,
            @PathVariable String stageId,
            @RequestBody(required = false) RunPipelineStageRequest request
    ) {
        RunPipelineStageRequest payload = request == null
                ? new RunPipelineStageRequest(null, null, null)
                : request;
        return pipelineBuilderUseCase.previewStagePrompt(id, stageId, payload.systemPrompt(), payload.userPrompt(), payload.args());
    }

    @PostMapping("/{id}/stages/{stageId}/run")
    public PipelineProjectView runStage(
            @PathVariable UUID id,
            @PathVariable String stageId,
            @RequestBody(required = false) RunPipelineStageRequest request
    ) {
        RunPipelineStageRequest payload = request == null
                ? new RunPipelineStageRequest(null, null, null)
                : request;
        return pipelineBuilderUseCase.runStage(id, stageId, payload.systemPrompt(), payload.userPrompt(), payload.args());
    }

    @PostMapping("/{id}/stages/{stageId}/approve")
    public PipelineProjectView approveStage(@PathVariable UUID id, @PathVariable String stageId) {
        return pipelineBuilderUseCase.approveStage(id, stageId);
    }

    @GetMapping("/{id}/export")
    public Object exportProject(@PathVariable UUID id) {
        return pipelineBuilderUseCase.exportProject(id);
    }

    @PostMapping("/{id}/import")
    public PipelineProjectView importProject(@PathVariable UUID id, @RequestBody ImportPipelineProjectRequest request) {
        return pipelineBuilderUseCase.importProject(id, request == null ? null : request.snapshot());
    }
}
