package com.myproject.questservice.adapter.in.rest.dto.generator;

public record CreateWorkspaceNodeRequest(
        String sourceNodeId,
        String sourceActionId
) {
}
