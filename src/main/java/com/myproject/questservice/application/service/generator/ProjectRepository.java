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
}

