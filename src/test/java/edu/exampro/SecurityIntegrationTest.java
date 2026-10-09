package edu.exampro;

import edu.exampro.app.ExamProApplication;
import edu.exampro.model.AppUser;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ExamProApplication.class)
@AutoConfigureMockMvc
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ExamCatalog examCatalog;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("1. Unauthenticated users are redirected to /login for app shell and receive 401 for /api")
    public void testUnauthenticatedAccess() throws Exception {
        // Protected app route redirects to /login
        mockMvc.perform(get("/app"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/login")));

        // Protected API route returns 401 JSON
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error", containsString("Authentication required")));
    }

    @Test
    @DisplayName("2. Admin can access admin endpoints and views")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAdminAccessEndpoints() throws Exception {
        mockMvc.perform(get("/app"))
            .andExpect(status().isOk())
            .andExpect(view().name("app"));

        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("3. Teacher cannot access admin-only endpoints")
    @WithMockUser(username = "teacher@exampro.edu", roles = {"TEACHER"})
    public void testTeacherCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/students"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("4. Student cannot access admin or teacher endpoints")
    @WithMockUser(username = "ada@exampro.edu", roles = {"STUDENT"})
    public void testStudentCannotAccessAdminOrTeacherEndpoints() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/students"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/exams")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Hacked Exam\",\"durationMinutes\":10,\"questions\":[]}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. A student cannot view another student's attempt or scorecard")
    public void testStudentCannotViewOtherStudentScorecard() throws Exception {
        Student ada = studentRepository.findByEmail("ada@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Ada Lovelace", "ada@exampro.edu", "STU-101")));
        Student alan = studentRepository.findByEmail("alan@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Alan Turing", "alan@exampro.edu", "STU-102")));

        Exam exam = new Exam(null, "Scorecard Security Test " + System.currentTimeMillis(), Duration.ofMinutes(15));
        exam.addQuestion(new edu.exampro.model.TrueFalseQuestion(null, "Security question", 10, true));
        Exam savedExam = examCatalog.create(exam);

        Attempt adaAttempt = new Attempt(null, savedExam.getId(), ada.getId(), Map.of(), Instant.now(), 10);
        Attempt savedAttempt = attemptRepository.save(adaAttempt);

        // Alan (STUDENT) tries to view Ada's attempt -> Expect 403 Forbidden
        mockMvc.perform(get("/api/attempts/" + savedAttempt.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("alan@exampro.edu").roles("STUDENT")))
            .andExpect(status().isForbidden());

        // Ada (STUDENT) views her own attempt -> Expect 200 OK
        mockMvc.perform(get("/api/attempts/" + savedAttempt.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT")))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("6. Form login authenticates valid credentials and redirects to /app")
    public void testFormLoginSuccess() throws Exception {
        String testUserEmail = "login_test_" + System.currentTimeMillis() + "@exampro.edu";
        appUserRepository.save(new AppUser(
            null, "Login User", testUserEmail, passwordEncoder.encode("SecretPass123!"), "STUDENT", true));

        mockMvc.perform(post("/spa-login")
                .with(csrf())
                .param("username", testUserEmail)
                .param("password", "SecretPass123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/app")));
    }

    @Test
    @DisplayName("7. Prevent student from submitting exam as another student by tampering with request params")
    public void testStudentCannotSubmitAsAnotherStudent() throws Exception {
        Student ada = studentRepository.findByEmail("ada@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Ada Lovelace", "ada@exampro.edu", "STU-101")));
        Student alan = studentRepository.findByEmail("alan@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Alan Turing", "alan@exampro.edu", "STU-102")));

        Exam exam = examCatalog.findAll().stream().findFirst().orElseGet(() -> {
            Exam e = new Exam(null, "Tampering Test Exam", Duration.ofMinutes(15));
            return examCatalog.create(e);
        });

        // Ada is logged in, but studentId in JSON payload is Alan's ID
        String payload = "{\"studentId\":" + alan.getId() + ",\"answers\":{}}";

        mockMvc.perform(post("/api/exams/" + exam.getId() + "/submit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("8. Admin User Management: RBAC Access Control restricts /api/users strictly to ADMIN")
    public void testAdminUserManagementAccessControl() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("teacher@exampro.edu").roles("TEACHER")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT")))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@exampro.edu").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", not(empty())));
    }

    @Test
    @DisplayName("9. Admin User Management: Create user with BCrypt hashing and no plaintext password storage")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAdminCreateUserWithBcryptAndNoPlaintext() throws Exception {
        String uniqueEmail = "teacher_babbage_" + System.currentTimeMillis() + "@exampro.edu";
        String rawPassword = "BabbageSecret123!";

        String payload = "{\"fullName\":\"Charles Babbage\",\"email\":\"" + uniqueEmail + "\",\"role\":\"TEACHER\",\"password\":\"" + rawPassword + "\"}";

        mockMvc.perform(post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email", is(uniqueEmail)));

        AppUser savedUser = appUserRepository.findByEmail(uniqueEmail).orElse(null);
        assertNotNull(savedUser, "User must be found in database");
        assertEquals("Charles Babbage", savedUser.getFullName());
        assertEquals("TEACHER", savedUser.getRole());
        assertTrue(savedUser.isEnabled());

        assertNotEquals(rawPassword, savedUser.getPasswordHash(), "Plaintext password must never be stored");
        assertTrue(savedUser.getPasswordHash().startsWith("$2a$") || savedUser.getPasswordHash().startsWith("$2b$"),
            "Password hash must be a valid BCrypt hash format");
        assertTrue(passwordEncoder.matches(rawPassword, savedUser.getPasswordHash()),
            "BCryptPasswordEncoder must verify the raw password against the stored hash");
    }

    @Test
    @DisplayName("10. Admin User Management: Duplicate email validation rejects duplicate account creation")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAdminCreateUserDuplicateEmailValidation() throws Exception {
        String payload = "{\"fullName\":\"Imposter Admin\",\"email\":\"admin@exampro.edu\",\"role\":\"ADMIN\",\"password\":\"SomePass123!\"}";

        mockMvc.perform(post("/api/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("already exists")));
    }

    @Test
    @DisplayName("11. Admin User Management: Secure change-password updates BCrypt hash and verifies match")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAdminChangeUserPasswordSecurely() throws Exception {
        String testEmail = "pwd_test_" + System.currentTimeMillis() + "@exampro.edu";
        String originalPassword = "InitialPassword123!";
        AppUser testUser = appUserRepository.save(new AppUser(
            null, "Password Test User", testEmail, passwordEncoder.encode(originalPassword), "STUDENT", true));

        String newPassword = "NewSecretPassword789!";

        // 1. Password mismatch rejection
        String mismatchPayload = "{\"newPassword\":\"" + newPassword + "\",\"confirmPassword\":\"MismatchPassword456!\"}";
        mockMvc.perform(post("/api/users/" + testUser.getId() + "/change-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mismatchPayload))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("do not match")));

        // 2. Successful password change
        String matchingPayload = "{\"newPassword\":\"" + newPassword + "\",\"confirmPassword\":\"" + newPassword + "\"}";
        mockMvc.perform(post("/api/users/" + testUser.getId() + "/change-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(matchingPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok", is(true)));

        AppUser updatedUser = appUserRepository.findById(testUser.getId()).orElseThrow();
        assertNotEquals(newPassword, updatedUser.getPasswordHash());
        assertFalse(passwordEncoder.matches(originalPassword, updatedUser.getPasswordHash()));
        assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash()));
    }

    @Test
    @DisplayName("12. HARD RULE: Automated verification that NO correct answers leak in any student responses")
    public void testNeverSendCorrectAnswersToStudentAcrossAllResponses() throws Exception {
        Exam testExam = new Exam(null, "Answer Leak Prevention Exam " + System.currentTimeMillis(), Duration.ofMinutes(25));
        testExam.addQuestion(new edu.exampro.model.MultipleChoiceQuestion(
            null, "What is encapsulation in OOP?", 4,
            java.util.List.of("Data hiding", "Inheritance", "Polymorphism", "Compilation"), 0));
        testExam.addQuestion(new edu.exampro.model.TrueFalseQuestion(
            null, "Interfaces can contain default methods in Java 8+.", 2, true));
        Exam savedExam = examCatalog.create(testExam);

        Student student = studentRepository.findByEmail("ada@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Ada Lovelace", "ada@exampro.edu", "STU-101")));

        // 1. GET /api/exams
        String examsJson = mockMvc.perform(get("/api/exams")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(examsJson.contains("correctOptionIndex"), "Exams list must never include correctOptionIndex");
        assertFalse(examsJson.contains("correctOption"), "Exams list must never include correctOption");
        assertFalse(examsJson.contains("tfCorrect"), "Exams list must never include tfCorrect");

        // 2. GET /api/exams/{id} for STUDENT
        String examDetailJson = mockMvc.perform(get("/api/exams/" + savedExam.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(examDetailJson.contains("correctOptionIndex"), "Student exam detail must never reveal correctOptionIndex");
        assertFalse(examDetailJson.contains("correctOption"), "Student exam detail must never reveal correctOption");
        assertFalse(examDetailJson.contains("tfCorrect"), "Student exam detail must never reveal tfCorrect");
        assertFalse(examDetailJson.contains("correctText"), "Student exam detail must never reveal correctText");

        // 3. POST /api/exams/{id}/start for STUDENT
        String startJson = mockMvc.perform(post("/api/exams/" + savedExam.getId() + "/start")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(startJson.contains("correctOptionIndex"), "Start exam session must never reveal correctOptionIndex");
        assertFalse(startJson.contains("correctOption"), "Start exam session must never reveal correctOption");
        assertFalse(startJson.contains("tfCorrect"), "Start exam session must never reveal tfCorrect");
        assertFalse(startJson.contains("correctText"), "Start exam session must never reveal correctText");

        // 4. Verify that ADMIN can see answer keys (confirms answer keys exist, but are strictly hidden from students)
        String adminDetailJson = mockMvc.perform(get("/api/exams/" + savedExam.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@exampro.edu").roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(adminDetailJson.contains("correctOptionIndex"), "Admin exam detail must include correctOptionIndex");
    }
}
