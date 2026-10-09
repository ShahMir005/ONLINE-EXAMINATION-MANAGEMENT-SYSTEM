package edu.exampro.exception;

/** Thrown when a student submits the same exam more than once, including while an earlier submission is in flight. */
public class DuplicateSubmissionException extends ExamException {
    private static final long serialVersionUID = 1L;

    public DuplicateSubmissionException(long examId, long studentId) {
        super("Student " + studentId + " has already submitted exam " + examId);
    }
}
