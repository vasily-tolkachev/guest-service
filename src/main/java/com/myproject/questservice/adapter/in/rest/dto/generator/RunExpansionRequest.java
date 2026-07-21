package com.myproject.questservice.adapter.in.rest.dto.generator;

import java.util.List;

public record RunExpansionRequest(
        List<String> knowledge
) {
}
