package com.myproject.questservice.textruntime.application.port.in.dto;

import java.util.List;
import java.util.UUID;

public record RuntimeGenerationStatus(
        UUID sessionId,
        String sceneId,
        boolean sceneGenerated,
        boolean actionsGenerated,
        String generatedSceneText,
        List<GeneratedAction> generatedActions
) {
    public record GeneratedAction(
            String id,
            String label,
            String targetId
    ) {
    }
}
