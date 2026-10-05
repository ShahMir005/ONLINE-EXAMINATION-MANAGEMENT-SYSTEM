package edu.exampro.web.controller;

import edu.exampro.app.ExamProApplication;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Student;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import edu.exampro.web.dto.AttemptViewDto;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final StudentRepository studentRepository;
    private final ExamCatalog examCatalog;
    private final AttemptRepository attemptRepository;

    public HomeController(StudentRepository studentRepository, ExamCatalog examCatalog, AttemptRepository attemptRepository) {
        this.studentRepository = studentRepository;
        this.examCatalog = examCatalog;
        this.attemptRepository = attemptRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<Student> students = studentRepository.findAll();
        List<Exam> exams = examCatalog.findAll();
        List<Attempt> attempts = attemptRepository.findAll();

        int totalQuestions = exams.stream().mapToInt(e -> e.getQuestions().size()).sum();

        Map<Long, Student> studentMap = students.stream()
            .collect(Collectors.toMap(Student::getId, s -> s, (s1, s2) -> s1));
        Map<Long, Exam> examMap = exams.stream()
            .collect(Collectors.toMap(Exam::getId, e -> e, (e1, e2) -> e1));

        List<AttemptViewDto> recentAttempts = new ArrayList<>();
        int limit = Math.min(5, attempts.size());
        for (int i = 0; i < limit; i++) {
            Attempt a = attempts.get(i);
            Student s = studentMap.get(a.getStudentId());
            Exam e = examMap.get(a.getExamId());
            AttemptViewDto dto = new AttemptViewDto();
            dto.setAttemptId(a.getId());
            dto.setExamId(a.getExamId());
            dto.setExamTitle(e != null ? e.getTitle() : "Exam #" + a.getExamId());
            dto.setStudentId(a.getStudentId());
            dto.setStudentName(s != null ? s.getName() : "Unknown");
            dto.setStudentEmail(s != null ? s.getEmail() : "");
            dto.setStudentRegistration(s != null ? s.getRegistrationNumber() : "");
            dto.setScore(a.getScore());
            int totalMarks = e != null ? e.totalMarks() : 0;
            dto.setTotalMarks(totalMarks);
            dto.setPercentage(totalMarks > 0 ? (int) Math.round((double) a.getScore() * 100 / totalMarks) : 0);
            dto.setPassed(totalMarks > 0 && a.getScore() >= (totalMarks / 2));
            dto.setSubmittedAtFormatted(FORMATTER.format(a.getSubmittedAt()));
            recentAttempts.add(dto);
        }

        model.addAttribute("studentCount", students.size());
        model.addAttribute("examCount", exams.size());
        model.addAttribute("attemptCount", attempts.size());
        model.addAttribute("questionCount", totalQuestions);
        model.addAttribute("exams", exams);
        model.addAttribute("recentAttempts", recentAttempts);

        return "index";
    }

    @PostMapping("/demo/seed")
    public String seedDemo(RedirectAttributes redirectAttributes) {
        try {
            ExamProApplication.run();
            redirectAttributes.addFlashAttribute("successMessage",
                "Review 1 features executed & demo data refreshed successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Error refreshing demo data: " + e.getMessage());
        }
        return "redirect:/";
    }
}
