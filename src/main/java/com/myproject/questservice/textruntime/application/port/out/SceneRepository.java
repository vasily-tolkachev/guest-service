package com.myproject.questservice.textruntime.application.port.out;

import com.myproject.questservice.textruntime.application.service.compilation.model.Scene;
import com.myproject.questservice.textruntime.application.service.compilation.model.SceneKey;

import java.util.List;
import java.util.Optional;

public interface SceneRepository {
    void save(Scene scene);

    Optional<Scene> find(SceneKey key);

    List<Scene> findByQuestId(String questId);
}
