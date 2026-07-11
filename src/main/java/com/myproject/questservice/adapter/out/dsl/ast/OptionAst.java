package com.myproject.questservice.adapter.out.dsl.ast;

import java.util.List;

public record OptionAst(
        String text,
        String targetNodeId,
        List<ConditionAst> conditions,
        List<EffectAst> effects,
        int line,
        int column
) {
}
