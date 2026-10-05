package edu.exampro;

import edu.exampro.app.ExamProApplication;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Student;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import edu.exampro.model.AppUser;
import edu.exampro.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

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
    @DisplayName("1. Unauthenticated users are redirected to login")
    public void testUnauthenticatedRedirect() throws Exception {
        // Admin route redirects to /admin/login
        mockMvc.perform(get("/admin/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/admin/login")));

        // Teacher route redirects to /teacher/login
        mockMvc.perform(get("/teacher/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/teacher/login")));

        // Student route redirects to /student/login
        mockMvc.perform(get("/student/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/student/login")));

        // General protected route redirects to login
        mockMvc.perform(get("/students"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/student/login")));
    }

    @Test
    @DisplayName("2. Admin can access admin dashboard")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAdminAccessAdminDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin-dashboard"))
            .andExpect(content().string(containsString("Admin Dashboard")));
    }

    @Test
    @DisplayName("3. Teacher cannot access admin routes")
    @WithMockUser(username = "teacher@exampro.edu", roles = {"TEACHER"})
    public void testTeacherCannotAccessAdminRoutes() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/students"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Student cannot access admin or teacher routes")
    @WithMockUser(username = "ada@exampro.edu", roles = {"STUDENT"})
    public void testStudentCannotAccessAdminOrTeacherRoutes() throws Exception {
        // Student cannot access admin dashboard
        mockMvc.perform(get("/admin/dashboard"))
            .andExpect(status().isForbidden());

        // Student cannot access student management
        mockMvc.perform(get("/students"))
            .andExpect(status().isForbidden());

        // Student cannot access teacher dashboard
        mockMvc.perform(get("/teacher/dashboard"))
            .andExpect(status().isForbidden());

        // Student cannot access exam creation
        mockMvc.perform(get("/exams/new"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. A student cannot view another student's scorecard")
    public void testStudentCannotViewOtherStudentScorecard() throws Exception {
        // Find Ada and Alan from repository
        Student ada = studentRepository.findByEmail("ada@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Ada Lovelace", "ada@exampro.edu", "STU-101")));
        Student alan = studentRepository.findByEmail("alan@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Alan Turing", "alan@exampro.edu", "STU-102")));

        // Create a unique exam with at least one question specifically for this attempt test
        Exam exam = new Exam(null, "Scorecard Security Test " + System.currentTimeMillis(), Duration.ofMinutes(15));
        exam.addQuestion(new edu.exampro.model.TrueFalseQuestion(null, "Security question", 10, true));
        Exam savedExam = examCatalog.create(exam);

        // Create an attempt explicitly belonging to Ada
        Attempt adaAttempt = new Attempt(null, savedExam.getId(), ada.getId(), Map.of(), Instant.now(), 10);
        Attempt savedAttempt = attemptRepository.save(adaAttempt);

        // Alan (logged in as STUDENT) tries to view Ada's attempt -> Expect 403 Forbidden
        mockMvc.perform(get("/attempts/" + savedAttempt.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("alan@exampro.edu").roles("STUDENT")))
            .andExpect(status().isForbidden());

        // Ada (logged in as herself) views her attempt -> Expect 200 OK
        mockMvc.perform(get("/attempts/" + savedAttempt.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT")))
            .andExpect(status().isOk())
            .andExpect(view().name("scorecard"));
    }

    @Test
    @DisplayName("Portal Role Enforcement: Login rejected when role does not match login portal")
    public void testPortalRoleMismatchRejection() throws Exception {
        // Teacher tries to login via Admin portal -> rejected with role_mismatch error
        mockMvc.perform(post("/admin/login")
                .with(csrf())
                .param("username", "teacher@exampro.edu")
                .param("password", "TeacherPassword123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/admin/login?error=role_mismatch")));

        // Student tries to login via Admin portal -> rejected with role_mismatch error
        mockMvc.perform(post("/admin/login")
                .with(csrf())
                .param("username", "ada@exampro.edu")
                .param("password", "StudentPassword123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/admin/login?error=role_mismatch")));

        // Admin logs in through Admin portal -> successfully redirected to /admin/dashboard
        mockMvc.perform(post("/admin/login")
                .with(csrf())
                .param("username", "admin@exampro.edu")
                .param("password", "AdminPassword123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/admin/dashboard")));

        // Teacher logs in through Teacher portal -> successfully redirected to /teacher/dashboard
        mockMvc.perform(post("/teacher/login")
                .with(csrf())
                .param("username", "teacher@exampro.edu")
                .param("password", "TeacherPassword123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/teacher/dashboard")));

        // Student logs in through Student portal -> successfully redirected to /student/dashboard
        mockMvc.perform(post("/student/login")
                .with(csrf())
                .param("username", "ada@exampro.edu")
                .param("password", "StudentPassword123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/student/dashboard")));
    }

    @Test
    @DisplayName("Prevent student from submitting exam as another student by tampering with request params")
    public void testStudentCannotSubmitAsAnotherStudent() throws Exception {
        Student ada = studentRepository.findByEmail("ada@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Ada Lovelace", "ada@exampro.edu", "STU-101")));
        Student alan = studentRepository.findByEmail("alan@exampro.edu")
            .orElseGet(() -> studentRepository.save(new Student(null, "Alan Turing", "alan@exampro.edu", "STU-102")));

        Exam exam = examCatalog.findAll().stream().findFirst().orElseGet(() -> {
            Exam e = new Exam(null, "Tampering Test Exam", Duration.ofMinutes(15));
            return examCatalog.create(e);
        });

        // Ada is logged in, but studentId parameter in request is set to Alan's ID
        mockMvc.perform(post("/exams/" + exam.getId() + "/submit")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT"))
                .param("studentId", String.valueOf(alan.getId())))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin User Management: RBAC Access Control restricts /admin/users strictly to ADMIN")
    public void testAdminUserManagementAccessControl() throws Exception {
        // Unauthenticated -> redirected to /admin/login
        mockMvc.perform(get("/admin/users"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/admin/login")));

        // TEACHER -> 403 Forbidden
        mockMvc.perform(get("/admin/users")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("teacher@exampro.edu").roles("TEACHER")))
            .andExpect(status().isForbidden());

        // STUDENT -> 403 Forbidden
        mockMvc.perform(get("/admin/users")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("ada@exampro.edu").roles("STUDENT")))
            .andExpect(status().isForbidden());

        // ADMIN -> 200 OK
        mockMvc.perform(get("/admin/users")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin@exampro.edu").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(view().name("admin-users"))
            .andExpect(content().string(containsString("User Account Management")));
    }

    @Test
    @DisplayName("Admin User Management: Create user with BCrypt hashing and no plaintext password storage")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAdminCreateUserWithBcryptAndNoPlaintext() throws Exception {
        String uniqueEmail = "teacher_babbage_" + System.currentTimeMillis() + "@exampro.edu";
        String rawPassword = "BabbageSecret123!";

        mockMvc.perform(post("/admin/users")
                .with(csrf())
                .param("fullName", "Charles Babbage")
                .param("email", uniqueEmail)
                .param("role", "TEACHER")
                .param("password", rawPassword))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attributeExists("successMessage"));

        // Fetch user from DB and verify BCrypt hash properties
        AppUser savedUser = appUserRepository.findByEmail(uniqueEmail).orElse(null);
        assertNotNull(savedUser, "User must be found in database");
        assertEquals("Charles Babbage", savedUser.getFullName());
        assertEquals("TEACHER", savedUser.getRole());
        assertTrue(savedUser.isEnabled());

        // Critical security check: Raw plaintext password MUST NOT be stored
        assertNotEquals(rawPassword, savedUser.getPasswordHash(), "Plaintext password must never be stored");
        assertTrue(savedUser.getPasswordHash().startsWith("$2a$") || savedUser.getPasswordHash().startsWith("$2b$"),
            "Password hash must be a valid BCrypt hash format");
        assertTrue(passwordEncoder.matches(rawPassword, savedUser.getPasswordHash()),
            "BCryptPasswordEncoder must verify the raw password against the stored hash");
    }

    @Test
    @DisplayName("Admin User Management: Duplicate email validation rejects duplicate account creation")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAdminCreateUserDuplicateEmailValidation() throws Exception {
        // Attempt to create user with already registered admin email
        mockMvc.perform(post("/admin/users")
                .with(csrf())
                .param("fullName", "Imposter Admin")
                .param("email", "admin@exampro.edu")
                .param("role", "ADMIN")
                .param("password", "SomePass123!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("errorMessage", containsString("already exists")));
    }

    @Test
    @DisplayName("Admin User Management: Secure change-password form updates BCrypt hash and verifies match")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAdminChangeUserPasswordSecurely() throws Exception {
        String testEmail = "pwd_test_" + System.currentTimeMillis() + "@exampro.edu";
        String originalPassword = "InitialPassword123!";
        AppUser testUser = appUserRepository.save(new AppUser(
            null, "Password Test User", testEmail, passwordEncoder.encode(originalPassword), "STUDENT", true));

        String newPassword = "NewSecretPassword789!";

        // 1. Password mismatch rejection
        mockMvc.perform(post("/admin/users/" + testUser.getId() + "/change-password")
                .with(csrf())
                .param("newPassword", newPassword)
                .param("confirmPassword", "MismatchPassword456!"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attribute("errorMessage", containsString("do not match")));

        // 2. Successful password change with matching confirm password
        mockMvc.perform(post("/admin/users/" + testUser.getId() + "/change-password")
                .with(csrf())
                .param("newPassword", newPassword)
                .param("confirmPassword", newPassword))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attributeExists("successMessage"));

        // Verify updated hash in database
        AppUser updatedUser = appUserRepository.findById(testUser.getId()).orElseThrow();
        assertNotEquals(newPassword, updatedUser.getPasswordHash(), "Plaintext password must not be stored");
        assertFalse(passwordEncoder.matches(originalPassword, updatedUser.getPasswordHash()), "Old password must no longer match");
        assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash()), "New password must match BCrypt hash");
    }
}

