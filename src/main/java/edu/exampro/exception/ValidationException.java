package edu.exampro.exception;

/** Thrown when an object is created or used with invalid data (blank name, wrong answer id, ...). */
public class ValidationException extends ExamException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
