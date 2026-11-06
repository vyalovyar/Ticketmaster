package com.ticket.ai.application;

import com.ticket.ai.infrastructure.AiUnavailableException;
import com.ticket.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice(assignableTypes = AiChatController.class)
public class AiExceptionHandler {

    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAiUnavailable(AiUnavailableException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .errorCode("AI_UNAVAILABLE")
                .message(exception.getMessage())
                .time(LocalDateTime.now())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
