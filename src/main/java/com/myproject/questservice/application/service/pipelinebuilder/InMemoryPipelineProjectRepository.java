package com.myproject.questservice.application.service.pipelinebuilder;

import com.myproject.questservice.domain.pipelinebuilder.PipelineProject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class InMemoryPipelineProjectRepository implements PipelineProjectRepository {
    private final ConcurrentMap<UUID, PipelineProject> storage = new ConcurrentHashMap<>();

    @Override
    public PipelineProject save(PipelineProject project) {
        storage.put(project.getId(), project);
        return project;
    }

    @Override
    public Optional<PipelineProject> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<PipelineProject> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void deleteById(UUID id) {
        storage.remove(id);
    }
}
