package com.myproject.questservice.adapter.out.inmemory;

import com.myproject.questservice.application.port.out.QuestRepositoryPort;
import com.myproject.questservice.domain.QuestDefinition;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("inmemory")
public class InMemoryQuestDefinitionRepositoryAdapter implements QuestRepositoryPort {

    private final Map<String, QuestDefinition> quests = new ConcurrentHashMap<>();

    @Override
    public List<QuestDefinition> findAll() {
        return quests.values().stream().toList();
    }

    @Override
    public Optional<QuestDefinition> findByQuestId(String questId) {
        return Optional.ofNullable(quests.get(questId));
    }

    @Override
    public void save(QuestDefinition questDefinition) {
        quests.put(questDefinition.questId(), questDefinition);
    }
}
