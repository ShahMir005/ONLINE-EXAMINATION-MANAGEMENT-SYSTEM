package edu.exampro.web.controller;

import edu.exampro.exception.DuplicateSubmissionException;
import edu.exampro.exception.ExamException;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Question;
import edu.exampro.model.Student;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import edu.exampro.service.ExamSubmissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ExamTakeController {
    private final ExamCatalog examCatalog;
    private final StudentRepository studentRepository;
    private final ExamSubmissionService submissionService;

    public ExamTakeController(ExamCatalog examCatalog, StudentRepository studentRepository,
                              ExamSubmissionService submissionService) {
        this.examCatalog = examCatalog;
        this.studentRepository = studentRepository;
        this.submissionService = submissionService;
    }

    @GetMapping("/take-exam")
    public String takeExamSelection(@RequestParam(value = "examId", required = false) Long examId,
                                    Model model, RedirectAttributes redirectAttributes) {
        if (examId != null) {
            return "redirect:/exams/" + examId + "/take";
        }
        List<Exam> exams = examCatalog.findAll();
        if (exams.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "No exams available. Please create an exam or load demo data first.");
            return "redirect:/exams";
        }
        return "redirect:/exams/" + exams.get(0).getId() + "/take";
    }

    @GetMapping("/exams/{id}/take")
    public String takeExamPage(@PathVariable("id") long id,
                               @RequestParam(value = "studentId", required = false) Long preselectedStudentId,
                               Authentication auth,
                               Model model, RedirectAttributes redirectAttributes) {
        Exam exam = examCatalog.find(id).orElse(null);
        if (exam == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Exam #" + id + " not found.");
            return "redirect:/exams";
        }

        List<Student> students = studentRepository.findAll();
        if (students.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "No registered students found. Please add a student before attempting an exam.");
            return "redirect:/students";
        }

        boolean isStudent = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));

        Long selectedId = null;
        if (isStudent) {
            Student currentStudent = studentRepository.findByEmail(auth.getName()).orElse(null);
            if (currentStudent != null) {
                selectedId = currentStudent.getId();
                // Filter dropdown list to only the student themselves
                students = List.of(currentStudent);
            }
        } else {
            selectedId = preselectedStudentId != null ? preselectedStudentId : students.get(0).getId();
        }

        model.addAttribute("exam", exam);
        model.addAttribute("students", students);
        model.addAttribute("selectedStudentId", selectedId);
        model.addAttribute("isStudentRole", isStudent);

        return "exam-take";
    }

    @PostMapping("/exams/{id}/submit")
    public String submitExam(@PathVariable("id") long id,
                             @RequestParam(value = "studentId", required = false) Long paramStudentId,
                             Authentication auth,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        Exam exam = examCatalog.find(id).orElse(null);
        if (exam == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Exam #" + id + " not found.");
            return "redirect:/exams";
        }

        boolean isStudent = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));

        long effectiveStudentId;
        if (isStudent) {
            Student currentStudent = studentRepository.findByEmail(auth.getName()).orElse(null);
            if (currentStudent == null) {
                throw new AccessDeniedException("No student record found for account: " + auth.getName());
            }
            // Enforce student ownership: prevent parameter tampering
            if (paramStudentId != null && paramStudentId != currentStudent.getId()) {
                throw new AccessDeniedException("Security violation: You cannot submit an exam as a different student!");
            }
            effectiveStudentId = currentStudent.getId();
        } else {
            if (paramStudentId == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Candidate selection is required.");
                return "redirect:/exams/" + id + "/take";
            }
            effectiveStudentId = paramStudentId;
        }

        Student student = studentRepository.findById(effectiveStudentId).orElse(null);
        if (student == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Student ID #" + effectiveStudentId + " not found.");
            return "redirect:/exams/" + id + "/take";
        }

        // Parse answers: for each question in the exam, check parameter "q_<questionId>"
        Map<Long, Integer> answers = new LinkedHashMap<>();
        for (Question<?> question : exam.getQuestions()) {
            String paramVal = request.getParameter("q_" + question.getId());
            if (paramVal != null && !paramVal.isBlank()) {
                try {
                    answers.put(question.getId(), Integer.parseInt(paramVal.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        try {
            // Exercise Review 1 multithreaded submission service with ReentrantLock and transaction commit
            CompletableFuture<Attempt> future = submissionService.submitAsync(exam, student, answers);
            Attempt attempt = future.join();

            redirectAttributes.addFlashAttribute("successMessage",
                "Exam submitted successfully! Score: " + attempt.getScore() + " / " + exam.totalMarks());
            return "redirect:/attempts/" + attempt.getId();

        } catch (CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof DuplicateSubmissionException) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Duplicate Submission Rejected: " + student.getName()
                        + " has already submitted this exam! Review 1 lock mechanism strictly prevents duplicate submissions.");
            } else if (cause instanceof ExamException) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Submission error: " + cause.getMessage());
            } else {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Unexpected submission error: " + cause.getMessage());
            }
            return "redirect:/exams/" + id + "/take?studentId=" + effectiveStudentId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Submission failed: " + e.getMessage());
            return "redirect:/exams/" + id + "/take?studentId=" + effectiveStudentId;
        }
    }
}
