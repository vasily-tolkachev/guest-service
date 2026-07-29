package com.myproject.questservice.textruntime.domain.model;

public class Npc {
    private final String id;
    private final String description;
    private final String dialogue;
    private final String dialogueId;

    public Npc(String id, String description, String dialogue, String dialogueId) {
        this.id = id;
        this.description = description;
        this.dialogue = dialogue;
        this.dialogueId = dialogueId;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getDialogue() {
        return dialogue;
    }

    public String getDialogueId() {
        return dialogueId;
    }
}
