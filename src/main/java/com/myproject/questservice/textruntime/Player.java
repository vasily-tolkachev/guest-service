package com.myproject.questservice.textruntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Player {
    private final List<Item> inventory;

    public Player() {
        this.inventory = new ArrayList<>();
    }

    public List<Item> getInventory() {
        return Collections.unmodifiableList(inventory);
    }

    public void addItem(Item item) {
        inventory.add(item);
    }

    public boolean hasItem(String itemId) {
        return inventory.stream().anyMatch(i -> i.getId().equalsIgnoreCase(itemId));
    }
}

