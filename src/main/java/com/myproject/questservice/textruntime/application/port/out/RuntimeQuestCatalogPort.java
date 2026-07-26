package com.myproject.questservice.textruntime.application.port.out;

import com.myproject.questservice.textruntime.domain.model.RuntimeQuestDefinition;
import com.myproject.questservice.textruntime.application.port.in.dto.RuntimeQuestImportRequest;

import java.util.List;
import java.util.Optional;

public interface RuntimeQuestCatalogPort {
    List<RuntimeQuestDefinition> findAll();

    Optional<RuntimeQuestDefinition> findById(String questId);

    void save(RuntimeQuestDefinition definition);

    default void save(RuntimeQuestDefinition definition, RuntimeQuestImportRequest source) {
        save(definition);
    }

    default Optional<RuntimeQuestImportRequest> findSourceById(String questId) {
        return Optional.empty();
    }
}
