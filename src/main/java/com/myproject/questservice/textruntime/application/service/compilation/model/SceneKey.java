package com.myproject.questservice.textruntime.application.service.compilation.model;

public record SceneKey(
        String questId,
        String locationId,
        String stateSignature
) {
}
