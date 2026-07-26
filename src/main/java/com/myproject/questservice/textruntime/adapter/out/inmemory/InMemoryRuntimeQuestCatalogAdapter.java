package com.myproject.questservice.textruntime.adapter.out.inmemory;

import com.myproject.questservice.textruntime.application.port.out.RuntimeQuestCatalogPort;
import com.myproject.questservice.textruntime.domain.model.RuntimeQuestDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryRuntimeQuestCatalogAdapter implements RuntimeQuestCatalogPort {
    private final ConcurrentMap<String, RuntimeQuestDefinition> quests = new ConcurrentHashMap<>();

    @Override
    public List<RuntimeQuestDefinition> findAll() {
        return new ArrayList<>(quests.values());
    }

    @Override
    public Optional<RuntimeQuestDefinition> findById(String questId) {
        return Optional.ofNullable(quests.get(normalizeQuestId(questId)));
    }

    @Override
    public void save(RuntimeQuestDefinition definition) {
        quests.put(normalizeQuestId(definition.id()), definition);
    }

    private static String normalizeQuestId(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}

