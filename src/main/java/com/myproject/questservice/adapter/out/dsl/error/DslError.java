package com.myproject.questservice.adapter.out.dsl.error;

public record DslError(
        String code,
        String message,
        int line,
        int column
) {
}
