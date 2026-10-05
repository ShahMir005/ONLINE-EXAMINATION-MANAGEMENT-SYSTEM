package edu.exampro.model;

import edu.exampro.exception.ValidationException;
import java.util.List;

public final class MultipleChoiceQuestion extends Question<Integer> {
    private final List<String> options;
    private final int correctOption;

    public MultipleChoiceQuestion(Long id, String prompt, int marks, List<String> options, int correctOption) {
        super(id, prompt, marks);
        Validation.requireNonNull(options, "Options");
        if (options.size() < 2) {
            throw new ValidationException("A multiple-choice question needs at least two options");
        }
        if (correctOption < 0 || correctOption >= options.size()) {
            throw new ValidationException("Correct option index is outside the option list");
        }
        this.options = List.copyOf(options);
        this.correctOption = correctOption;
    }

    @Override
    public String getType() {
        return "MCQ";
    }

    @Override
    public List<String> choices() {
        return options;
    }

    @Override
    public int getCorrectOptionIndex() {
        return correctOption;
    }

    @Override
    protected Integer toAnswer(int selectedOption) {
        return selectedOption;
    }

    @Override
    public boolean isCorrect(Integer answer) {
        return answer != null && answer == correctOption;
    }
}
