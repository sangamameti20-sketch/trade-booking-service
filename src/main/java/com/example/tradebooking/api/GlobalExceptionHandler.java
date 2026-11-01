package com.example.tradebooking.api;

import com.example.tradebooking.api.dto.ErrorResponse;
import com.example.tradebooking.api.dto.ErrorResponse.FieldViolation;
import com.example.tradebooking.service.TradeNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();

        log.atWarn()
                .addKeyValue("path", req.getRequestURI())
                .addKeyValue("violations", violations.stream().map(v -> v.field() + ": " + v.message()).toList())
                .log("Trade request failed validation");

        return build(HttpStatus.BAD_REQUEST, "Request validation failed", req, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.atWarn().addKeyValue("path", req.getRequestURI()).log("Malformed request body");
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request body", req, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", req, List.of());
    }

    @ExceptionHandler(TradeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TradeNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.atError().addKeyValue("path", req.getRequestURI()).setCause(ex).log("Unhandled error");
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req, List.of());
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req,
                                                       List<FieldViolation> violations) {
        var body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), violations);
        return ResponseEntity.status(status).body(body);
    }
}
