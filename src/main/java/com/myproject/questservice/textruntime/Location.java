package com.myproject.questservice.textruntime;

public class Location {
    private final String id;
    private final String description;

    public Location(String id, String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }
}
