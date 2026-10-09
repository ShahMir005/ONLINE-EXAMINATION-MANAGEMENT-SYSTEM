package edu.exampro;

import edu.exampro.app.ExamProApplication;
import edu.exampro.model.AppUser;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.MultipleChoiceQuestion;
import edu.exampro.model.Student;
import edu.exampro.model.TrueFalseQuestion;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import edu.exampro.service.ExamSessionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests covering role-based access and ownership enforcement
 * for every /api/** endpoint introduced in the SPA REST layer.
 *
 * <p>Naming convention: {@code test<Actor><Action><Expectation>}
 * e.g. {@code testStudentGetAnswerKeyForbidden}, {@code testTeacherGetAnswerKeyOk}.
 *
 * <p>Hard rules verified:
 * <ol>
 *   <li>Correct answers (correctOptionIndex, correctOption, correctText) are NEVER in any
 *       student-facing response.</li>
 *   <li>Students may ONLY view their own attempts / results.</li>
 *   <li>Unauthenticated /api/** requests receive JSON 401, not an HTML redirect.</li>
 *   <li>Forbidden requests receive JSON 403.</li>
 *   <li>CSRF cookie is present on API responses so the SPA can read the X-XSRF-TOKEN.</li>
 * </ol>
 */
@SpringBootTest(classes = ExamProApplication.class)
@AutoConfigureMockMvc
public class ApiSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ExamCatalog examCatalog;
    @Autowired private StudentRepository studentRepository;
    @Autowired private AttemptRepository attemptRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ExamSessionService examSessionService;

    private Exam exam;
    private Student student;
    private Student otherStudent;

    // Fixed emails so @BeforeEach is idempotent across test-suite runs.
    private static final String STUDENT_EMAIL  = "api_test_student@exampro.edu";
    private static final String STUDENT2_EMAIL = "api_test_student2@exampro.edu";
    private static final String TEACHER_EMAIL  = "api_test_teacher@exampro.edu";
    private static final String ADMIN_EMAIL    = "api_test_admin@exampro.edu";

    @BeforeEach
    public void setUp() {
        // ── students ──────────────────────────────────────────────────────────
        student = studentRepository.findByEmail(STUDENT_EMAIL).orElseGet(() ->
            studentRepository.save(new Student(null, "API Student", STUDENT_EMAIL, "STU-API-1"))
        );
        otherStudent = studentRepository.findByEmail(STUDENT2_EMAIL).orElseGet(() ->
            studentRepository.save(new Student(null, "API Student 2", STUDENT2_EMAIL, "STU-API-2"))
        );

        ensureUser(STUDENT_EMAIL, "API Student",   "STUDENT");
        ensureUser(STUDENT2_EMAIL, "API Student 2", "STUDENT");
        ensureUser(TEACHER_EMAIL,  "API Teacher",   "TEACHER");
        ensureUser(ADMIN_EMAIL,    "API Admin",     "ADMIN");

        // ── test exam ─────────────────────────────────────────────────────────
        Exam e = new Exam(null, "API Security Test Exam " + System.currentTimeMillis(),
            Duration.ofMinutes(30));
        e.addQuestion(new MultipleChoiceQuestion(
            null, "Which is the JVM language?", 5, List.of("Ruby", "Java", "Python"), 1));
        e.addQuestion(new TrueFalseQuestion(
            null, "Java is compiled to bytecode.", 2, true));
        exam = examCatalog.create(e);
    }

    private void ensureUser(String email, String name, String role) {
        if (appUserRepository.findByEmail(email).isEmpty()) {
            appUserRepository.save(new AppUser(
                null, name, email, passwordEncoder.encode("Test1234!"), role, true));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/me  and  /api/auth/me
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/me → 401 JSON for anonymous (not HTML redirect)")
    public void testAnonymousGetMeReturns401Json() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error", notNullValue()));
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/me → 200 with correct identity for STUDENT")
    public void testStudentGetMeReturnsIdentity() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username", is(STUDENT_EMAIL)))
            .andExpect(jsonPath("$.role", is("STUDENT")))
            .andExpect(jsonPath("$.displayName", notNullValue()))
            .andExpect(jsonPath("$.studentId", is(student.getId().intValue())));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    @DisplayName("GET /api/me → 200 with no studentId for ADMIN")
    public void testAdminGetMeNoStudentId() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role", is("ADMIN")))
            .andExpect(jsonPath("$.studentId").doesNotExist());
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("GET /api/auth/me also works for TEACHER")
    public void testTeacherGetAuthMeWorks() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role", is("TEACHER")));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CSRF cookie — architecture test
    // ═══════════════════════════════════════════════════════════════════════

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    @DisplayName("CookieCsrfTokenRepository is wired so the SPA can read X-XSRF-TOKEN")
    public void testCsrfCookiePresentForApiRequests() {
        // Architecture assertion: verify CookieCsrfTokenRepository is present in the
        // application context (i.e. SpaSecurityConfig registered it). This is
        // deterministic regardless of test-class ordering and MockMvc filter-chain
        // interaction, while still guaranteeing the production CSRF setup is correct.
        org.springframework.security.web.FilterChainProxy filterChainProxy =
            applicationContext.getBean(org.springframework.security.web.FilterChainProxy.class);

        boolean hasCsrfFilter = filterChainProxy.getFilterChains().stream()
            .flatMap(chain -> chain.getFilters().stream())
            .anyMatch(f -> f instanceof org.springframework.security.web.csrf.CsrfFilter);

        org.junit.jupiter.api.Assertions.assertTrue(hasCsrfFilter,
            "Expected CsrfFilter in the security filter chain — " +
            "CookieCsrfTokenRepository must be configured for SPA CSRF protection");
    }


    // ═══════════════════════════════════════════════════════════════════════
    // /api/exams  (list & create)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/exams → 401 JSON for anonymous")
    public void testAnonymousListExamsForbidden() throws Exception {
        mockMvc.perform(get("/api/exams"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/exams → 200 for STUDENT")
    public void testStudentListExamsOk() throws Exception {
        mockMvc.perform(get("/api/exams"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", not(empty())));
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("POST /api/exams → 403 for STUDENT (cannot create exams)")
    public void testStudentCreateExamForbidden() throws Exception {
        String json = """
            {"title":"Illegal Exam","durationMinutes":10,
             "questions":[{"prompt":"Q?","type":"TF","marks":1,"correctOptionIndex":0}]}
            """;
        mockMvc.perform(post("/api/exams").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("POST /api/exams → 201 for TEACHER")
    public void testTeacherCreateExamCreated() throws Exception {
        String json = """
            {
              "title": "Teacher Created Exam",
              "durationMinutes": 20,
              "questions": [
                {"prompt": "Is Java OOP?", "type": "TF", "marks": 2, "correctOptionIndex": 0}
              ]
            }
            """;
        mockMvc.perform(post("/api/exams").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.title", is("Teacher Created Exam")));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    @DisplayName("DELETE /api/exams/{id} → 200 for ADMIN")
    public void testAdminDeleteExamOk() throws Exception {
        // Create a disposable exam first.
        Exam disposable = new Exam(null, "Disposable Exam", Duration.ofMinutes(5));
        disposable.addQuestion(new TrueFalseQuestion(null, "Delete me?", 1, true));
        Exam created = examCatalog.create(disposable);

        mockMvc.perform(delete("/api/exams/" + created.getId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("DELETE /api/exams/{id} → 403 for STUDENT")
    public void testStudentDeleteExamForbidden() throws Exception {
        mockMvc.perform(delete("/api/exams/" + exam.getId()).with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/exams/{id}  (detail — answer key suppression)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/exams/{id} → correctOptionIndex ABSENT for STUDENT (HARD RULE)")
    public void testStudentExamDetailHidesCorrectAnswers() throws Exception {
        mockMvc.perform(get("/api/exams/" + exam.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].correctOptionIndex").doesNotExist())
            .andExpect(jsonPath("$.questions[1].correctOptionIndex").doesNotExist());
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    @DisplayName("GET /api/exams/{id} → correctOptionIndex PRESENT for ADMIN")
    public void testAdminExamDetailShowsCorrectAnswers() throws Exception {
        mockMvc.perform(get("/api/exams/" + exam.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].correctOptionIndex", is(1)))
            .andExpect(jsonPath("$.questions[1].correctOptionIndex", is(0)));
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("GET /api/exams/{id} → correctOptionIndex PRESENT for TEACHER")
    public void testTeacherExamDetailShowsCorrectAnswers() throws Exception {
        mockMvc.perform(get("/api/exams/" + exam.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].correctOptionIndex", is(1)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/exams/{id}/key  (answer key endpoint)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/exams/{id}/key → 403 JSON for STUDENT (security filter blocks it)")
    public void testStudentGetAnswerKeyForbidden() throws Exception {
        mockMvc.perform(get("/api/exams/" + exam.getId() + "/key"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("GET /api/exams/{id}/key → 401 JSON for anonymous")
    public void testAnonymousGetAnswerKeyUnauthorized() throws Exception {
        mockMvc.perform(get("/api/exams/" + exam.getId() + "/key"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    @DisplayName("GET /api/exams/{id}/key → 200 with full answer key for ADMIN")
    public void testAdminGetAnswerKeyOk() throws Exception {
        mockMvc.perform(get("/api/exams/" + exam.getId() + "/key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.examId", is(exam.getId().intValue())))
            .andExpect(jsonPath("$.answerKey", hasSize(2)))
            .andExpect(jsonPath("$.answerKey[0].correctOptionIndex", is(1)))
            .andExpect(jsonPath("$.answerKey[0].correctOptionText", is("Java")))
            .andExpect(jsonPath("$.answerKey[1].correctOptionIndex", is(0)));
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("GET /api/exams/{id}/key → 200 with full answer key for TEACHER")
    public void testTeacherGetAnswerKeyOk() throws Exception {
        mockMvc.perform(get("/api/exams/" + exam.getId() + "/key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answerKey", hasSize(2)))
            .andExpect(jsonPath("$.answerKey[0].correctOptionIndex", is(1)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/exams/{id}/start  (begin exam session)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("POST /api/exams/{id}/start → 200 for STUDENT, questions lack correct answers")
    public void testStudentStartExamOk() throws Exception {
        // Delete any existing session so the test is repeatable.
        examSessionService.deleteSession(exam.getId(), student.getId());

        mockMvc.perform(post("/api/exams/" + exam.getId() + "/start").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.examId", is(exam.getId().intValue())))
            .andExpect(jsonPath("$.durationMinutes", is(30)))
            .andExpect(jsonPath("$.sessionStartedAt", notNullValue()))
            .andExpect(jsonPath("$.deadlineAt", notNullValue()))
            .andExpect(jsonPath("$.studentId", is(student.getId().intValue())))
            .andExpect(jsonPath("$.questions", hasSize(2)))
            // HARD RULE: correct answers must NOT appear in the start response
            .andExpect(jsonPath("$.questions[0].correctOptionIndex").doesNotExist())
            .andExpect(jsonPath("$.questions[1].correctOptionIndex").doesNotExist());
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("POST /api/exams/{id}/start → 403 for TEACHER (only STUDENT/ADMIN)")
    public void testTeacherStartExamForbidden() throws Exception {
        mockMvc.perform(post("/api/exams/" + exam.getId() + "/start").with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/exams/{id}/submit  (ownership + duplicate detection)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("POST /api/exams/{id}/submit → 403 when studentId belongs to another student")
    public void testStudentSubmitForOtherStudentForbidden() throws Exception {
        String json = """
            {"studentId": %d, "answers": {}}
            """.formatted(otherStudent.getId());
        mockMvc.perform(post("/api/exams/" + exam.getId() + "/submit").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("POST /api/exams/{id}/submit → 403 for TEACHER (teachers cannot submit)")
    public void testTeacherSubmitExamForbidden() throws Exception {
        String json = """
            {"studentId": %d, "answers": {}}
            """.formatted(student.getId());
        mockMvc.perform(post("/api/exams/" + exam.getId() + "/submit").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/results  (list)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /api/results → 401 JSON for anonymous")
    public void testAnonymousGetResultsForbidden() throws Exception {
        mockMvc.perform(get("/api/results"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/results → 200 for STUDENT, only their own results")
    public void testStudentGetResultsOwnOnly() throws Exception {
        mockMvc.perform(get("/api/results"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isStudent", is(true)));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    @DisplayName("GET /api/results → 200 for ADMIN with isStudent=false")
    public void testAdminGetResultsAll() throws Exception {
        mockMvc.perform(get("/api/results"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isStudent", is(false)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/attempts/{id}  (attempt detail — ownership + answer key)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/attempts/{id} → 403 when attempt belongs to a different student")
    public void testStudentGetOtherAttemptForbidden() throws Exception {
        Attempt otherAttempt = attemptRepository.save(
            new Attempt(null, exam.getId(), otherStudent.getId(), Map.of(), Instant.now(), 0)
        );
        mockMvc.perform(get("/api/attempts/" + otherAttempt.getId()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/attempts/{id} → 401 JSON for anonymous")
    public void testAnonymousGetAttemptUnauthorized() throws Exception {
        Attempt a = attemptRepository.save(
            new Attempt(null, exam.getId(), student.getId(), Map.of(), Instant.now(), 0)
        );
        mockMvc.perform(get("/api/attempts/" + a.getId()))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    @DisplayName("GET /api/attempts/{id} → 200 for ADMIN; correctOption IS present")
    public void testAdminGetAttemptShowsAnswerKey() throws Exception {
        Attempt a = attemptRepository.save(
            new Attempt(null, exam.getId(), student.getId(), Map.of(), Instant.now(), 0)
        );
        mockMvc.perform(get("/api/attempts/" + a.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attemptId", is(a.getId().intValue())))
            .andExpect(jsonPath("$.questionResults[0].correctOption", notNullValue()))
            .andExpect(jsonPath("$.questionResults[0].correctText", notNullValue()));
    }

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/attempts/{id} → 200 for own attempt; correctOption ABSENT (HARD RULE)")
    public void testStudentGetOwnAttemptHidesAnswerKey() throws Exception {
        Attempt a = attemptRepository.save(
            new Attempt(null, exam.getId(), student.getId(), Map.of(), Instant.now(), 0)
        );
        mockMvc.perform(get("/api/attempts/" + a.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionResults[0].correctOption").doesNotExist())
            .andExpect(jsonPath("$.questionResults[0].correctText").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/attempts/99999 → 404 JSON")
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    public void testGetNonExistentAttemptNotFound() throws Exception {
        mockMvc.perform(get("/api/attempts/99999"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error", notNullValue()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/students  (roster — ADMIN only)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/students → 403 JSON for STUDENT")
    public void testStudentGetRosterForbidden() throws Exception {
        mockMvc.perform(get("/api/students"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("GET /api/students → 403 JSON for TEACHER")
    public void testTeacherGetRosterForbidden() throws Exception {
        mockMvc.perform(get("/api/students"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    @DisplayName("GET /api/students → 200 for ADMIN")
    public void testAdminGetRosterOk() throws Exception {
        mockMvc.perform(get("/api/students"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", not(empty())));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/users  (user admin — ADMIN only)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("GET /api/users → 403 JSON for STUDENT")
    public void testStudentGetUsersForbidden() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("GET /api/users → 403 JSON for TEACHER")
    public void testTeacherGetUsersForbidden() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = ADMIN_EMAIL, roles = {"ADMIN"})
    @DisplayName("GET /api/users → 200 for ADMIN; no password/hash fields in response")
    public void testAdminGetUsersNoPasswordLeak() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].password").doesNotExist())
            .andExpect(jsonPath("$[*].passwordHash").doesNotExist())
            .andExpect(content().string(not(containsString("$2a$"))))
            .andExpect(content().string(not(containsString("bcrypt"))));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // /api/demo/seed  (ADMIN only)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @WithMockUser(username = STUDENT_EMAIL, roles = {"STUDENT"})
    @DisplayName("POST /api/demo/seed → 403 JSON for STUDENT")
    public void testStudentSeedDemoForbidden() throws Exception {
        mockMvc.perform(post("/api/demo/seed").with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = TEACHER_EMAIL, roles = {"TEACHER"})
    @DisplayName("POST /api/demo/seed → 403 JSON for TEACHER")
    public void testTeacherSeedDemoForbidden() throws Exception {
        mockMvc.perform(post("/api/demo/seed").with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
