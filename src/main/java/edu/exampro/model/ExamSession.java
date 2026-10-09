package edu.exampro.model;

import java.time.Instant;

/** A student's recorded start time for one examination. */
public final class ExamSession implements Identifiable {
    private Long id;
    private final long examId;
    private final long studentId;
    private final Instant startTime;

    public ExamSession(Long id, long examId, long studentId, Instant startTime) {
        this.id = id;
        this.examId = examId;
        this.studentId = studentId;
        this.startTime = Validation.requireNonNull(startTime, "Session start time");
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public long getExamId() {
        return examId;
    }

    public long getStudentId() {
        return studentId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    @Override
    public String toString() {
        return "ExamSession{id=" + id + ", examId=" + examId + ", studentId=" + studentId
            + ", startTime=" + startTime + "}";
    }
}
