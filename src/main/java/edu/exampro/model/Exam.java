package edu.exampro.model;

import edu.exampro.exception.ValidationException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Exam implements Identifiable {
    private Long id;
    private String title;
    private Duration duration;
    private final List<Question<?>> questions = new ArrayList<>();

    public Exam(Long id, String title, Duration duration) {
        this.id = id;
        setTitle(title);
        setDuration(duration);
    }

    public void addQuestion(Question<?> question) {
        questions.add(Validation.requireNonNull(question, "Question"));
    }

    public int totalMarks() {
        int total = 0;
        for (Question<?> question : questions) {
            total += question.getMarks();
        }
        return total;
    }

    /** Scores answers given as questionId -> selected option index. Unanswered questions earn nothing. */
    public int score(Map<Long, Integer> answers) {
        int total = 0;
        for (Question<?> question : questions) {
            Integer selected = question.getId() == null ? null : answers.get(question.getId());
            total += question.marksFor(selected);
        }
        return total;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Validation.requireText(title, "Exam title");
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        Validation.requireNonNull(duration, "Exam duration");
        if (duration.toMinutes() < 1) {
            throw new ValidationException("Exam duration must be at least one minute");
        }
        this.duration = duration;
    }

    public List<Question<?>> getQuestions() {
        return Collections.unmodifiableList(questions);
    }
}
