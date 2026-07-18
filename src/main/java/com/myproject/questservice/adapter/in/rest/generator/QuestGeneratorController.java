package com.myproject.questservice.adapter.in.rest.generator;

import com.myproject.questservice.adapter.in.rest.dto.generator.ConvertDslRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.CreateProjectRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.ImportProjectJsonRequest;
import com.myproject.questservice.adapter.in.rest.dto.generator.QuestProjectView;
import com.fasterxml.jackson.databind.JsonNode;
import com.myproject.questservice.application.port.in.generator.QuestGeneratorUseCase;
import com.myproject.questservice.domain.generator.StageType;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/generator/projects")
public class QuestGeneratorController {

    private final QuestGeneratorUseCase questGeneratorUseCase;

    public QuestGeneratorController(QuestGeneratorUseCase questGeneratorUseCase) {
        this.questGeneratorUseCase = questGeneratorUseCase;
    }

    @PostMapping
    public QuestProjectView createProject(@Valid @RequestBody CreateProjectRequest request) {
        return questGeneratorUseCase.createProject(request.name(), request.questStyle());
    }

    @GetMapping
    public List<QuestProjectView> listProjects() {
        return questGeneratorUseCase.listProjects();
    }

    @GetMapping("/{id}")
    public QuestProjectView getProject(@PathVariable UUID id) {
        return questGeneratorUseCase.getProject(id);
    }

    @PostMapping("/{id}/stages/{type}/generate")
    public QuestProjectView generateStage(@PathVariable UUID id, @PathVariable StageType type) {
        return questGeneratorUseCase.generateStage(id, type);
    }

    @PostMapping("/{id}/stages/{type}/steps/{step}/generate")
    public QuestProjectView generateStageStep(@PathVariable UUID id, @PathVariable StageType type, @PathVariable String step) {
        return questGeneratorUseCase.generateStageStep(id, type, step);
    }

    @PostMapping("/{id}/stages/{type}/approve")
    public QuestProjectView approveStage(@PathVariable UUID id, @PathVariable StageType type) {
        return questGeneratorUseCase.approveStage(id, type);
    }

    @PostMapping("/{id}/stages/CHAPTERS/chapters/{chapterId}/generate")
    public QuestProjectView generateChapter(@PathVariable UUID id, @PathVariable String chapterId) {
        return questGeneratorUseCase.generateChapter(id, chapterId);
    }

    @PostMapping("/{id}/stages/CHAPTERS/chapters/{chapterId}/approve")
    public QuestProjectView approveChapter(@PathVariable UUID id, @PathVariable String chapterId) {
        return questGeneratorUseCase.approveChapter(id, chapterId);
    }

    @PostMapping("/{id}/stages/SCENES/scenes/{sceneId}/generate")
    public QuestProjectView generateScene(@PathVariable UUID id, @PathVariable String sceneId) {
        return questGeneratorUseCase.generateScene(id, sceneId);
    }

    @PostMapping("/{id}/stages/SCENES/scenes/{sceneId}/approve")
    public QuestProjectView approveScene(@PathVariable UUID id, @PathVariable String sceneId) {
        return questGeneratorUseCase.approveScene(id, sceneId);
    }

    @PostMapping("/{id}/stages/ACHIEVEMENT_SCENES/achievements/{achievementId}/generate")
    public QuestProjectView generateAchievementScene(@PathVariable UUID id, @PathVariable String achievementId) {
        return questGeneratorUseCase.generateAchievementScene(id, achievementId);
    }

    @PostMapping("/{id}/stages/ACHIEVEMENT_SCENES/achievements/{achievementId}/approve")
    public QuestProjectView approveAchievementScene(@PathVariable UUID id, @PathVariable String achievementId) {
        return questGeneratorUseCase.approveAchievementScene(id, achievementId);
    }

    @GetMapping("/{id}/export-json")
    public Object exportProjectJson(@PathVariable UUID id) {
        return questGeneratorUseCase.exportProjectJson(id);
    }

    @PostMapping("/{id}/import-json")
    public QuestProjectView importProjectJson(@PathVariable UUID id, @Valid @RequestBody ImportProjectJsonRequest request) {
        return questGeneratorUseCase.importProjectJson(id, request.snapshotJson());
    }

    @PostMapping(value = "/{id}/export-dsl", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportDsl(@PathVariable UUID id) {
        return questGeneratorUseCase.exportDsl(id);
    }

    @PostMapping(value = "/convert-dsl", produces = MediaType.TEXT_PLAIN_VALUE)
    public String convertDsl(@RequestBody ConvertDslRequest request) {
        return questGeneratorUseCase.convertDsl(request.projectName(), request.questGraphJson());
    }
}
