package com.myproject.questservice.textruntime.application.port.in.dto;

import java.util.List;
import java.util.UUID;

public record RuntimeSnapshot(
        UUID sessionId,
        String currentLocationId,
        String description,
        List<ItemView> items,
        List<ExitView> exits,
        List<ActionView> availableActions,
        List<ItemView> inventory,
        List<NpcView> npcs
) {
    public record ItemView(String id, String name) {
    }

    public record ExitView(String actionText, String targetLocationId) {
    }

    public record ActionView(String id, String description, String targetId) {
    }

    public record NpcView(String id, String description, String dialogue) {
    }
}
