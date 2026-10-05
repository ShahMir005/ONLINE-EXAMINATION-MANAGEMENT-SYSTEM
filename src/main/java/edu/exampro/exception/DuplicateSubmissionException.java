package edu.exampro.exception;

/** Thrown when a student tries to submit the same exam twice. */
public class DuplicateSubmissionException extends ExamException {
    private static final long serialVersionUID = 1L;

    public DuplicateSubmissionException(long examId, long studentId) {
        super("Student " + studentId + " has already submitted exam " + examId);
    }
}
