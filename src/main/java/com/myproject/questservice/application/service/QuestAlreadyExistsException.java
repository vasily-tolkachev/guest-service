package com.myproject.questservice.application.service;

public class QuestAlreadyExistsException extends RuntimeException {

    public QuestAlreadyExistsException(String message) {
        super(message);
    }
}
