package com.myproject.questservice.adapter.out.dsl.ast;

import java.util.List;

public record NodeAst(
        String id,
        String title,
        String location,
        List<String> participants,
        List<ConditionAst> entryConditions,
        List<EffectAst> entryEffects,
        String text,
        List<OptionAst> options,
        int line,
        int column
) {
}
