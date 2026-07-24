package com.myproject.questservice.textruntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Location {
    private final String id;
    private final String description;
    private final List<Item> items;
    private final List<Exit> exits;

    public Location(String id, String description, List<Item> items, List<Exit> exits) {
        this.id = id;
        this.description = description == null ? "" : description;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.exits = Collections.unmodifiableList(new ArrayList<>(exits));
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public List<Item> getItems() {
        return items;
    }

    public List<Exit> getExits() {
        return exits;
    }

    public record Exit(String actionText, String targetLocationId) {
    }
}

