package com.myproject.questservice.application.port.out;

import com.myproject.questservice.domain.QuestDefinition;

import java.util.List;
import java.util.Optional;

public interface QuestRepositoryPort {

    List<QuestDefinition> findAll();

    Optional<QuestDefinition> findByQuestId(String questId);

    void save(QuestDefinition questDefinition);
}
