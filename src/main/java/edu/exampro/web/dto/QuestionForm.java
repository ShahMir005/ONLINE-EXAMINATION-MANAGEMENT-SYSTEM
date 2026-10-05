package edu.exampro.web.dto;

import java.util.ArrayList;
import java.util.List;

public class QuestionForm {
    private String type = "MCQ"; // "MCQ" or "TF"
    private String prompt;
    private int marks = 5;
    private List<String> options = new ArrayList<>(List.of("", "", "", ""));
    private int correctOption = 0;
    private boolean tfCorrect = true;

    public QuestionForm() { }

    public static QuestionForm mcq(String prompt, int marks, List<String> options, int correctOption) {
        QuestionForm q = new QuestionForm();
        q.setType("MCQ");
        q.setPrompt(prompt);
        q.setMarks(marks);
        q.setOptions(new ArrayList<>(options));
        q.setCorrectOption(correctOption);
        return q;
    }

    public static QuestionForm tf(String prompt, int marks, boolean tfCorrect) {
        QuestionForm q = new QuestionForm();
        q.setType("TF");
        q.setPrompt(prompt);
        q.setMarks(marks);
        q.setTfCorrect(tfCorrect);
        return q;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public int getMarks() {
        return marks;
    }

    public void setMarks(int marks) {
        this.marks = marks;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(int correctOption) {
        this.correctOption = correctOption;
    }

    public boolean isTfCorrect() {
        return tfCorrect;
    }

    public void setTfCorrect(boolean tfCorrect) {
        this.tfCorrect = tfCorrect;
    }
}
