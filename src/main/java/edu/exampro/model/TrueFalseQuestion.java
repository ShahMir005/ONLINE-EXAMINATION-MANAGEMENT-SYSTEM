package edu.exampro.model;

import java.util.List;

/** Second Question subclass: the answer type is Boolean, so the generic parameter really differs. */
public final class TrueFalseQuestion extends Question<Boolean> {
    private final boolean correctAnswer;

    public TrueFalseQuestion(Long id, String prompt, int marks, boolean correctAnswer) {
        super(id, prompt, marks);
        this.correctAnswer = correctAnswer;
    }

    @Override
    public String getType() {
        return "TF";
    }

    /** Option 0 is "True", option 1 is "False". */
    @Override
    public List<String> choices() {
        return List.of("True", "False");
    }

    @Override
    public int getCorrectOptionIndex() {
        return correctAnswer ? 0 : 1;
    }

    @Override
    protected Boolean toAnswer(int selectedOption) {
        if (selectedOption == 0) {
            return Boolean.TRUE;
        }
        return selectedOption == 1 ? Boolean.FALSE : null;
    }

    @Override
    public boolean isCorrect(Boolean answer) {
        return answer != null && answer == correctAnswer;
    }
}
