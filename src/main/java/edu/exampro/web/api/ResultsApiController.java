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
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for exam results and detailed candidate scorecards.
 *
 * Enforces ownership:
 *  - Students can ONLY list and view their own attempts.
 *  - Administrators and Teachers can view all attempts and filter by exam.
 */
@RestController
@RequestMapping("/api/results")
public class ResultsApiController {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AttemptRepository attemptRepository;
    private final StudentRepository studentRepository;
    private final ExamCatalog examCatalog;

    public ResultsApiController(AttemptRepository attemptRepository,
                                StudentRepository studentRepository,
                                ExamCatalog examCatalog) {
        this.attemptRepository = attemptRepository;
        this.studentRepository = studentRepository;
        this.examCatalog = examCatalog;
    }

    private boolean isStudent(Authentication auth) {
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_STUDENT".equals(ga.getAuthority()) || "STUDENT".equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<?> listResults(@RequestParam(value = "examId", required = false) Long filterExamId,
                                         Authentication auth) {
        boolean studentRole = isStudent(auth);
        Student currentStudent = null;
        if (studentRole) {
            currentStudent = studentRepository.findByEmail(auth.getName()).orElse(null);
            if (currentStudent == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No candidate profile found for user account"));
            }
        }

        List<Exam> allExams = examCatalog.findAll();
        List<Student> allStudents = studentRepository.findAll();

        Map<Long, Exam> examMap = allExams.stream()
            .collect(Collectors.toMap(Exam::getId, e -> e, (e1, e2) -> e1));
        Map<Long, Student> studentMap = allStudents.stream()
            .collect(Collectors.toMap(Student::getId, s -> s, (s1, s2) -> s1));

        List<Attempt> rawAttempts = (filterExamId != null && filterExamId > 0)
            ? attemptRepository.findByExamId(filterExamId)
            : attemptRepository.findAll();

        // Ownership rule: students can ONLY view their own attempts
        if (studentRole) {
            final Long myId = currentStudent.getId();
            rawAttempts = rawAttempts.stream()
                .filter(a -> Objects.equals(a.getStudentId(), myId))
                .toList();
        }

        List<Map<String, Object>> resultDtos = new ArrayList<>();
        int passedCount = 0;
        int totalScoreAccum = 0;
        int maxScoreSeen = 0;

        for (Attempt attempt : rawAttempts) {
            Exam exam = examMap.get(attempt.getExamId());
            if (exam == null) {
                exam = examCatalog.find(attempt.getExamId()).orElse(null);
            }
            Student student = studentMap.get(attempt.getStudentId());
            if (student == null) {
                student = studentRepository.findById(attempt.getStudentId()).orElse(null);
            }

            int totalMarks = exam != null ? exam.totalMarks() : 0;
            int pct = totalMarks > 0 ? (int) Math.round((double) attempt.getScore() * 100 / totalMarks) : 0;
            boolean passed = totalMarks > 0 && attempt.getScore() >= (totalMarks / 2);
            if (passed) passedCount++;
            totalScoreAccum += pct;
            if (attempt.getScore() > maxScoreSeen) maxScoreSeen = attempt.getScore();

            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("attemptId", attempt.getId());
            dto.put("examId", attempt.getExamId());
            dto.put("examTitle", exam != null ? exam.getTitle() : "Exam #" + attempt.getExamId());
            dto.put("studentId", attempt.getStudentId());
            dto.put("studentName", student != null ? student.getName() : "Student #" + attempt.getStudentId());
            dto.put("studentEmail", student != null ? student.getEmail() : "");
            dto.put("studentRegistration", student != null ? student.getRegistrationNumber() : "");
            dto.put("score", attempt.getScore());
            dto.put("totalMarks", totalMarks);
            dto.put("percentage", pct);
            dto.put("passed", passed);
            dto.put("submittedAtFormatted", FORMATTER.format(attempt.getSubmittedAt()));

            resultDtos.add(dto);
        }

        int avgPct = resultDtos.isEmpty() ? 0 : Math.round((float) totalScoreAccum / resultDtos.size());
        int passRate = resultDtos.isEmpty() ? 0 : Math.round((float) passedCount * 100 / resultDtos.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", resultDtos);
        response.put("totalAttempts", resultDtos.size());
        response.put("avgPercentage", avgPct);
        response.put("maxScore", maxScoreSeen);
        response.put("passRate", passRate);
        response.put("isStudent", studentRole);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getScorecard(@PathVariable("id") long id, Authentication auth) {
        Optional<Attempt> maybeAttempt = attemptRepository.findById(id);
        if (maybeAttempt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Attempt not found"));
        }
        Attempt attempt = maybeAttempt.get();

        // Ownership enforcement for student role
        if (isStudent(auth)) {
            Optional<Student> maybeStudent = studentRepository.findByEmail(auth.getName());
            if (maybeStudent.isEmpty() || !Objects.equals(attempt.getStudentId(), maybeStudent.get().getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied: You are not authorized to view another candidate's scorecard"));
            }
        }

        boolean isStaff = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ADMIN".equals(a.getAuthority())
                        || "ROLE_TEACHER".equals(a.getAuthority()) || "TEACHER".equals(a.getAuthority()));

        Exam exam = examCatalog.find(attempt.getExamId()).orElse(null);
        Student student = studentRepository.findById(attempt.getStudentId()).orElse(null);

        int totalMarks = exam != null ? exam.totalMarks() : 0;
        int percentage = totalMarks > 0 ? (int) Math.round((double) attempt.getScore() * 100 / totalMarks) : 0;
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
                // HARD RULE: Correct answers are only sent to ADMIN/TEACHER
                if (isStaff) {
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
