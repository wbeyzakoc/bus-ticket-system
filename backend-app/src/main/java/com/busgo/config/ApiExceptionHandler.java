package com.busgo.config;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatus(
      ResponseStatusException ex, HttpServletRequest request) {
    HttpStatus status =
        HttpStatus.resolve(ex.getStatusCode().value()) == null
            ? HttpStatus.INTERNAL_SERVER_ERROR
            : HttpStatus.valueOf(ex.getStatusCode().value());
    String message = ex.getReason() == null || ex.getReason().isBlank() ? "Request failed" : ex.getReason();
    return buildResponse(status, message, request.getRequestURI());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
    String message =
        fieldError != null && fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()
            ? fieldError.getDefaultMessage()
            : "Validation failed";
    return buildResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request body", request.getRequestURI());
  }

  private ResponseEntity<Map<String, Object>> buildResponse(
      HttpStatus status, String message, String path) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("error", status.getReasonPhrase());
    body.put("message", message);
    body.put("path", path);
    return ResponseEntity.status(status).body(body);
  }
}
