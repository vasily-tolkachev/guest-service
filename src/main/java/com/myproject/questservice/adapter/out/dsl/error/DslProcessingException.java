package com.myproject.questservice.adapter.out.dsl.error;

public class DslProcessingException extends RuntimeException {

    private final DslError error;

    public DslProcessingException(DslError error) {
        super(error.message());
        this.error = error;
    }

    public DslError error() {
        return error;
    }
}
