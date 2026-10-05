package edu.exampro;

import edu.exampro.app.ExamProApplication;
import edu.exampro.model.Exam;
import edu.exampro.model.Student;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ExamProApplication.class)
@AutoConfigureMockMvc
@WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
public class ExamProControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ExamCatalog examCatalog;

    @Autowired
    private AttemptRepository attemptRepository;

    @Test
    @DisplayName("GET / returns 200 and loads home dashboard with counts")
    public void testHomePage() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("index"))
            .andExpect(model().attributeExists("studentCount"))
            .andExpect(model().attributeExists("examCount"))
            .andExpect(model().attributeExists("attemptCount"))
            .andExpect(model().attributeExists("exams"))
            .andExpect(content().string(containsString("ExamPro")));
    }

    @Test
    @DisplayName("GET /students returns 200 and student list view")
    public void testStudentsPage() throws Exception {
        mockMvc.perform(get("/students"))
            .andExpect(status().isOk())
            .andExpect(view().name("students"))
            .andExpect(model().attributeExists("students"))
            .andExpect(model().attributeExists("studentForm"));
    }

    @Test
    @DisplayName("POST /students registers new student and redirects")
    public void testAddStudent() throws Exception {
        String uniqueEmail = "mvc_test_" + System.currentTimeMillis() + "@exampro.edu";
        mockMvc.perform(post("/students")
                .with(csrf())
                .param("name", "Test Candidate")
                .param("email", uniqueEmail)
                .param("registrationNumber", "REG-" + System.currentTimeMillis()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/students"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("GET /exams returns 200 and list of exams")
    public void testExamsPage() throws Exception {
        mockMvc.perform(get("/exams"))
            .andExpect(status().isOk())
            .andExpect(view().name("exams"))
            .andExpect(model().attributeExists("exams"));
    }

    @Test
    @DisplayName("GET /exams/new returns 200 and exam creation form")
    public void testNewExamForm() throws Exception {
        mockMvc.perform(get("/exams/new"))
            .andExpect(status().isOk())
            .andExpect(view().name("exam-create"))
            .andExpect(model().attributeExists("examForm"));
    }

    @Test
    @DisplayName("POST /exams creates new exam with MCQ and TF questions")
    public void testCreateExam() throws Exception {
        mockMvc.perform(post("/exams")
                .with(csrf())
                .param("title", "Spring Boot Integration Test Exam " + System.currentTimeMillis())
                .param("durationMinutes", "45")
                // MCQ Question
                .param("questions[0].type", "MCQ")
                .param("questions[0].prompt", "Which Spring annotation designates a configuration class?")
                .param("questions[0].marks", "5")
                .param("questions[0].options[0]", "@Service")
                .param("questions[0].options[1]", "@Configuration")
                .param("questions[0].options[2]", "@Component")
                .param("questions[0].options[3]", "@Repository")
                .param("questions[0].correctOption", "1")
                // TF Question
                .param("questions[1].type", "TF")
                .param("questions[1].prompt", "Spring Boot requires explicit XML configuration files.")
                .param("questions[1].marks", "3")
                .param("questions[1].tfCorrect", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/exams"))
            .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("GET /results returns 200 and results leaderboard")
    public void testResultsPage() throws Exception {
        mockMvc.perform(get("/results"))
            .andExpect(status().isOk())
            .andExpect(view().name("results"))
            .andExpect(model().attributeExists("results"))
            .andExpect(model().attributeExists("totalAttempts"))
            .andExpect(model().attributeExists("avgPercentage"));
    }

    @Test
    @DisplayName("GET /take-exam redirects or displays take exam interface")
    public void testTakeExamFlow() throws Exception {
        Exam exam = examCatalog.findAll().stream().findFirst().orElse(null);
        if (exam != null) {
            mockMvc.perform(get("/exams/" + exam.getId() + "/take"))
                .andExpect(status().isOk())
                .andExpect(view().name("exam-take"))
                .andExpect(model().attributeExists("exam"))
                .andExpect(model().attributeExists("students"));
        }
    }
}
