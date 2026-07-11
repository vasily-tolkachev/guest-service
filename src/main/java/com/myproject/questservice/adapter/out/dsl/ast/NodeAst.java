package com.myproject.questservice.adapter.out.dsl.ast;

import java.util.List;

public record NodeAst(
        String id,
        String text,
        List<OptionAst> options,
        int line,
        int column
) {
}
