package edu.exampro.web.dto;

import java.util.ArrayList;
import java.util.List;

public class ExamForm {
    private String title;
    private int durationMinutes = 30;
    private List<QuestionForm> questions = new ArrayList<>();

    public ExamForm() {
        // Default with 1 MCQ and 1 TF question for quick editing
        QuestionForm mcq = new QuestionForm();
        mcq.setType("MCQ");
        mcq.setPrompt("Which feature allows one method interface to have multiple implementations in Java?");
        mcq.setMarks(5);
        mcq.setOptions(new ArrayList<>(List.of("Polymorphism", "Encapsulation", "Inheritance", "Abstraction")));
        mcq.setCorrectOption(0);

        QuestionForm tf = new QuestionForm();
        tf.setType("TF");
        tf.setPrompt("In Java, an interface can be instantiated directly with the 'new' keyword.");
        tf.setMarks(2);
        tf.setTfCorrect(false);

        questions.add(mcq);
        questions.add(tf);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public List<QuestionForm> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionForm> questions) {
        this.questions = questions;
    }
}
