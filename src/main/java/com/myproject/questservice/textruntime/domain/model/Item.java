package com.myproject.questservice.textruntime.domain.model;

public class Item {
    private final String id;
    private final String description;

    public Item(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getName() {
        return description;
    }
}
