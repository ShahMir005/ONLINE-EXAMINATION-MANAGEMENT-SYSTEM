package edu.exampro.web.controller;

import edu.exampro.model.AppUser;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final StudentRepository studentRepository;
    private final ExamCatalog examCatalog;
    private final AttemptRepository attemptRepository;
    private final AppUserRepository appUserRepository;

    public LoginController(StudentRepository studentRepository, ExamCatalog examCatalog,
                           AttemptRepository attemptRepository, AppUserRepository appUserRepository) {
        this.studentRepository = studentRepository;
        this.examCatalog = examCatalog;
        this.attemptRepository = attemptRepository;
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/admin/login")
    public String adminLogin(@RequestParam(value = "error", required = false) String error,
                             @RequestParam(value = "logout", required = false) String logout,
                             Model model) {
        model.addAttribute("role", "ADMIN");
        model.addAttribute("portalTitle", "Administrator Portal");
        model.addAttribute("loginAction", "/admin/login");
        model.addAttribute("error", error);
        model.addAttribute("logout", logout);
        return "login-admin";
    }

    @GetMapping("/teacher/login")
    public String teacherLogin(@RequestParam(value = "error", required = false) String error,
                               @RequestParam(value = "logout", required = false) String logout,
                               Model model) {
        model.addAttribute("role", "TEACHER");
        model.addAttribute("portalTitle", "Instructor & Teacher Portal");
        model.addAttribute("loginAction", "/teacher/login");
        model.addAttribute("error", error);
        model.addAttribute("logout", logout);
        return "login-teacher";
    }

    @GetMapping("/student/login")
    public String studentLogin(@RequestParam(value = "error", required = false) String error,
                               @RequestParam(value = "logout", required = false) String logout,
                               Model model) {
        model.addAttribute("role", "STUDENT");
        model.addAttribute("portalTitle", "Student Assessment Portal");
        model.addAttribute("loginAction", "/student/login");
        model.addAttribute("error", error);
        model.addAttribute("logout", logout);
        return "login-student";
    }

    @GetMapping("/access-denied")
    public String accessDenied(Authentication auth, Model model) {
        model.addAttribute("user", auth != null ? auth.getName() : "Anonymous");
        model.addAttribute("roles", auth != null ? auth.getAuthorities() : List.of());
        return "access-denied";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        List<Student> students = studentRepository.findAll();
        List<Exam> exams = examCatalog.findAll();
        List<Attempt> attempts = attemptRepository.findAll();
        List<AppUser> users = appUserRepository.findAll();

        int totalQuestions = exams.stream().mapToInt(e -> e.getQuestions().size()).sum();

        model.addAttribute("studentCount", students.size());
        model.addAttribute("examCount", exams.size());
        model.addAttribute("questionCount", totalQuestions);
        model.addAttribute("attemptCount", attempts.size());
        model.addAttribute("userCount", users.size());
        model.addAttribute("students", students);
        model.addAttribute("exams", exams);
        model.addAttribute("users", users);

        return "admin-dashboard";
    }

    @GetMapping("/teacher/dashboard")
    public String teacherDashboard(Model model) {
        List<Exam> exams = examCatalog.findAll();
        List<Attempt> attempts = attemptRepository.findAll();
        int totalQuestions = exams.stream().mapToInt(e -> e.getQuestions().size()).sum();

        model.addAttribute("examCount", exams.size());
        model.addAttribute("questionCount", totalQuestions);
        model.addAttribute("attemptCount", attempts.size());
        model.addAttribute("exams", exams);

        return "teacher-dashboard";
    }

    @GetMapping("/student/dashboard")
    public String studentDashboard(Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        Student currentStudent = studentRepository.findByEmail(email).orElse(null);
        List<Exam> allExams = examCatalog.findAll();

        List<AttemptViewDto> myAttempts = new ArrayList<>();
        int myScoreSum = 0;
        int myMarksSum = 0;

        if (currentStudent != null) {
            Map<Long, Exam> examMap = allExams.stream()
                .collect(Collectors.toMap(Exam::getId, e -> e, (e1, e2) -> e1));

            List<Attempt> attempts = attemptRepository.findAll().stream()
                .filter(a -> a.getStudentId() == currentStudent.getId())
                .toList();

            for (Attempt a : attempts) {
                Exam e = examMap.get(a.getExamId());
                AttemptViewDto dto = new AttemptViewDto();
                dto.setAttemptId(a.getId());
                dto.setExamId(a.getExamId());
                dto.setExamTitle(e != null ? e.getTitle() : "Exam #" + a.getExamId());
                dto.setStudentId(currentStudent.getId());
                dto.setStudentName(currentStudent.getName());
                dto.setScore(a.getScore());
                int totalMarks = e != null ? e.totalMarks() : 0;
                dto.setTotalMarks(totalMarks);
                int pct = totalMarks > 0 ? (int) Math.round((double) a.getScore() * 100 / totalMarks) : 0;
                dto.setPercentage(pct);
                dto.setPassed(totalMarks > 0 && a.getScore() >= (totalMarks / 2));
                dto.setSubmittedAtFormatted(FORMATTER.format(a.getSubmittedAt()));
                myAttempts.add(dto);

                myScoreSum += a.getScore();
                myMarksSum += totalMarks;
            }
        }

        int avgPct = myMarksSum > 0 ? (int) Math.round((double) myScoreSum * 100 / myMarksSum) : 0;

        model.addAttribute("student", currentStudent);
        model.addAttribute("exams", allExams);
        model.addAttribute("myAttempts", myAttempts);
        model.addAttribute("examsAttemptedCount", myAttempts.size());
        model.addAttribute("averageScorePct", avgPct);
        model.addAttribute("totalPointsEarned", myScoreSum);

        return "student-dashboard";
    }
}
