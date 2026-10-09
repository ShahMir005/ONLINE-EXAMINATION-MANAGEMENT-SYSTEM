package edu.exampro.web.api;

import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.StudentRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication information API — used by the SPA to bootstrap session state.
 *
 * Endpoints:
 *   GET  /api/auth/me   → {username, role, studentId?, displayName?} or 401
 *
 * POST /api/auth/logout is handled entirely by Spring Security
 * (logoutUrl = "/api/auth/logout" in SpaSecurityConfig) and returns 200 JSON.
 *
 * Hard rules enforced here:
 *  - No password hash, no tech details, no internal IDs that a student
 *    should not see are sent beyond what the role permits.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final AppUserRepository appUserRepository;
    private final StudentRepository studentRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public AuthApiController(AppUserRepository appUserRepository,
                             StudentRepository studentRepository,
                             org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Returns the identity of the currently authenticated user.
     *
     * Response shape:
     * {
     *   "username":    "user@example.com",
     *   "role":        "STUDENT",          // ADMIN | TEACHER | STUDENT
     *   "displayName": "Jane Smith",
     *   "studentId":   42                  // only present when role == STUDENT
     * }
     *
     * Returns 401 if the request is not authenticated (handled by
     * SpaSecurityConfig's entry point before this method is called).
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        // Look up the full user record for display name and role.
        // AppUserRepository.findByEmail is the canonical lookup.
        var maybeUser = appUserRepository.findByEmail(username);
        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(401).build();
        }
        var appUser = maybeUser.get();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username",    appUser.getEmail());
        body.put("role",        appUser.getRole());
        body.put("displayName", appUser.getFullName());

        // For STUDENT accounts, also expose the linked student record ID
        // so the SPA can pre-select the right student when starting an exam.
        if ("STUDENT".equals(appUser.getRole())) {
            Optional<Student> linked = studentRepository.findByEmail(appUser.getEmail());
            linked.ifPresent(s -> body.put("studentId", s.getId()));
        }

        return ResponseEntity.ok(body);
    }

    /** Returns environment information for dev features. */
    @GetMapping("/env")
    public ResponseEntity<Map<String, Object>> env() {
        return ResponseEntity.ok(Map.of("isDev", edu.exampro.service.DevSeedService.isDevelopment()));
    }

    /**
     * Local development password reset endpoint.
     * Strictly rejected if not in development mode.
     */
    @org.springframework.web.bind.annotation.PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        if (!edu.exampro.service.DevSeedService.isDevelopment()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Password reset is only available in development mode"));
        }
        String email = body.get("email");
        String newPassword = body.get("newPassword");
        if (email == null || email.isBlank() || newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and newPassword are required"));
        }
        if (newPassword.trim().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters long"));
        }
        boolean ok = edu.exampro.service.DevSeedService.resetUserPassword(email, newPassword, appUserRepository, passwordEncoder);
        if (!ok) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                .body(Map.of("error", "User with email '" + email + "' not found"));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Password has been successfully reset."));
    }
}
