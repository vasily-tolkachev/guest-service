package com.myproject.questservice.textruntime.application.port.in.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RuntimeSnapshot(
        UUID sessionId,
        String currentLocationId,
        String description,
        List<ItemView> items,
        List<ExitView> exits,
        List<ActionView> availableActions,
        List<ItemView> inventory,
        List<NpcView> npcs,
        List<ObjectView> objects,
        List<ObjectiveView> objectives,
        List<String> knownFacts,
        Map<String, String> objectStates,
        Map<String, String> characterStates
) {
    public record ItemView(String id, String name) {
    }

    public record ExitView(String actionText, String targetLocationId) {
    }

    public record ActionView(String id, String description, String targetId, List<String> requiredItems) {
    }

    public record NpcView(String id, String description, String dialogue) {
    }

    public record ObjectView(String id, String description) {
    }

    public record ObjectiveView(String id, String title, String description, boolean completed, List<ObjectiveView> children) {
    }
}
