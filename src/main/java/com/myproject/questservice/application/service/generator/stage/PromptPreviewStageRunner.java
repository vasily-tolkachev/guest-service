package com.myproject.questservice.application.service.generator.stage;

import java.util.UUID;

public interface PromptPreviewStageRunner {

    StagePromptPreview previewPrompt(UUID projectId);
}
