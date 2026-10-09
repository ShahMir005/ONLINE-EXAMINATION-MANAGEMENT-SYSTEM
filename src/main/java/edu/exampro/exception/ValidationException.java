package edu.exampro.exception;

/** Thrown when model or service input is invalid, such as a blank required field or an answer for another exam. */
public class ValidationException extends ExamException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
