package com.myproject.questservice.textruntime;

public class Npc {
    private final String id;
    private final String description;
    private final String dialogue;

    public Npc(String id, String description, String dialogue) {
        this.id = id;
        this.description = description;
        this.dialogue = dialogue;
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
}
