package com.manhduc205.meetingplatform.exceptions;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RequestException.class)
    public ResponseEntity<?> handleRequestException(RequestException ex) {
        return ResponseEntity.badRequest().body(
                Map.of(
                        "success", false,
                        "message", ex.getMessage(),
                        "errorCode", "REQUEST_ERROR"
                )
        );
    }

    // Không tìm thấy dữ liệu
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of(
                        "success", false,
                        "message", ex.getMessage(),
                        "errorCode", "NOT_FOUND"
                )
        );
    }

    // Lỗi Spring Security
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                Map.of(
                        "success", false,
                        "message", "Bạn không có quyền thực hiện hành động này",
                        "errorCode", "FORBIDDEN"
                )
        );
    }

    // Lỗi chưa xác định
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                Map.of(
                        "success", false,
                        "message", "Lỗi hệ thống",
                        "errorCode", "SERVER_ERROR"
                )
        );
    }
}

