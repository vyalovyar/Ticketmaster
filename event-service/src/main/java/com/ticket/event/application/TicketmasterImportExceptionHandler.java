package com.ticket.event.application;

import com.ticket.dto.ErrorResponse;
import com.ticket.event.infrastructure.ticketmaster.TicketmasterImportException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice(assignableTypes = TicketmasterImportController.class)
public class TicketmasterImportExceptionHandler {

    @ExceptionHandler(TicketmasterImportException.class)
    public ResponseEntity<ErrorResponse> handleImportError(TicketmasterImportException exception) {
        ErrorResponse body = ErrorResponse.builder()
                .errorCode(exception.getErrorCode())
                .message(exception.getMessage())
                .time(LocalDateTime.now())
                .build();
        return ResponseEntity.status(exception.getStatus()).body(body);
    }
}
