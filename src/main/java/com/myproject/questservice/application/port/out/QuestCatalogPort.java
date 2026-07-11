package com.myproject.questservice.application.port.out;

import com.myproject.questservice.domain.Quest;

import java.util.List;
import java.util.Optional;

public interface QuestCatalogPort {

    List<Quest> findAll();

    Optional<Quest> findById(String questId);
}
