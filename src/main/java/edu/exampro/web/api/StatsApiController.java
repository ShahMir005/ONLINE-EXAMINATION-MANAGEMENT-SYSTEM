package edu.exampro.web.api;

import edu.exampro.app.ExamProApplication;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for dashboard statistics and demo data management.
 */
@RestController
public class StatsApiController {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final StudentRepository studentRepository;
    private final ExamCatalog examCatalog;
    private final AttemptRepository attemptRepository;
    private final AppUserRepository appUserRepository;

    public StatsApiController(StudentRepository studentRepository,
                              ExamCatalog examCatalog,
                              AttemptRepository attemptRepository,
                              AppUserRepository appUserRepository) {
        this.studentRepository = studentRepository;
        this.examCatalog = examCatalog;
        this.attemptRepository = attemptRepository;
        this.appUserRepository = appUserRepository;
    }

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null) return false;
        String target = "ROLE_" + role;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (ga.getAuthority().equals(target) || ga.getAuthority().equals(role)) {
                return true;
            }
        }
        return false;
    }

    @GetMapping("/api/stats")
    public ResponseEntity<Map<String, Object>> getStats(Authentication auth) {
        return ResponseEntity.ok(buildStats(auth));
    }

    @PostMapping("/api/stats")
    public ResponseEntity<Map<String, Object>> postStats(Authentication auth) {
        return ResponseEntity.ok(buildStats(auth));
    }

    private Map<String, Object> buildStats(Authentication auth) {
        List<Exam> exams = examCatalog.findAll();
        List<Student> students = studentRepository.findAll();
        List<Attempt> allAttempts = attemptRepository.findAll();

        Map<Long, Exam> examMap = exams.stream()
            .collect(Collectors.toMap(Exam::getId, e -> e, (e1, e2) -> e1));
        Map<Long, Student> studentMap = students.stream()
            .collect(Collectors.toMap(Student::getId, s -> s, (s1, s2) -> s1));

        int totalQuestions = exams.stream().mapToInt(e -> e.getQuestions().size()).sum();
        boolean isStudent = hasRole(auth, "STUDENT");

        List<Attempt> relevantAttempts = allAttempts;
        if (isStudent) {
            Student curStudent = auth != null ? studentRepository.findByEmail(auth.getName()).orElse(null) : null;
            if (curStudent != null) {
                final Long myId = curStudent.getId();
                relevantAttempts = allAttempts.stream()
                    .filter(a -> Objects.equals(a.getStudentId(), myId))
                    .toList();
            } else {
                relevantAttempts = List.of();
            }
        }

        int passedCount = 0;
        int totalScoreAccum = 0;
        for (Attempt a : relevantAttempts) {
            Exam exam = examMap.get(a.getExamId());
            int totalMarks = exam != null ? exam.totalMarks() : 0;
            int pct = totalMarks > 0 ? (int) Math.round((double) a.getScore() * 100 / totalMarks) : 0;
            boolean passed = totalMarks > 0 && a.getScore() >= (totalMarks / 2);
            if (passed) passedCount++;
            totalScoreAccum += pct;
        }

        int avgPct = relevantAttempts.isEmpty() ? 0 : Math.round((float) totalScoreAccum / relevantAttempts.size());
        int passRate = relevantAttempts.isEmpty() ? 0 : Math.round((float) passedCount * 100 / relevantAttempts.size());

        List<Map<String, Object>> recentAttempts = new ArrayList<>();
        int limit = Math.min(5, relevantAttempts.size());
        for (int i = 0; i < limit; i++) {
            Attempt a = relevantAttempts.get(i);
            Exam exam = examMap.get(a.getExamId());
            Student student = studentMap.get(a.getStudentId());

            int totalMarks = exam != null ? exam.totalMarks() : 0;
            int pct = totalMarks > 0 ? (int) Math.round((double) a.getScore() * 100 / totalMarks) : 0;
            boolean passed = totalMarks > 0 && a.getScore() >= (totalMarks / 2);

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("attemptId", a.getId());
            dto.put("examId", a.getExamId());
            dto.put("examTitle", exam != null ? exam.getTitle() : "Exam #" + a.getExamId());
            dto.put("studentId", a.getStudentId());
            dto.put("studentName", student != null ? student.getName() : "Candidate #" + a.getStudentId());
            dto.put("score", a.getScore());
            dto.put("totalMarks", totalMarks);
            dto.put("percentage", pct);
            dto.put("passed", passed);
            dto.put("submittedAtFormatted", FORMATTER.format(a.getSubmittedAt()));
            recentAttempts.add(dto);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalExams", exams.size());
        stats.put("totalQuestions", totalQuestions);
        stats.put("totalStudents", students.size());
        stats.put("totalAttempts", relevantAttempts.size());
        stats.put("avgPercentage", avgPct);
        stats.put("passRate", passRate);
        stats.put("recentAttempts", recentAttempts);

        if (hasRole(auth, "ADMIN")) {
            long userCount = appUserRepository.findAll().size();
            stats.put("totalUsers", userCount);
        }

        return stats;
    }

    @PostMapping("/api/demo/seed")
    public ResponseEntity<?> seedDemo(Authentication auth) {
        if (!hasRole(auth, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        try {
            ExamProApplication.run();
            return ResponseEntity.ok(Map.of("success", true, "message", "Demo data refreshed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Error refreshing demo data: " + e.getMessage()));
        }
    }
}
