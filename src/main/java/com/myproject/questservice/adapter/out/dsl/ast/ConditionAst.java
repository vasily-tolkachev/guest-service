package com.myproject.questservice.adapter.out.dsl.ast;

import java.util.List;

public record ConditionAst(
        String name,
        List<String> arguments,
        int line,
        int column
) {
}
