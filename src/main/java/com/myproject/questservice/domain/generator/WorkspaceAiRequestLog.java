package com.myproject.questservice.domain.generator;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class WorkspaceAiRequestLog {
    private String id;
    private String stage;
    private String nodeId;
    private String systemPrompt;
    private String userPrompt;
    private Instant createdAt;

    public WorkspaceAiRequestLog() {
        this("", "", null, "", "", Instant.now());
    }

    public WorkspaceAiRequestLog(String id, String stage, String nodeId, String systemPrompt, String userPrompt, Instant createdAt) {
        this.id = id;
        this.stage = stage;
        this.nodeId = nodeId;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.createdAt = createdAt;
    }
}
