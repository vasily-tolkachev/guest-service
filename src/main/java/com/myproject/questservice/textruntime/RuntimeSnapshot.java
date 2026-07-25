package com.myproject.questservice.textruntime;

import java.util.List;
import java.util.UUID;

public record RuntimeSnapshot(
        UUID sessionId,
        String currentLocationId,
        String description,
        List<ItemView> items,
        List<ExitView> exits,
        List<ItemView> inventory,
        List<NpcView> npcs
) {
    public record ItemView(String id, String name) {
    }

    public record ExitView(String actionText, String targetLocationId) {
    }

    public record NpcView(String id, String description, String dialogue) {
    }
}
