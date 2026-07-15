package com.myproject.questservice.application.service.generator.stage;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public interface ChapterStageRunner extends StageRunner {

    JsonNode generateChapter(UUID projectId, String chapterId, JsonNode currentOutput);
}
