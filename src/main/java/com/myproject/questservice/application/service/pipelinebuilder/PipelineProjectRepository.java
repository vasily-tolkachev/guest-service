package com.myproject.questservice.application.service.pipelinebuilder;

import com.myproject.questservice.domain.pipelinebuilder.PipelineProject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineProjectRepository {
    PipelineProject save(PipelineProject project);

    Optional<PipelineProject> findById(UUID id);

    List<PipelineProject> findAll();

    void deleteById(UUID id);
}
