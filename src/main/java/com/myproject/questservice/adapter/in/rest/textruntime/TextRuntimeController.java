package com.myproject.questservice.adapter.in.rest.textruntime;

import com.myproject.questservice.textruntime.application.port.in.TextRuntimeUseCase;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestSummary;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeSnapshot;
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
    private final TextRuntimeUseCase runtimeUseCase;

    public TextRuntimeController(TextRuntimeUseCase runtimeUseCase) {
        this.runtimeUseCase = runtimeUseCase;
    }

    @GetMapping("/quests")
    public List<RuntimeQuestSummary> listRuntimeQuests() {
        return runtimeUseCase.listRuntimeQuests();
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

    public record MoveRequest(@NotBlank String locationId) {
    }

    public record TakeRequest(@NotBlank String itemId) {
    }

    public record UseRequest(@NotBlank String itemId, @NotBlank String targetId) {
    }
}
