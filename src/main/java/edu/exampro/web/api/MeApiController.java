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
 * GET /api/me — canonical session-bootstrap endpoint for the SPA.
 *
 * Returns the identity of the currently authenticated user so the frontend can
 * decide which view to render without a second round-trip.
 *
 * <pre>
 * {
 *   "username":    "alice@example.com",
 *   "role":        "STUDENT",          // ADMIN | TEACHER | STUDENT
 *   "displayName": "Alice Smith",
 *   "studentId":   42                  // only when role == STUDENT
 * }
 * </pre>
 *
 * Unauthenticated requests return {@code 401} JSON (enforced by {@link edu.exampro.security.SpaSecurityConfig}
 * before this method is ever reached).
 *
 * Hard rules: passwords, hashes, and internal tech details are never included.
 */
@RestController
@RequestMapping("/api/me")
public class MeApiController {

    private final AppUserRepository appUserRepository;
    private final StudentRepository studentRepository;

    public MeApiController(AppUserRepository appUserRepository,
                           StudentRepository studentRepository) {
        this.appUserRepository = appUserRepository;
        this.studentRepository = studentRepository;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required", "status", 401));
        }

        var maybeUser = appUserRepository.findByEmail(authentication.getName());
        if (maybeUser.isEmpty()) {
            return ResponseEntity.status(401)
                .body(Map.of("error", "Authentication required", "status", 401));
        }
        var appUser = maybeUser.get();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username",    appUser.getEmail());
        body.put("role",        appUser.getRole());
        body.put("displayName", appUser.getFullName());

        if ("STUDENT".equals(appUser.getRole())) {
            Optional<Student> linked = studentRepository.findByEmail(appUser.getEmail());
            linked.ifPresent(s -> body.put("studentId", s.getId()));
        }

        return ResponseEntity.ok(body);
    }
}
