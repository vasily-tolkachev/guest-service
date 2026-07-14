package com.myproject.questservice.application.service.generator.stage;

import com.myproject.questservice.domain.generator.StageType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class StageRunnerRegistry {

    private final Map<StageType, StageRunner> registry;

    public StageRunnerRegistry(List<StageRunner> runners) {
        Map<StageType, StageRunner> map = new EnumMap<>(StageType.class);
        for (StageRunner runner : runners) {
            map.put(runner.type(), runner);
        }
        this.registry = Map.copyOf(map);
    }

    public Optional<StageRunner> find(StageType type) {
        return Optional.ofNullable(registry.get(type));
    }
}

