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
import java.time.Duration;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ExamProApplication.class)
@AutoConfigureMockMvc
public class SpaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExamCatalog examCatalog;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Exam testExam;
    private Student testStudent;

    @BeforeEach
    public void setUp() {
        // Ensure test student exists
        testStudent = studentRepository.findByEmail("spastudent@exampro.edu").orElseGet(() ->
            studentRepository.save(new Student(null, "SPA Student", "spastudent@exampro.edu", "STU-SPA-1"))
        );

        // Ensure AppUser for student exists
        if (appUserRepository.findByEmail("spastudent@exampro.edu").isEmpty()) {
            appUserRepository.save(new AppUser(
                null,
                "SPA Student",
                "spastudent@exampro.edu",
                passwordEncoder.encode("StudentPassword123!"),
                "STUDENT",
                true
            ));
        }

        // Ensure AppUser for admin exists
        if (appUserRepository.findByEmail("admin@exampro.edu").isEmpty()) {
            appUserRepository.save(new AppUser(
                null,
                "Admin User",
                "admin@exampro.edu",
                passwordEncoder.encode("AdminPassword123!"),
                "ADMIN",
                true
            ));
        }

        // Create a unique test exam
        Exam exam = new Exam(null, "SPA Test Exam " + System.currentTimeMillis(), Duration.ofMinutes(20));
        exam.addQuestion(new MultipleChoiceQuestion(null, "Which language is this?", 5,
            List.of("Python", "Java", "C++"), 1));
        exam.addQuestion(new TrueFalseQuestion(null, "Java is statically typed.", 2, true));
        testExam = examCatalog.create(exam);
    }

    @Test
    @DisplayName("SPA shell: /login and /app routing")
    public void testSpaShellAccess() throws Exception {
        // Public login page returns 200 HTML
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("Sign in as")));

        // Unauthenticated access to /app redirects to /login
        mockMvc.perform(get("/app"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("GET /api/auth/me returns authenticated user identity with studentId")
    public void testAuthMeForStudent() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username", is("spastudent@exampro.edu")))
            .andExpect(jsonPath("$.role", is("STUDENT")))
            .andExpect(jsonPath("$.displayName", is("SPA Student")))
            .andExpect(jsonPath("$.studentId", is(testStudent.getId().intValue())));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 for unauthenticated requests")
    public void testAuthMeUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("HARD RULE 2: Correct answers are NEVER returned to a student")
    public void testStudentCannotSeeAnswers() throws Exception {
        mockMvc.perform(get("/api/exams/" + testExam.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(testExam.getId().intValue())))
            .andExpect(jsonPath("$.questions", hasSize(2)))
            .andExpect(jsonPath("$.questions[0].choices", hasSize(3)))
            // Verify correctOptionIndex is completely absent for student
            .andExpect(jsonPath("$.questions[0].correctOptionIndex").doesNotExist())
            .andExpect(jsonPath("$.questions[1].correctOptionIndex").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    @DisplayName("Admins and teachers CAN inspect correct answers")
    public void testAdminCanSeeAnswers() throws Exception {
        mockMvc.perform(get("/api/exams/" + testExam.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questions[0].correctOptionIndex", is(1)))
            .andExpect(jsonPath("$.questions[1].correctOptionIndex", is(0)));
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("Role enforcement: Student cannot create an exam (403)")
    public void testStudentCannotCreateExam() throws Exception {
        String examJson = """
            {
              "title": "Unauthorized Exam",
              "durationMinutes": 30,
              "questions": [
                {
                  "prompt": "Test?",
                  "type": "TF",
                  "marks": 2,
                  "correctOptionIndex": 0
                }
              ]
            }
            """;

        mockMvc.perform(post("/api/exams")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(examJson))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("Ownership enforcement: Student cannot submit exam for a different student (403)")
    public void testStudentTamperingStudentIdForbidden() throws Exception {
        long differentStudentId = testStudent.getId() + 999;
        String submitJson = """
            {
              "studentId": %d,
              "answers": {}
            }
            """.formatted(differentStudentId);

        mockMvc.perform(post("/api/exams/" + testExam.getId() + "/submit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitJson))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("Legitimate exam submission calculates score and prevents duplicates (409)")
    public void testSubmitExamAndDuplicatePrevention() throws Exception {
        long q1Id = testExam.getQuestions().get(0).getId();
        long q2Id = testExam.getQuestions().get(1).getId();

        // Option 1 is correct for MCQ (5 marks), Option 0 (true) is correct for TF (2 marks)
        String submitJson = """
            {
              "studentId": %d,
              "answers": {
                "%d": 1,
                "%d": 0
              }
            }
            """.formatted(testStudent.getId(), q1Id, q2Id);

        // First submission succeeds
        mockMvc.perform(post("/api/exams/" + testExam.getId() + "/submit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.score", is(7)))
            .andExpect(jsonPath("$.totalMarks", is(7)))
            .andExpect(jsonPath("$.passed", is(true)));

        // Duplicate submission for same exam + student must return 409 CONFLICT
        mockMvc.perform(post("/api/exams/" + testExam.getId() + "/submit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitJson))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error", containsString("already submitted")));
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("Role enforcement: Student cannot access student directory (403)")
    public void testStudentCannotAccessStudentsDirectory() throws Exception {
        mockMvc.perform(get("/api/students"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("Role enforcement: Student cannot access user administration (403)")
    public void testStudentCannotAccessUsersDirectory() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    @DisplayName("Admin can view user accounts without password leakage")
    public void testAdminUserDirectoryNoPasswordLeak() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", not(empty())))
            // Verify password or passwordHash are NEVER in the JSON
            .andExpect(jsonPath("$[*].password").doesNotExist())
            .andExpect(jsonPath("$[*].passwordHash").doesNotExist())
            .andExpect(content().string(not(containsString("bcrypt"))))
            .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    @DisplayName("New students receive a random one-time password shown once to admin, not a fixed one")
    public void testAdminCreatesStudentWithRandomOneTimePassword() throws Exception {
        String uniqueEmail = "otp_candidate_" + System.currentTimeMillis() + "@exampro.edu";
        String uniqueReg = "REG-OTP-" + System.currentTimeMillis();
        String payload = """
            {
              "name": "One Time Candidate",
              "email": "%s",
              "registrationNumber": "%s"
            }
            """.formatted(uniqueEmail, uniqueReg);

        mockMvc.perform(post("/api/students")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email", is(uniqueEmail)))
            .andExpect(jsonPath("$.oneTimePassword", notNullValue()))
            .andExpect(jsonPath("$.oneTimePassword", not("StudentPassword123!")))
            .andExpect(result -> {
                String body = result.getResponse().getContentAsString();
                assertTrue(body.contains("\"oneTimePassword\""));
            });
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("Item 3: Student scorecard hides correctOption and correctText")
    public void testStudentScorecardHidesAnswerKeys() throws Exception {
        long q1Id = testExam.getQuestions().get(0).getId();
        long q2Id = testExam.getQuestions().get(1).getId();

        String submitJson = """
            {
              "studentId": %d,
              "answers": {
                "%d": 1,
                "%d": 0
              }
            }
            """.formatted(testStudent.getId(), q1Id, q2Id);

        String responseContent = mockMvc.perform(post("/api/exams/" + testExam.getId() + "/submit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(submitJson))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Extract attempt ID from response
        long attemptId = Long.parseLong(responseContent.replaceAll(".*\"attemptId\":([0-9]+).*", "$1"));

        // Student accesses own scorecard
        mockMvc.perform(get("/api/results/" + attemptId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questionResults[0].correctOption").doesNotExist())
            .andExpect(jsonPath("$.questionResults[0].correctText").doesNotExist())
            .andExpect(jsonPath("$.questionResults[1].correctOption").doesNotExist())
            .andExpect(jsonPath("$.questionResults[1].correctText").doesNotExist());
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("Item 3: Student cannot view another student's scorecard (403)")
    public void testStudentCannotViewOtherStudentScorecard() throws Exception {
        // Create an attempt for another student
        Student otherStudent = studentRepository.save(
            new Student(null, "Other Student", "other_" + System.currentTimeMillis() + "@exampro.edu", "REG-OTH-" + System.currentTimeMillis())
        );
        Attempt otherAttempt = attemptRepository.save(
            new Attempt(null, testExam.getId(), otherStudent.getId(), Map.of(), java.time.Instant.now(), 0)
        );

        mockMvc.perform(get("/api/results/" + otherAttempt.getId()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "spastudent@exampro.edu", roles = {"STUDENT"})
    @DisplayName("Item 3: Non-admin cannot seed demo data (403)")
    public void testStudentCannotSeedDemoData() throws Exception {
        mockMvc.perform(post("/api/demo/seed").with(csrf()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/demo/seed").with(csrf()))
            .andExpect(status().isForbidden());
    }
}
