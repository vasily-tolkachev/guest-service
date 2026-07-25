package com.myproject.questservice.textruntime.application.service.compilation.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record SceneGenerationRequest(
        SceneKey key,
        String locationDescription,
        List<String> visibleItems,
        List<String> visibleNpcs,
        List<String> exits,
        List<String> availableActions,
        List<String> inventory,
        Set<String> progressFlags,
        Set<String> knownFacts,
        Map<String, String> objectStates,
        Map<String, String> characterStates
) {
}
