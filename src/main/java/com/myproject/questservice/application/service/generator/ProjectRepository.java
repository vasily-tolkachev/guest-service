package com.myproject.questservice.application.service.generator;

import com.myproject.questservice.domain.generator.QuestProject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProjectRepository {

    private final Map<UUID, QuestProject> projects = new ConcurrentHashMap<>();

    public QuestProject save(QuestProject project) {
        projects.put(project.getId(), project);
        return project;
    }

    public Optional<QuestProject> findById(UUID id) {
        return Optional.ofNullable(projects.get(id));
    }

    public List<QuestProject> findAll() {
        return new ArrayList<>(projects.values());
    }

    public Optional<QuestProject> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return projects.values().stream()
                .filter(project -> project.getName() != null && project.getName().trim().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public void deleteById(UUID id) {
        projects.remove(id);
    }
}
