package edu.exampro.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Unified Spring Security configuration for ExamPro Single Page Application.
 *
 * Enforces role-based access control, cookie-backed CSRF tokens for JavaScript,
 * JSON-based 401/403 responses for REST endpoints, and session management.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
            .authorizeHttpRequests(auth -> auth
                // Static assets permitted without authentication
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()

                // SPA login, error, and login processing are public
                .requestMatchers("/login", "/spa-login", "/error", "/api/auth/reset-password", "/api/auth/env").permitAll()

                // API identity
                .requestMatchers(HttpMethod.GET, "/api/me", "/api/auth/me", "/api/stats").authenticated()

                // Exam browsing & detail: authenticated users (answers hidden for students by controller)
                .requestMatchers(HttpMethod.GET, "/api/exams", "/api/exams/*").authenticated()

                // Answer keys: ADMIN and TEACHER only
                .requestMatchers(HttpMethod.GET, "/api/exams/*/key").hasAnyRole("ADMIN", "TEACHER")

                // Exam creation & deletion: ADMIN and TEACHER only
                .requestMatchers(HttpMethod.POST, "/api/exams").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.DELETE, "/api/exams/*").hasAnyRole("ADMIN", "TEACHER")

                // Exam start & submit: STUDENT and ADMIN
                .requestMatchers(HttpMethod.POST, "/api/exams/*/start", "/api/exams/*/submit").hasAnyRole("STUDENT", "ADMIN")

                // Results and attempts: authenticated (ownership enforced)
                .requestMatchers(HttpMethod.GET, "/api/results", "/api/results/*", "/api/attempts/*").authenticated()

                // Student roster: ADMIN only
                .requestMatchers("/api/students/**").hasRole("ADMIN")

                // User management: ADMIN only
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                // Demo reset: ADMIN only
                .requestMatchers(HttpMethod.POST, "/demo/seed", "/api/demo/seed").hasRole("ADMIN")

                // App shell routes require authentication
                .requestMatchers("/", "/app", "/app/**").authenticated()

                // Any other request requires authentication
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/spa-login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler((req, res, auth) -> res.sendRedirect(req.getContextPath() + "/app"))
                .failureHandler((req, res, ex) -> res.sendRedirect(req.getContextPath() + "/login?error=true"))
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) -> {
                    String uri = req.getRequestURI();
                    if (uri.startsWith(req.getContextPath() + "/api/") || uri.startsWith(req.getContextPath() + "/demo/")) {
                        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"error\":\"Authentication required\"}");
                    } else {
                        res.sendRedirect(req.getContextPath() + "/login");
                    }
                })
                .accessDeniedHandler((req, res, accessEx) -> {
                    String uri = req.getRequestURI();
                    if (uri.startsWith(req.getContextPath() + "/api/") || uri.startsWith(req.getContextPath() + "/demo/")) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"error\":\"Access denied\"}");
                    } else {
                        res.sendRedirect(req.getContextPath() + "/login?error=access_denied");
                    }
                })
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new OrRequestMatcher(
                    new AntPathRequestMatcher("/api/auth/logout"),
                    new AntPathRequestMatcher("/logout")
                ))
                .logoutSuccessHandler((req, res, auth) -> {
                    String uri = req.getRequestURI();
                    if (uri.contains("/api/")) {
                        res.setStatus(HttpServletResponse.SC_OK);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"ok\":true}");
                    } else {
                        res.sendRedirect(req.getContextPath() + "/login?logout=true");
                    }
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                .csrfTokenRequestHandler(requestHandler)
            )
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);

        return http.build();
    }
}
