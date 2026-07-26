package com.myproject.questservice.textruntime.application.port.in.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record RuntimeQuestImportRequest(
        @NotBlank String id,
        @NotBlank String name,
        String description,
        @NotBlank String startLocationId,
        @NotNull @Valid List<LocationView> locations,
        @NotNull @Valid List<ItemView> items,
        @NotNull @Valid List<NpcView> npcs,
        @Valid List<ObjectView> worldObjects,
        @NotNull Map<String, List<String>> locationItems,
        @NotNull Map<String, List<String>> locationNpcs,
        Map<String, List<String>> locationObjects,
        @NotNull @Valid List<TransitionView> transitions,
        @NotNull @Valid List<ActionView> actions,
        @Valid List<ObjectiveView> objectives,
        @NotNull @Valid List<EndingView> endings
) {
    public record LocationView(@NotBlank String id, @NotBlank String description) {}

    public record ItemView(@NotBlank String id, @NotBlank String description) {}

    public record NpcView(@NotBlank String id, @NotBlank String description, @NotBlank String dialogue) {}

    public record ObjectView(@NotBlank String id, @NotBlank String description) {}

    public record TransitionView(
            @NotBlank String fromId,
            @NotBlank String toId,
            @Valid ConditionSpec condition,
            Boolean hasCondition
    ) {}

    public record ActionView(
            @NotBlank String id,
            String locationId,
            @NotBlank String description,
            String targetId,
            Set<String> requiredItems,
            Set<String> progressFlagsToSet,
            @Valid ConditionSpec condition,
            @Valid List<EffectSpec> effects,
            Boolean hasCondition,
            Boolean hasEffect
    ) {}

    public record ObjectiveView(
            @NotBlank String id,
            @NotBlank String title,
            String description,
            @Valid ConditionSpec condition,
            Boolean hasCondition,
            @Valid List<ObjectiveView> children
    ) {}

    public record EndingView(
            @NotBlank String id,
            @Valid ConditionSpec condition,
            Boolean hasCondition
    ) {}

    public record ConditionSpec(
            @NotBlank String type,
            String key,
            String value,
            @Valid List<ConditionSpec> conditions,
            @Valid ConditionSpec condition
    ) {}

    public record EffectSpec(
            @NotBlank String type,
            String key,
            String value
    ) {}
}
