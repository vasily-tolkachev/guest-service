package com.myproject.questservice.textruntime.domain.model;

public record RuntimeQuestDefinition(
        String id,
        String name,
        String description,
        World world,
        String startLocationId,
        java.util.List<Objective> objectives
) {
    public RuntimeQuestDefinition {
        objectives = objectives == null ? java.util.List.of() : java.util.List.copyOf(objectives);
    }

    public record Objective(
            String id,
            String title,
            String description,
            World.Condition condition,
            java.util.List<Objective> children
    ) {
        public Objective {
            children = children == null ? java.util.List.of() : java.util.List.copyOf(children);
        }
    }
}
