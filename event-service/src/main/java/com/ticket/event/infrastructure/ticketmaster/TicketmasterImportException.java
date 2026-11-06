package com.ticket.event.infrastructure.ticketmaster;

import org.springframework.http.HttpStatus;

public class TicketmasterImportException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public TicketmasterImportException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
