package com.myproject.questservice.textruntime;

import java.util.List;
import java.util.UUID;

public record RuntimeSnapshot(
        UUID sessionId,
        String currentLocationId,
        String description,
        List<ItemView> items,
        List<ExitView> exits,
        List<ItemView> inventory
) {
    public record ItemView(String id, String name) {
    }

    public record ExitView(String actionText, String targetLocationId) {
    }
}

