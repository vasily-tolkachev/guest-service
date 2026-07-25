package com.myproject.questservice.textruntime.application.port.out;

import com.myproject.questservice.textruntime.application.service.compilation.model.Scene;
import com.myproject.questservice.textruntime.application.service.compilation.model.SceneGenerationRequest;

public interface SceneGenerator {
    Scene generate(SceneGenerationRequest request);
}
