package edu.exampro.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Chain 1: Dedicated Administrator Portal (/admin/**)
     */
    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/admin/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/login").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(new RoleCheckingAuthenticationSuccessHandler(
                    "ROLE_ADMIN", "/admin/dashboard", "/admin/login?error=role_mismatch"))
                .failureUrl("/admin/login?error=true")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/admin/login"))
            );

        return http.build();
    }

    /**
     * Chain 2: Dedicated Teacher Portal (/teacher/**)
     */
    @Bean
    @Order(2)
    public SecurityFilterChain teacherFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/teacher/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/teacher/login").permitAll()
                .requestMatchers("/teacher/**").hasRole("TEACHER")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/teacher/login")
                .loginProcessingUrl("/teacher/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(new RoleCheckingAuthenticationSuccessHandler(
                    "ROLE_TEACHER", "/teacher/dashboard", "/teacher/login?error=role_mismatch"))
                .failureUrl("/teacher/login?error=true")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/teacher/login"))
            );

        return http.build();
    }

    /**
     * Chain 3: Dedicated Student Portal (/student/**)
     */
    @Bean
    @Order(3)
    public SecurityFilterChain studentFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/student/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/student/login").permitAll()
                .requestMatchers("/student/**").hasRole("STUDENT")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/student/login")
                .loginProcessingUrl("/student/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(new RoleCheckingAuthenticationSuccessHandler(
                    "ROLE_STUDENT", "/student/dashboard", "/student/login?error=role_mismatch"))
                .failureUrl("/student/login?error=true")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/student/login"))
            );

        return http.build();
    }

    /**
     * Chain 4: Main Application Chain for shared resources, exams, students, and results
     */
    @Bean
    @Order(4)
    public SecurityFilterChain mainFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Static assets permitted without authentication
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                // Dedicated login endpoints and access denied page
                .requestMatchers("/admin/login", "/teacher/login", "/student/login", "/access-denied").permitAll()
                // Students management: ADMIN only (Teacher cannot delete students or manage users)
                .requestMatchers("/students/**").hasRole("ADMIN")
                // Exam creation & deletion: ADMIN and TEACHER
                .requestMatchers("/exams/new", "/exams/*/delete").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.POST, "/exams").hasAnyRole("ADMIN", "TEACHER")
                // Exam taking: STUDENT and ADMIN
                .requestMatchers("/exams/*/take", "/exams/*/submit", "/take-exam").hasAnyRole("STUDENT", "ADMIN")
                // Browsing exams: any authenticated user (ADMIN, TEACHER, STUDENT)
                .requestMatchers("/exams", "/exams/*").authenticated()
                // Results and scorecards: authenticated (ownership enforced in controller)
                .requestMatchers("/results", "/attempts/**").authenticated()
                // Root and other routes require authentication
                .requestMatchers("/").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
                .authenticationEntryPoint(customAuthenticationEntryPoint())
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/student/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }

    private AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            String uri = request.getRequestURI();
            if (uri.startsWith("/admin")) {
                response.sendRedirect(request.getContextPath() + "/admin/login");
            } else if (uri.startsWith("/teacher")) {
                response.sendRedirect(request.getContextPath() + "/teacher/login");
            } else {
                response.sendRedirect(request.getContextPath() + "/student/login");
            }
        };
    }
}
