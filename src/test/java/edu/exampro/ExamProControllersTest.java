package edu.exampro;

import edu.exampro.app.ExamProApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for SPA shell and entry routes served by SpaController.
 */
@SpringBootTest(classes = ExamProApplication.class)
@AutoConfigureMockMvc
public class ExamProControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /login returns 200 and serves login.html view")
    public void testLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"))
        .andExpect(content().string(containsString("Sign In — ExamPro")));
    }

    @Test
    @DisplayName("GET / redirects authenticated or anonymous users towards /app")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testRootRedirectsToApp() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/app"));
    }

    @Test
    @DisplayName("GET /app returns 200 and serves app.html shell for authenticated user")
    @WithMockUser(username = "admin@exampro.edu", roles = {"ADMIN"})
    public void testAppShellAuthenticated() throws Exception {
        mockMvc.perform(get("/app"))
            .andExpect(status().isOk())
            .andExpect(view().name("app"))
            .andExpect(content().string(containsString("main.js")));
    }

    @Test
    @DisplayName("GET /app redirects anonymous users to /login")
    public void testAppShellUnauthenticatedRedirects() throws Exception {
        mockMvc.perform(get("/app"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    @DisplayName("GET /error returns 200 and serves error.html view")
    public void testErrorPage() throws Exception {
        mockMvc.perform(get("/error"))
            .andExpect(status().isOk())
            .andExpect(view().name("error"))
            .andExpect(content().string(containsString("Something went wrong")));
    }
}
