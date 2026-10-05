package edu.exampro.model;

import java.util.List;

/**
 * Generic question. The type parameter T is the type of answer a concrete question accepts
 * (Integer for multiple choice, Boolean for true/false).
 */
public abstract class Question<T> implements Identifiable {
    private Long id;
    private final String prompt;
    private final int marks;

    protected Question(Long id, String prompt, int marks) {
        this.id = id;
        this.prompt = Validation.requireText(prompt, "Question prompt");
        this.marks = Validation.requirePositive(marks, "Question marks");
    }

    /** Short code stored in the database, for example "MCQ" or "TF". */
    public abstract String getType();

    /** The choices shown to the student. */
    public abstract List<String> choices();

    /** Index (0-based) of the correct choice, used for storage. */
    public abstract int getCorrectOptionIndex();

    /** Converts the index of the chosen option into this question's answer type. */
    protected abstract T toAnswer(int selectedOption);

    public abstract boolean isCorrect(T answer);

    public final boolean isCorrectOption(int selectedOption) {
        return isCorrect(toAnswer(selectedOption));
    }

    /** Marks earned for the chosen option; null means the question was not answered. */
    public final int marksFor(Integer selectedOption) {
        return selectedOption != null && isCorrectOption(selectedOption) ? marks : 0;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getMarks() {
        return marks;
    }
}
