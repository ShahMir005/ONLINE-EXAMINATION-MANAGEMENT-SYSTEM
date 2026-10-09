package edu.exampro.web.api;

import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Question;
import edu.exampro.model.Student;
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
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for individual exam attempts.
 *
 * <pre>
 *   GET /api/attempts/{id}  — Fetch the full scorecard for a single attempt.
 * </pre>
 *
 * <p>Authorization rules:
 * <ul>
 *   <li>Any authenticated user may call this endpoint.</li>
 *   <li>Students may only retrieve their <em>own</em> attempts; a student requesting
 *       another student's attempt receives {@code 403}.</li>
 *   <li>ADMIN and TEACHER may view any attempt.</li>
 * </ul>
 *
 * <p>Hard rules:
 * <ul>
 *   <li>Correct answer indices and text are <strong>never</strong> included in the response
 *       for a student — only ADMIN and TEACHER receive the {@code correctOption} fields.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/attempts")
public class AttemptApiController {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AttemptRepository attemptRepository;
    private final StudentRepository studentRepository;
    private final ExamCatalog examCatalog;

    public AttemptApiController(AttemptRepository attemptRepository,
                                StudentRepository studentRepository,
                                ExamCatalog examCatalog) {
        this.attemptRepository = attemptRepository;
        this.studentRepository = studentRepository;
        this.examCatalog = examCatalog;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean isStudent(Authentication auth) {
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_STUDENT".equals(ga.getAuthority()) || "STUDENT".equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean isStaff(Authentication auth) {
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if ("ROLE_ADMIN".equals(a) || "ADMIN".equals(a)
                || "ROLE_TEACHER".equals(a) || "TEACHER".equals(a)) {
                return true;
            }
        }
        return false;
    }

    // ── endpoints ─────────────────────────────────────────────────────────────

    /**
     * Returns the detailed scorecard for attempt {@code id}.
     *
     * <p>Response shape:
     * <pre>
     * {
     *   "attemptId": 7,
     *   "examId": 3,
     *   "examTitle": "Java Basics",
     *   "studentId": 2,
     *   "studentName": "Alice Smith",
     *   "studentEmail": "alice@example.com",
     *   "studentRegistration": "STU-001",
     *   "score": 12,
     *   "totalMarks": 15,
     *   "percentage": 80,
     *   "passed": true,
     *   "submittedAtFormatted": "Oct 05, 2026 14:30:00",
     *   "questionResults": [
     *     {
     *       "questionNumber": 1,
     *       "prompt": "...",
     *       "type": "MCQ",
     *       "marks": 5,
     *       "selectedOption": 1,
     *       "selectedText": "Java",
     *       "correct": true,
     *       "awardedMarks": 5,
     *       // correctOption + correctText only present for ADMIN/TEACHER
     *     }
     *   ]
     * }
     * </pre>
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAttempt(@PathVariable("id") long id, Authentication auth) {
        Optional<Attempt> maybeAttempt = attemptRepository.findById(id);
        if (maybeAttempt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Attempt not found", "status", 404));
        }
        Attempt attempt = maybeAttempt.get();

        // Ownership check — students may only view their own scorecard.
        if (isStudent(auth)) {
            Optional<Student> maybeStudent = studentRepository.findByEmail(auth.getName());
            if (maybeStudent.isEmpty()
                || !Objects.equals(attempt.getStudentId(), maybeStudent.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error",
                        "Access denied: You are not authorised to view another candidate's scorecard",
                        "status", 403));
            }
        }

        boolean staff = isStaff(auth);

        Exam exam = examCatalog.find(attempt.getExamId()).orElse(null);
        Student student = studentRepository.findById(attempt.getStudentId()).orElse(null);

        int totalMarks = exam != null ? exam.totalMarks() : 0;
        int percentage = totalMarks > 0
            ? (int) Math.round((double) attempt.getScore() * 100 / totalMarks) : 0;
        boolean passed = totalMarks > 0 && attempt.getScore() >= (totalMarks / 2);

        List<Map<String, Object>> questionResults = new ArrayList<>();
        if (exam != null) {
            int qNum = 1;
            for (Question<?> q : exam.getQuestions()) {
                Map<String, Object> qDto = new LinkedHashMap<>();
                qDto.put("questionNumber", qNum++);
                qDto.put("prompt", q.getPrompt());
                qDto.put("type", q.getType());
                qDto.put("marks", q.getMarks());

                Integer chosenOpt = attempt.getAnswers().get(q.getId());
                qDto.put("selectedOption", chosenOpt);

                List<String> choices = q.choices();

                // HARD RULE: correctOption / correctText only for ADMIN and TEACHER.
                if (staff) {
                    int correctOpt = q.getCorrectOptionIndex();
                    qDto.put("correctOption", correctOpt);
                    qDto.put("correctText", correctOpt >= 0 && correctOpt < choices.size()
                        ? choices.get(correctOpt) : "Option " + correctOpt);
                }

                if (chosenOpt != null && chosenOpt >= 0 && chosenOpt < choices.size()) {
                    qDto.put("selectedText", choices.get(chosenOpt));
                } else if (chosenOpt != null) {
                    qDto.put("selectedText", "Option " + chosenOpt);
                } else {
                    qDto.put("selectedText", "Unanswered");
                }

                boolean isCorrect = chosenOpt != null && q.isCorrectOption(chosenOpt);
                qDto.put("correct", isCorrect);
                qDto.put("awardedMarks", isCorrect ? q.getMarks() : 0);

                questionResults.add(qDto);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("attemptId", attempt.getId());
        body.put("examId", attempt.getExamId());
        body.put("examTitle", exam != null ? exam.getTitle() : "Exam #" + attempt.getExamId());
        body.put("studentId", attempt.getStudentId());
        body.put("studentName", student != null ? student.getName() : "Candidate #" + attempt.getStudentId());
        body.put("studentEmail", student != null ? student.getEmail() : "");
        body.put("studentRegistration", student != null ? student.getRegistrationNumber() : "");
        body.put("score", attempt.getScore());
        body.put("totalMarks", totalMarks);
        body.put("percentage", percentage);
        body.put("passed", passed);
        body.put("submittedAtFormatted", FORMATTER.format(attempt.getSubmittedAt()));
        body.put("questionResults", questionResults);

        return ResponseEntity.ok(body);
    }
}
