package edu.exampro.web.api;

import edu.exampro.exception.ExamException;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.MultipleChoiceQuestion;
import edu.exampro.model.Question;
import edu.exampro.model.Student;
import edu.exampro.model.TrueFalseQuestion;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import edu.exampro.service.ExamSessionService;
import edu.exampro.service.ExamSubmissionService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for exams — serving the single-page application.
 *
 * Enforces role authorization and strict ownership:
 *  - GET /api/exams              : Any authenticated user. Returns exam summaries.
 *  - GET /api/exams/{id}         : Any authenticated user. Detail view.
 *                                  CRITICAL: Correct answers are NEVER returned to students!
 *  - POST /api/exams             : ADMIN and TEACHER only. Creates new exam.
 *  - DELETE /api/exams/{id}      : ADMIN and TEACHER only. Deletes exam.
 *  - POST /api/exams/{id}/submit : STUDENT and ADMIN only. Submits candidate answers.
 */
@RestController
@RequestMapping("/api/exams")
public class ExamApiController {

    private final ExamCatalog examCatalog;
    private final StudentRepository studentRepository;
    private final ExamSubmissionService submissionService;
    private final ExamSessionService examSessionService;

    public ExamApiController(ExamCatalog examCatalog,
                             StudentRepository studentRepository,
                             ExamSubmissionService submissionService,
                             ExamSessionService examSessionService) {
        this.examCatalog = examCatalog;
        this.studentRepository = studentRepository;
        this.submissionService = submissionService;
        this.examSessionService = examSessionService;
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

    /**
     * Lists all exams. Returns basic summaries (no questions or answers).
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listExams() {
        List<Exam> exams = examCatalog.findAll();
        List<Map<String, Object>> response = new ArrayList<>();
        for (Exam exam : exams) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", exam.getId());
            item.put("title", exam.getTitle());
            item.put("durationMinutes", exam.getDuration().toMinutes());
            item.put("totalMarks", exam.totalMarks());
            item.put("questionCount", exam.getQuestions().size());
            response.add(item);
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Detailed view of an exam.
     * HARD RULE: Correct answers are NEVER sent to a student!
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getExam(@PathVariable("id") long id, Authentication auth) {
        Optional<Exam> maybeExam = examCatalog.find(id);
        if (maybeExam.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Exam not found"));
        }

        Exam exam = maybeExam.get();
        boolean isStaff = hasRole(auth, "ADMIN") || hasRole(auth, "TEACHER");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", exam.getId());
        body.put("title", exam.getTitle());
        body.put("durationMinutes", exam.getDuration().toMinutes());
        body.put("totalMarks", exam.totalMarks());
        body.put("questionCount", exam.getQuestions().size());

        List<Map<String, Object>> questionList = new ArrayList<>();
        for (Question<?> q : exam.getQuestions()) {
            Map<String, Object> qMap = new LinkedHashMap<>();
            qMap.put("id", q.getId());
            qMap.put("prompt", q.getPrompt());
            qMap.put("type", q.getType());
            qMap.put("marks", q.getMarks());
            qMap.put("choices", q.choices());

            // HARD RULE: Only ADMIN and TEACHER may see the correct choice index.
            if (isStaff) {
                qMap.put("correctOptionIndex", q.getCorrectOptionIndex());
            }

            questionList.add(qMap);
        }
        body.put("questions", questionList);

        return ResponseEntity.ok(body);
    }

    /**
     * Creates a new exam with questions. Allowed for ADMIN and TEACHER only.
     */
    @PostMapping
    public ResponseEntity<?> createExam(@RequestBody CreateExamRequest req, Authentication auth) {
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "TEACHER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Only administrators and teachers can create exams"));
        }

        if (req.title == null || req.title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Exam title is required"));
        }
        if (req.durationMinutes < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "Duration must be at least 1 minute"));
        }
        if (req.questions == null || req.questions.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "An exam must have at least one question"));
        }

        Exam exam = new Exam(null, req.title.trim(), Duration.ofMinutes(req.durationMinutes));

        int idx = 1;
        for (CreateQuestionRequest qr : req.questions) {
            if (qr.prompt == null || qr.prompt.isBlank()) {
                continue;
            }
            int marks = qr.marks > 0 ? qr.marks : 1;

            if ("TF".equalsIgnoreCase(qr.type)) {
                boolean tfCorrect = qr.correctOptionIndex == 0 || qr.tfCorrect;
                exam.addQuestion(new TrueFalseQuestion(null, qr.prompt.trim(), marks, tfCorrect));
            } else {
                List<String> validChoices = new ArrayList<>();
                if (qr.choices != null) {
                    for (String c : qr.choices) {
                        if (c != null && !c.isBlank()) {
                            validChoices.add(c.trim());
                        }
                    }
                }
                if (validChoices.size() < 2) {
                    return ResponseEntity.badRequest().body(Map.of("error",
                        "Question #" + idx + " must have at least two choices"));
                }
                int correctOpt = qr.correctOptionIndex >= 0 && qr.correctOptionIndex < validChoices.size()
                    ? qr.correctOptionIndex : 0;
                exam.addQuestion(new MultipleChoiceQuestion(null, qr.prompt.trim(), marks, validChoices, correctOpt));
            }
            idx++;
        }

        if (exam.getQuestions().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please provide at least one valid question"));
        }

        Exam created = examCatalog.create(exam);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", created.getId());
        resp.put("title", created.getTitle());
        resp.put("durationMinutes", created.getDuration().toMinutes());
        resp.put("totalMarks", created.totalMarks());
        resp.put("questionCount", created.getQuestions().size());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * Deletes an exam. Allowed for ADMIN and TEACHER only.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteExam(@PathVariable("id") long id, Authentication auth) {
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "TEACHER")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Only administrators and teachers can delete exams"));
        }

        boolean deleted = examCatalog.delete(id);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Exam not found"));
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * Submits candidate answers for scoring.
     * Allowed for STUDENT and ADMIN.
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitExam(@PathVariable("id") long id,
                                       @RequestBody SubmitExamRequest req,
                                       Authentication auth) {
        if (!hasRole(auth, "STUDENT") && !hasRole(auth, "ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Teachers cannot submit exam attempts"));
        }

        Optional<Exam> maybeExam = examCatalog.find(id);
        if (maybeExam.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Exam not found"));
        }
        Exam exam = maybeExam.get();

        Student effectiveStudent;
        if (hasRole(auth, "STUDENT")) {
            Optional<Student> maybeStudent = studentRepository.findByEmail(auth.getName());
            if (maybeStudent.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No candidate profile found for current user account"));
            }
            Student currentStudent = maybeStudent.get();

            // Prevent tampering: a student cannot submit as another student ID
            if (req.studentId != null && !req.studentId.equals(currentStudent.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Security violation: You cannot submit an exam as a different student"));
            }
            effectiveStudent = currentStudent;
        } else {
            // ADMIN submitting on behalf of a candidate
            if (req.studentId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Candidate selection is required"));
            }
            Optional<Student> maybeStudent = studentRepository.findById(req.studentId);
            if (maybeStudent.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Candidate not found"));
            }
            effectiveStudent = maybeStudent.get();
        }

        Map<Long, Integer> answers = req.answers != null ? req.answers : Map.of();

        try {
            CompletableFuture<Attempt> future = submissionService.submitAsync(exam, effectiveStudent, answers);
            Attempt attempt = future.join();

            int totalMarks = exam.totalMarks();
            boolean passed = totalMarks > 0 && attempt.getScore() >= (totalMarks / 2);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("attemptId", attempt.getId());
            resp.put("examId", exam.getId());
            resp.put("examTitle", exam.getTitle());
            resp.put("studentId", effectiveStudent.getId());
            resp.put("studentName", effectiveStudent.getName());
            resp.put("score", attempt.getScore());
            resp.put("totalMarks", totalMarks);
            resp.put("passed", passed);

            return ResponseEntity.ok(resp);
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof ExamException examException) {
                throw examException;
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An error occurred while recording the exam attempt"));
        }
    }

    /**
     * Returns the complete answer key for every question in the exam.
     *
     * <p><strong>HARD RULE:</strong> This endpoint is gated to {@code ADMIN} and {@code TEACHER}
     * roles at the security filter level ({@link edu.exampro.security.SpaSecurityConfig}).
     * Students must never receive correct answers — access-denied is enforced before
     * this method body executes.
     */
    @GetMapping("/{id}/key")
    public ResponseEntity<?> getAnswerKey(@PathVariable("id") long id, Authentication auth) {
        if (!hasRole(auth, "ADMIN") && !hasRole(auth, "TEACHER")) {
            // Double-check: SpaSecurityConfig already blocks students, but defend in depth.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Only administrators and teachers may view answer keys"));
        }

        Optional<Exam> maybeExam = examCatalog.find(id);
        if (maybeExam.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Exam not found"));
        }
        Exam exam = maybeExam.get();

        List<Map<String, Object>> keys = new ArrayList<>();
        int qNum = 1;
        for (Question<?> q : exam.getQuestions()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("questionNumber", qNum++);
            entry.put("questionId", q.getId());
            entry.put("prompt", q.getPrompt());
            entry.put("type", q.getType());
            entry.put("marks", q.getMarks());
            entry.put("choices", q.choices());
            entry.put("correctOptionIndex", q.getCorrectOptionIndex());
            List<String> choices = q.choices();
            int ci = q.getCorrectOptionIndex();
            entry.put("correctOptionText", ci >= 0 && ci < choices.size() ? choices.get(ci) : "");
            keys.add(entry);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("examId", exam.getId());
        body.put("examTitle", exam.getTitle());
        body.put("totalMarks", exam.totalMarks());
        body.put("answerKey", keys);
        return ResponseEntity.ok(body);
    }

    /**
     * Records the moment a student begins an exam and returns the exam questions (no answers).
     * Re-entrant: if the student already has an active session the original start time is returned
     * so the timer cannot be reset by reloading the page.
     *
     * <p>Allowed for {@code STUDENT} and {@code ADMIN}. Ownership is enforced: students can only
     * start exams for themselves.
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<?> startExam(@PathVariable("id") long id, Authentication auth) {
        Optional<Exam> maybeExam = examCatalog.find(id);
        if (maybeExam.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Exam not found"));
        }
        Exam exam = maybeExam.get();

        // Resolve the student for whom the session is being opened.
        Student effectiveStudent;
        if (hasRole(auth, "STUDENT")) {
            Optional<Student> maybeStudent = studentRepository.findByEmail(auth.getName());
            if (maybeStudent.isEmpty()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No candidate profile found for your account"));
            }
            effectiveStudent = maybeStudent.get();
        } else if (hasRole(auth, "ADMIN")) {
            // Admins can preview any exam — session is not enforced for admin submissions.
            return ResponseEntity.ok(Map.of(
                "examId", exam.getId(),
                "examTitle", exam.getTitle(),
                "durationMinutes", exam.getDuration().toMinutes(),
                "note", "Administrator preview — no session timer recorded"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Only students may start an examination session"));
        }

        Instant startTime = examSessionService.recordOrGetSession(exam.getId(), effectiveStudent.getId());

        // Build the question list WITHOUT correct answers (HARD RULE).
        List<Map<String, Object>> questionList = new ArrayList<>();
        for (Question<?> q : exam.getQuestions()) {
            Map<String, Object> qMap = new LinkedHashMap<>();
            qMap.put("id", q.getId());
            qMap.put("prompt", q.getPrompt());
            qMap.put("type", q.getType());
            qMap.put("marks", q.getMarks());
            qMap.put("choices", q.choices());
            // correctOptionIndex is intentionally OMITTED for student sessions.
            questionList.add(qMap);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("examId", exam.getId());
        body.put("examTitle", exam.getTitle());
        body.put("durationMinutes", exam.getDuration().toMinutes());
        body.put("totalMarks", exam.totalMarks());
        body.put("sessionStartedAt", startTime.toString());
        body.put("deadlineAt", startTime.plus(exam.getDuration()).plusSeconds(30).toString());
        body.put("studentId", effectiveStudent.getId());
        body.put("questions", questionList);
        return ResponseEntity.ok(body);
    }

    /* ── Request DTOs ── */

    public static class CreateExamRequest {
        public String title;
        public int durationMinutes = 30;
        public List<CreateQuestionRequest> questions = new ArrayList<>();
    }

    public static class CreateQuestionRequest {
        public String prompt;
        public String type = "MCQ"; // "MCQ" or "TF"
        public int marks = 1;
        public List<String> choices = new ArrayList<>();
        public int correctOptionIndex = 0;
        public boolean tfCorrect = false;
    }

    public static class SubmitExamRequest {
        public Long studentId;
        public Map<Long, Integer> answers = new LinkedHashMap<>();
    }
}
