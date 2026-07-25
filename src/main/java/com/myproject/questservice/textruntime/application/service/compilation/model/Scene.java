package com.myproject.questservice.textruntime.application.service.compilation.model;

import java.util.List;

public record Scene(
        SceneKey key,
        String title,
        String description,
        List<String> actions
) {
}
