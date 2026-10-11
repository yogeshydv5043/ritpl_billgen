package com.ritpl.billgen.api;

import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ritpl.billgen.service.ResourceNotFoundException;

@RestControllerAdvice(basePackages = "com.ritpl.billgen.api")
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException ex) {
        return response(HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> badRequest(IllegalArgumentException ex) {
        return response(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream().map(e -> e.getField() + ": " + e.getDefaultMessage()).distinct().collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, detail.isBlank() ? "Request validation failed." : detail);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(DataIntegrityViolationException ex) {
        return response(HttpStatus.CONFLICT, "The request conflicts with existing billing data.");
    }
    private ResponseEntity<ProblemDetail> response(HttpStatus status,String detail){
        ProblemDetail problem=ProblemDetail.forStatusAndDetail(status,detail);problem.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status).body(problem);
    }
}
