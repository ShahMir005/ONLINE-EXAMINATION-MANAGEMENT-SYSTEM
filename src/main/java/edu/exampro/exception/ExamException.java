package edu.exampro.exception;

/**
 * Thrown when an ExamPro operation cannot be completed, such as when persistence fails or an
 * examination session is missing or expired. Unchecked, so callers are not forced to catch it.
 */
public class ExamException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExamException(String message) {
        super(message);
    }

    public ExamException(String message, Throwable cause) {
        super(message, cause);
    }
}
