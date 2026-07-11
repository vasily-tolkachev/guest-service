package com.myproject.questservice.adapter.out.dsl.ast;

public record OptionAst(
        String text,
        String targetNodeId,
        int line,
        int column
) {
}
