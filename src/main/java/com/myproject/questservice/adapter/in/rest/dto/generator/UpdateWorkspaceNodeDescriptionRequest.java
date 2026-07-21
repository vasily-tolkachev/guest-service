package com.myproject.questservice.adapter.in.rest.dto.generator;

public record UpdateWorkspaceNodeDescriptionRequest(
        String actionDescription,
        String stateDescription
) {
}
