package com.myproject.questservice.textruntime.application.port.out;

import com.myproject.questservice.textruntime.domain.model.RuntimeQuestDefinition;

import java.util.List;
import java.util.Optional;

public interface RuntimeQuestCatalogPort {
    List<RuntimeQuestDefinition> findAll();

    Optional<RuntimeQuestDefinition> findById(String questId);
}
