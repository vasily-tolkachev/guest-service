package com.myproject.questservice.adapter.in.rest.dto;

public record ApiErrorResponse(
        String code,
        String message,
        Integer line,
        Integer column
) {
}
