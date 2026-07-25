package com.myproject.questservice.textruntime.application.port.in.dto;

public record RuntimeActionResult(
        String message,
        RuntimeSnapshot snapshot,
        String engineAction
) {
}
