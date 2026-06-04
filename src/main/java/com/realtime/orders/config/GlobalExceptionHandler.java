package com.realtime.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts every exception into a consistent JSON error response:
 * { "status": 400, "error": "Bad Request", "message": "...", "timestamp": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** ResponseStatusException — thrown by service layer (404, 400, etc.) */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return error(ex.getStatusCode().value(), ex.getReason());
    }

    /** @Valid failures on @RequestBody — missing / blank required fields */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> fieldErrors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        String message = String.join("; ", fieldErrors);
        return error(HttpStatus.BAD_REQUEST.value(), message);
    }

    /** Wrong type in path/query variable — e.g. /api/orders/abc instead of a number */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, TypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(TypeMismatchException ex) {
        String param = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : "parameter";
        return error(HttpStatus.BAD_REQUEST.value(),
                "Invalid value '" + ex.getValue() + "' for '" + param + "'. Expected a number.");
    }

    /** Malformed or missing JSON body */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST.value(), "Request body is missing or contains invalid JSON.");
    }

    /** Catch-all — unexpected errors */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "An unexpected error occurred. Please try again.");
    }

    private ResponseEntity<Map<String, Object>> error(int status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",    status);
        body.put("error",     HttpStatus.resolve(status) != null
                              ? HttpStatus.resolve(status).getReasonPhrase() : "Error");
        body.put("message",   message);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
