package edu.exampro.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** One student's finished submission of one exam. Immutable except for the database id. */
public final class Attempt implements Identifiable {
    private Long id;
    private final long examId;
    private final long studentId;
    private final Map<Long, Integer> answers;
    private final Instant submittedAt;
    private final int score;

    public Attempt(Long id, long examId, long studentId, Map<Long, Integer> answers, Instant submittedAt, int score) {
        this.id = id;
        this.examId = examId;
        this.studentId = studentId;
        Validation.requireNonNull(answers, "Answers");
        this.answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
        this.submittedAt = Validation.requireNonNull(submittedAt, "Submission time");
        this.score = score;
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

    public Map<Long, Integer> getAnswers() {
        return answers;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public int getScore() {
        return score;
    }
}
