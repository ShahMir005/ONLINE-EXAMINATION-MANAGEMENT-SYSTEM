package edu.exampro;

import edu.exampro.exception.DuplicateSubmissionException;
import edu.exampro.exception.ExamException;
import edu.exampro.exception.ValidationException;
import edu.exampro.web.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExceptionThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @Test
    void duplicateSubmissionReturnsConflictJson() throws Exception {
        mockMvc.perform(get("/test/duplicate"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error")
                .value("Student 7 has already submitted exam 3"));
    }

    @Test
    void validationFailureReturnsBadRequestJson() throws Exception {
        mockMvc.perform(get("/test/validation"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("Answers are required"));
    }

    @Test
    void examFailureReturnsBadRequestJson() throws Exception {
        mockMvc.perform(get("/test/exam"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("Exam session has expired"));
    }

    @RestController
    static class ExceptionThrowingController {
        @GetMapping("/test/duplicate")
        void duplicateSubmission() {
            throw new DuplicateSubmissionException(3, 7);
        }

        @GetMapping("/test/validation")
        void validationFailure() {
            throw new ValidationException("Answers are required");
        }

        @GetMapping("/test/exam")
        void examFailure() {
            throw new ExamException("Exam session has expired");
        }
    }
}
