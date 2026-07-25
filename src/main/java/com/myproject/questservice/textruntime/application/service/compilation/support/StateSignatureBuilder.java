package com.myproject.questservice.textruntime.application.service.compilation.support;

import com.myproject.questservice.textruntime.domain.model.GameState;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class StateSignatureBuilder {
    private StateSignatureBuilder() {
    }

    public static String build(GameState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("loc=").append(state.getCurrentLocation()).append('|');
        appendSet(sb, "inv", state.getPlayer().getInventory());
        appendSet(sb, "facts", state.getKnownFacts());
        appendSet(sb, "flags", state.getProgressFlags());
        appendSet(sb, "removed", state.getRemovedWorldItems());
        appendMap(sb, "obj", state.getObjectStates());
        appendMap(sb, "char", state.getCharacterStates());
        return sb.toString();
    }

    private static void appendSet(StringBuilder sb, String key, Set<String> values) {
        sb.append(key).append("=[");
        TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sorted.addAll(values);
        boolean first = true;
        for (String value : sorted) {
            if (!first) {
                sb.append(',');
            }
            sb.append(value);
            first = false;
        }
        sb.append("]|");
    }

    private static void appendMap(StringBuilder sb, String key, Map<String, String> values) {
        sb.append(key).append("={");
        TreeMap<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(values);
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        sb.append("}|");
    }
}
