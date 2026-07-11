package com.myproject.questservice.adapter.out.inmemory;

import com.myproject.questservice.application.port.out.QuestCatalogPort;
import com.myproject.questservice.application.service.QuestAlreadyExistsException;
import com.myproject.questservice.domain.Quest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryQuestCatalogAdapter implements QuestCatalogPort {

    private final Map<String, Quest> quests = new ConcurrentHashMap<>();

    @Override
    public List<Quest> findAll() {
        return quests.values().stream().toList();
    }

    @Override
    public Optional<Quest> findById(String questId) {
        return Optional.ofNullable(quests.get(questId));
    }

    @Override
    public void save(Quest quest) {
        Quest existing = quests.putIfAbsent(quest.id(), quest);
        if (existing != null) {
            throw new QuestAlreadyExistsException("Quest already exists: " + quest.id());
        }
    }
}
