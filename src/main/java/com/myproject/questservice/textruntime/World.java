package com.myproject.questservice.textruntime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class World {
    private final Map<String, Location> locations;

    public World(Map<String, Location> locations) {
        this.locations = Collections.unmodifiableMap(new HashMap<>(locations));
    }

    public Map<String, Location> getLocations() {
        return locations;
    }
}

