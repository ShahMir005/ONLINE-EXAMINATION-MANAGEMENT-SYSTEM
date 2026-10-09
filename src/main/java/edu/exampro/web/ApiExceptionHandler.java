package edu.exampro.web;

import edu.exampro.exception.DuplicateSubmissionException;
import edu.exampro.exception.ExamException;
import edu.exampro.exception.ValidationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ExamException.class)
    public ResponseEntity<Map<String, String>> handleExamException(ExamException exception) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateSubmissionException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateSubmission(DuplicateSubmissionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ValidationException exception) {
        return ResponseEntity.badRequest()
            .body(Map.of("error", exception.getMessage()));
    }
}
