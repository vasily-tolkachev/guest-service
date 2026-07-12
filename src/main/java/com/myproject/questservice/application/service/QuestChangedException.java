package com.myproject.questservice.application.service;

public class QuestChangedException extends RuntimeException {
    public QuestChangedException(String message) {
        super(message);
    }
}
