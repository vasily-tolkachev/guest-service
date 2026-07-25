package com.myproject.questservice.adapter.in.rest;

import com.myproject.questservice.textruntime.RuntimeSnapshot;
import com.myproject.questservice.textruntime.RuntimeQuestSummary;
import com.myproject.questservice.textruntime.TextRuntimeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
    private final TextRuntimeService runtimeService;

    public TextRuntimeController(TextRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @GetMapping("/quests")
    public List<RuntimeQuestSummary> listRuntimeQuests() {
        return runtimeService.listRuntimeQuests();
    }

    @PostMapping("/quests/{questId}/start")
    public RuntimeSnapshot startQuest(@PathVariable String questId) {
        return runtimeService.startRuntimeQuest(questId);
    }

    @GetMapping("/sessions/{sessionId}/inspect")
    public RuntimeSnapshot inspect(@PathVariable UUID sessionId) {
        return runtimeService.inspect(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/move")
    public RuntimeSnapshot move(@PathVariable UUID sessionId, @Valid @RequestBody MoveRequest request) {
        return runtimeService.move(sessionId, request.locationId());
    }

    @PostMapping("/sessions/{sessionId}/take")
    public RuntimeSnapshot take(@PathVariable UUID sessionId, @Valid @RequestBody TakeRequest request) {
        return runtimeService.take(sessionId, request.itemId());
    }

    @PostMapping("/sessions/{sessionId}/use")
    public RuntimeSnapshot use(@PathVariable UUID sessionId, @Valid @RequestBody UseRequest request) {
        return runtimeService.use(sessionId, request.itemId(), request.targetId());
    }

    public record MoveRequest(@NotBlank String locationId) {
    }

    public record TakeRequest(@NotBlank String itemId) {
    }

    public record UseRequest(@NotBlank String itemId, @NotBlank String targetId) {
    }
}
