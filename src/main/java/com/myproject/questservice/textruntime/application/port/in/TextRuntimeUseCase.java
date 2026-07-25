package com.myproject.questservice.textruntime.application.port.in;

import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestSummary;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeActionResult;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeGenerationStatus;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeSnapshot;

import java.util.List;
import java.util.UUID;

public interface TextRuntimeUseCase {
    List<RuntimeQuestSummary> listRuntimeQuests();

    RuntimeSnapshot startRuntimeQuest(String questId);

    RuntimeSnapshot inspect(UUID sessionId);

    RuntimeSnapshot move(UUID sessionId, String locationId);

    RuntimeSnapshot take(UUID sessionId, String itemId);

    RuntimeSnapshot use(UUID sessionId, String itemId, String targetId);

    RuntimeActionResult interact(UUID sessionId, String targetId);

    String inspectTarget(UUID sessionId, String targetId);

    RuntimeGenerationStatus generateScene(UUID sessionId);

    RuntimeGenerationStatus generateActions(UUID sessionId);

    RuntimeGenerationStatus generationStatus(UUID sessionId);
}
