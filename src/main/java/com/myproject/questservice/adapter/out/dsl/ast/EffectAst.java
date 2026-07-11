package com.myproject.questservice.adapter.out.dsl.ast;

import java.util.List;

public record EffectAst(
        String name,
        List<String> arguments,
        int line,
        int column
) {
}
