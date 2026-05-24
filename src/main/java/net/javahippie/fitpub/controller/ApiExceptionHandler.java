package net.javahippie.fitpub.controller;

import net.javahippie.fitpub.exception.ApiValidationException;
import net.javahippie.fitpub.model.dto.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central exception mapping for REST APIs.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiValidationException.class)
    public ResponseEntity<ApiError> handleApiValidation(ApiValidationException e) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", e.getMessage()));
    }
}
