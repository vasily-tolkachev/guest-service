package com.myproject.questservice.domain.generator;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkspaceAction {
    private String id;
    private String text;

    public WorkspaceAction() {
        this("", "");
    }

    public WorkspaceAction(String id, String text) {
        this.id = id;
        this.text = text;
    }
}
