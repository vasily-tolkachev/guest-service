package com.myproject.questservice.adapter.in.rest;

import com.myproject.questservice.adapter.in.rest.dto.ApiErrorResponse;
import com.myproject.questservice.adapter.out.dsl.error.DslProcessingException;
import com.myproject.questservice.application.service.BadRequestException;
import com.myproject.questservice.application.service.AiGenerationException;
import com.myproject.questservice.application.service.ConflictException;
import com.myproject.questservice.application.service.FileReadException;
import com.myproject.questservice.application.service.NotFoundException;
import com.myproject.questservice.application.service.NotImplementedException;
import com.myproject.questservice.application.service.QuestChangedException;
import com.myproject.questservice.application.service.ValidationConflictException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

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

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse("CONFLICT", ex.getMessage(), null, null));
    }

    @ExceptionHandler(ValidationConflictException.class)
    public ResponseEntity<Map<String, Object>> handleValidationConflict(ValidationConflictException ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", "CONFLICT");
        payload.put("message", ex.getMessage());
        payload.put("errors", ex.getErrors());
        payload.put("result", ex.getResult());
        payload.put("line", null);
        payload.put("column", null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(payload);
    }

    @ExceptionHandler(NotImplementedException.class)
    public ResponseEntity<ApiErrorResponse> handleNotImplemented(NotImplementedException ex) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(new ApiErrorResponse("NOT_IMPLEMENTED", ex.getMessage(), null, null));
    }

    @ExceptionHandler(AiGenerationException.class)
    public ResponseEntity<ApiErrorResponse> handleAiGeneration(AiGenerationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiErrorResponse("AI_GENERATION_ERROR", ex.getMessage(), null, null));
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
