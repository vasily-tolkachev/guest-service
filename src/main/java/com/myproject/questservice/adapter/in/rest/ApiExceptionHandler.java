package com.myproject.questservice.adapter.in.rest;

import com.myproject.questservice.adapter.in.rest.dto.ApiErrorResponse;
import com.myproject.questservice.adapter.out.dsl.error.DslProcessingException;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.application.service.FileReadException;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.QuestChangedException;
import com.myproject.questservice.application.service.QuestAlreadyExistsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("NOT_FOUND", ex.getMessage(), null, null));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("BAD_REQUEST", ex.getMessage(), null, null));
    }

    @ExceptionHandler(QuestAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(QuestAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("QUEST_ALREADY_EXISTS", ex.getMessage(), null, null));
    }

    @ExceptionHandler(QuestChangedException.class)
    public ResponseEntity<ApiErrorResponse> handleQuestChanged(QuestChangedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("QUEST_CHANGED", ex.getMessage(), null, null));
    }

    @ExceptionHandler(FileReadException.class)
    public ResponseEntity<ApiErrorResponse> handleFileRead(FileReadException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("FILE_READ_ERROR", ex.getMessage(), null, null));
    }

    @ExceptionHandler(DslProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleDslError(DslProcessingException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        ex.error().code(),
                        ex.error().message(),
                        ex.error().line(),
                        ex.error().column()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "Validation failed" : error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse("BAD_REQUEST", errorMessage, null, null));
    }
}
