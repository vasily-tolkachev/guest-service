package com.myproject.questservice.adapter.in.rest.textruntime;

import com.myproject.questservice.textruntime.application.port.in.TextRuntimeUseCase;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeActionResult;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeGenerationStatus;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestExport;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestImportRequest;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestSummary;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeSnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/text-runtime")
public class TextRuntimeController {
    private final TextRuntimeUseCase runtimeUseCase;

    public TextRuntimeController(TextRuntimeUseCase runtimeUseCase) {
        this.runtimeUseCase = runtimeUseCase;
    }

    @GetMapping("/quests")
    public List<RuntimeQuestSummary> listRuntimeQuests() {
        return runtimeUseCase.listRuntimeQuests();
    }

    @PostMapping("/quests/import")
    public RuntimeQuestSummary importQuest(@Valid @RequestBody RuntimeQuestImportRequest request) {
        return runtimeUseCase.importRuntimeQuest(request);
    }

    @GetMapping("/quests/{questId}/export")
    public ResponseEntity<RuntimeQuestExport> exportQuest(@PathVariable String questId) {
        RuntimeQuestExport payload = runtimeUseCase.exportRuntimeQuest(questId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename((payload.id() == null || payload.id().isBlank() ? "runtime-quest" : payload.id()) + ".json")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload);
    }

    @PostMapping("/quests/{questId}/start")
    public RuntimeSnapshot startQuest(@PathVariable String questId) {
        return runtimeUseCase.startRuntimeQuest(questId);
    }

    @GetMapping("/sessions/{sessionId}/inspect")
    public RuntimeSnapshot inspect(@PathVariable UUID sessionId) {
        return runtimeUseCase.inspect(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/move")
    public RuntimeSnapshot move(@PathVariable UUID sessionId, @Valid @RequestBody MoveRequest request) {
        return runtimeUseCase.move(sessionId, request.locationId());
    }

    @PostMapping("/sessions/{sessionId}/take")
    public RuntimeSnapshot take(@PathVariable UUID sessionId, @Valid @RequestBody TakeRequest request) {
        return runtimeUseCase.take(sessionId, request.itemId());
    }

    @PostMapping("/sessions/{sessionId}/use")
    public RuntimeSnapshot use(@PathVariable UUID sessionId, @Valid @RequestBody UseRequest request) {
        return runtimeUseCase.use(sessionId, request.itemId(), request.targetId());
    }

    @PostMapping("/sessions/{sessionId}/interact")
    public RuntimeActionResult interact(@PathVariable UUID sessionId, @Valid @RequestBody InteractRequest request) {
        return runtimeUseCase.interact(sessionId, request.targetId());
    }

    @PostMapping("/sessions/{sessionId}/execute-action")
    public RuntimeSnapshot executeAction(@PathVariable UUID sessionId, @Valid @RequestBody ExecuteActionRequest request) {
        return runtimeUseCase.executeAction(sessionId, request.actionId());
    }

    @PostMapping("/sessions/{sessionId}/inspect-target")
    public InspectTargetResponse inspectTarget(@PathVariable UUID sessionId, @Valid @RequestBody InspectTargetRequest request) {
        return new InspectTargetResponse(runtimeUseCase.inspectTarget(sessionId, request.targetId()));
    }

    @PostMapping("/sessions/{sessionId}/generate-scene")
    public RuntimeGenerationStatus generateScene(@PathVariable UUID sessionId) {
        return runtimeUseCase.generateScene(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/generate-actions")
    public RuntimeGenerationStatus generateActions(@PathVariable UUID sessionId) {
        return runtimeUseCase.generateActions(sessionId);
    }

    @GetMapping("/sessions/{sessionId}/generation-status")
    public RuntimeGenerationStatus generationStatus(@PathVariable UUID sessionId) {
        return runtimeUseCase.generationStatus(sessionId);
    }

    public record MoveRequest(@NotBlank String locationId) {
    }

    public record TakeRequest(@NotBlank String itemId) {
    }

    public record UseRequest(@NotBlank String itemId, @NotBlank String targetId) {
    }

    public record InteractRequest(@NotBlank String targetId) {
    }

    public record ExecuteActionRequest(@NotBlank String actionId) {
    }

    public record InspectTargetRequest(@NotBlank String targetId) {
    }

    public record InspectTargetResponse(String description) {
    }
}
