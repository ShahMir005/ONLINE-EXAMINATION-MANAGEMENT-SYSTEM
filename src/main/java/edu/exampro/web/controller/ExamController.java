package edu.exampro.web.controller;

import edu.exampro.exception.ExamException;
import edu.exampro.model.Exam;
import edu.exampro.model.MultipleChoiceQuestion;
import edu.exampro.model.Question;
import edu.exampro.model.TrueFalseQuestion;
import edu.exampro.service.ExamCatalog;
import edu.exampro.web.dto.ExamForm;
import edu.exampro.web.dto.QuestionForm;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ExamController {
    private final ExamCatalog examCatalog;

    public ExamController(ExamCatalog examCatalog) {
        this.examCatalog = examCatalog;
    }

    @GetMapping("/exams")
    public String listExams(Model model) {
        List<Exam> exams = examCatalog.findAll();
        model.addAttribute("exams", exams);
        return "exams";
    }

    @GetMapping("/exams/new")
    public String newExamForm(Model model) {
        if (!model.containsAttribute("examForm")) {
            model.addAttribute("examForm", new ExamForm());
        }
        return "exam-create";
    }

    @PostMapping("/exams")
    public String createExam(@ModelAttribute("examForm") ExamForm form, RedirectAttributes redirectAttributes) {
        try {
            if (form.getTitle() == null || form.getTitle().isBlank()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Exam title is required.");
                redirectAttributes.addFlashAttribute("examForm", form);
                return "redirect:/exams/new";
            }

            if (form.getDurationMinutes() < 1) {
                redirectAttributes.addFlashAttribute("errorMessage", "Duration must be at least 1 minute.");
                redirectAttributes.addFlashAttribute("examForm", form);
                return "redirect:/exams/new";
            }

            Exam exam = new Exam(null, form.getTitle().trim(), Duration.ofMinutes(form.getDurationMinutes()));

            if (form.getQuestions() == null || form.getQuestions().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "An exam must have at least one question.");
                redirectAttributes.addFlashAttribute("examForm", form);
                return "redirect:/exams/new";
            }

            int questionIndex = 1;
            for (QuestionForm qf : form.getQuestions()) {
                if (qf.getPrompt() == null || qf.getPrompt().isBlank()) {
                    continue; // skip blank question entries
                }
                int marks = qf.getMarks() > 0 ? qf.getMarks() : 1;

                if ("TF".equalsIgnoreCase(qf.getType())) {
                    // Polymorphic TrueFalseQuestion
                    TrueFalseQuestion tf = new TrueFalseQuestion(null, qf.getPrompt().trim(), marks, qf.isTfCorrect());
                    exam.addQuestion(tf);
                } else {
                    // Polymorphic MultipleChoiceQuestion
                    List<String> validOptions = new ArrayList<>();
                    if (qf.getOptions() != null) {
                        for (String opt : qf.getOptions()) {
                            if (opt != null && !opt.isBlank()) {
                                validOptions.add(opt.trim());
                            }
                        }
                    }
                    if (validOptions.size() < 2) {
                        redirectAttributes.addFlashAttribute("errorMessage",
                            "MCQ question #" + questionIndex + " must have at least 2 non-empty options.");
                        redirectAttributes.addFlashAttribute("examForm", form);
                        return "redirect:/exams/new";
                    }

                    int correctOpt = qf.getCorrectOption();
                    if (correctOpt < 0 || correctOpt >= validOptions.size()) {
                        correctOpt = 0; // fallback to first option if index out of bounds
                    }

                    MultipleChoiceQuestion mcq = new MultipleChoiceQuestion(
                        null, qf.getPrompt().trim(), marks, validOptions, correctOpt);
                    exam.addQuestion(mcq);
                }
                questionIndex++;
            }

            if (exam.getQuestions().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Please provide at least one valid question with prompt.");
                redirectAttributes.addFlashAttribute("examForm", form);
                return "redirect:/exams/new";
            }

            // Create via ExamCatalog (triggers ExamRepository.createWithQuestions within a single JDBC transaction)
            Exam created = examCatalog.create(exam);

            redirectAttributes.addFlashAttribute("successMessage",
                "Exam '" + created.getTitle() + "' created successfully with "
                    + created.getQuestions().size() + " questions (" + created.totalMarks() + " marks)!");
            return "redirect:/exams";
        } catch (ExamException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to create exam: " + e.getMessage());
            redirectAttributes.addFlashAttribute("examForm", form);
            return "redirect:/exams/new";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unexpected error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("examForm", form);
            return "redirect:/exams/new";
        }
    }

    @GetMapping("/exams/{id}")
    public String viewExam(@PathVariable("id") long id, Model model, RedirectAttributes redirectAttributes) {
        return examCatalog.find(id)
            .map(exam -> {
                model.addAttribute("exam", exam);
                return "exam-detail";
            })
            .orElseGet(() -> {
                redirectAttributes.addFlashAttribute("errorMessage", "Exam #" + id + " not found.");
                return "redirect:/exams";
            });
    }

    @PostMapping("/exams/{id}/delete")
    public String deleteExam(@PathVariable("id") long id, RedirectAttributes redirectAttributes) {
        try {
            boolean deleted = examCatalog.delete(id);
            if (deleted) {
                redirectAttributes.addFlashAttribute("successMessage", "Exam #" + id + " deleted successfully.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Exam #" + id + " could not be found.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to delete exam: " + e.getMessage());
        }
        return "redirect:/exams";
    }
}
