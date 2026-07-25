package com.myproject.questservice.textruntime.domain.model;

import java.util.LinkedHashSet;
import java.util.Set;

public class Player {
    private final Set<String> inventory = new LinkedHashSet<>();

    public Set<String> getInventory() {
        return inventory;
    }
}
