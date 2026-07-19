package com.myproject.questservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public class ValidationConflictException extends ConflictException {

    private final List<String> errors;
    private final JsonNode result;

    public ValidationConflictException(String message, List<String> errors, JsonNode result) {
        super(message);
        this.errors = errors;
        this.result = result;
    }

    public List<String> getErrors() {
        return errors;
    }

    public JsonNode getResult() {
        return result;
    }
}
