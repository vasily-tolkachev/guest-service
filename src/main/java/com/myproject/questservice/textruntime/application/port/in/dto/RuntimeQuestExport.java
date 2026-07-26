package com.myproject.questservice.textruntime.application.port.in.dto;

import java.util.List;
import java.util.Map;

public record RuntimeQuestExport(
        String id,
        String name,
        String description,
        String startLocationId,
        List<LocationView> locations,
        List<ItemView> items,
        List<NpcView> npcs,
        List<ObjectView> worldObjects,
        Map<String, List<String>> locationItems,
        Map<String, List<String>> locationNpcs,
        Map<String, List<String>> locationObjects,
        List<TransitionView> transitions,
        List<ActionView> actions,
        List<EndingView> endings
) {
    public record LocationView(String id, String description) {}

    public record ItemView(String id, String description) {}

    public record NpcView(String id, String description, String dialogue) {}

    public record ObjectView(String id, String description) {}

    public record TransitionView(String fromId, String toId, boolean hasCondition) {}

    public record ActionView(
            String id,
            String locationId,
            String description,
            String targetId,
            List<String> requiredItems,
            List<String> progressFlagsToSet,
            boolean hasCondition,
            boolean hasEffect
    ) {}

    public record EndingView(String id, boolean hasCondition) {}
}
