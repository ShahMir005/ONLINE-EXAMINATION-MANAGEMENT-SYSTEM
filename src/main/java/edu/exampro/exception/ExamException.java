package edu.exampro.exception;

/** Base class for every error raised by ExamPro. Unchecked, so callers are not forced to catch it. */
public class ExamException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExamException(String message) {
        super(message);
    }

    public ExamException(String message, Throwable cause) {
        super(message, cause);
    }
}
