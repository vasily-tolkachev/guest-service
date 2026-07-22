package com.myproject.questservice.adapter.in.rest.dto.nodegenerator;

public record NodeGeneratorProjectView(
        String id,
        String name,
        String questStyle,
        String status,
        Object workspace
) {
}
