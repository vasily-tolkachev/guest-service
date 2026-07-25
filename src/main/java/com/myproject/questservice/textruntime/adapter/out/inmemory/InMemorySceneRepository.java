package com.myproject.questservice.textruntime.adapter.out.inmemory;

import com.myproject.questservice.textruntime.application.port.out.SceneRepository;
import com.myproject.questservice.textruntime.application.service.compilation.model.Scene;
import com.myproject.questservice.textruntime.application.service.compilation.model.SceneKey;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemorySceneRepository implements SceneRepository {
    private final Map<SceneKey, Scene> scenes = new ConcurrentHashMap<>();

    @Override
    public void save(Scene scene) {
        scenes.put(scene.key(), scene);
    }

    @Override
    public Optional<Scene> find(SceneKey key) {
        return Optional.ofNullable(scenes.get(key));
    }

    @Override
    public List<Scene> findByQuestId(String questId) {
        return scenes.values().stream()
                .filter(scene -> scene.key().questId().equalsIgnoreCase(questId))
                .toList();
    }
}
