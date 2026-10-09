package edu.exampro.web.api;

import edu.exampro.model.AppUser;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.StudentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for user account administration — restricted to administrators.
 *
 * Enforces:
 *  - ADMIN role required for all operations.
 *  - Self-protection: An administrator cannot disable or delete their own account.
 *  - Passwords and security credentials are never returned in responses.
 *  - Plain student-friendly error messages without technical jargon.
 */
@RestController
@RequestMapping("/api/users")
public class UserManagementApiController {

    private final AppUserRepository appUserRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementApiController(AppUserRepository appUserRepository,
                                       StudentRepository studentRepository,
                                       PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority()) || "ADMIN".equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    @GetMapping
    public ResponseEntity<?> listUsers(Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        List<AppUser> users = appUserRepository.findAll();
        List<Map<String, Object>> response = new ArrayList<>();
        for (AppUser u : users) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("fullName", u.getFullName());
            map.put("email", u.getEmail());
            map.put("role", u.getRole());
            map.put("enabled", u.isEnabled());
            response.add(map);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest req, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        String name = req.fullName != null ? req.fullName.trim() : "";
        String email = req.email != null ? req.email.trim().toLowerCase(Locale.ROOT) : "";
        String role = req.role != null ? req.role.trim().toUpperCase(Locale.ROOT) : "STUDENT";
        String password = req.password != null ? req.password.trim() : "";

        if (name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name is required"));
        }
        if (email.isBlank() || !email.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid email address is required"));
        }
        if (!List.of("ADMIN", "TEACHER", "STUDENT").contains(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Role must be Administrator, Teacher, or Student"));
        }
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters long"));
        }

        if (appUserRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "An account with this email address already exists"));
        }

        if ("STUDENT".equals(role)) {
            if (studentRepository.findByEmail(email).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error",
                    "A student profile with this email already exists in the roster"));
            }
            String reg = (req.registrationNumber != null && !req.registrationNumber.isBlank())
                ? req.registrationNumber.trim()
                : "STU-" + (System.currentTimeMillis() % 100000);
            studentRepository.save(new Student(null, name, email, reg));
        }

        AppUser newUser = new AppUser(null, name, email, passwordEncoder.encode(password), role, true);
        AppUser saved = appUserRepository.save(newUser);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("fullName", saved.getFullName());
        resp.put("email", saved.getEmail());
        resp.put("role", saved.getRole());
        resp.put("enabled", saved.isEnabled());

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/{id}/change-password")
    public ResponseEntity<?> changePassword(@PathVariable("id") long id,
                                           @RequestBody ChangePasswordRequest req,
                                           Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        Optional<AppUser> userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        if (req.confirmPassword != null && !req.confirmPassword.equals(req.newPassword)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
        }

        if (req.newPassword == null || req.newPassword.trim().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "Password must be at least 6 characters long"));
        }

        AppUser user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(req.newPassword.trim()));
        appUserRepository.save(user);

        return ResponseEntity.ok(Map.of("success", true, "ok", true));
    }

    @PostMapping("/{id}/toggle-status")
    public ResponseEntity<?> toggleStatus(@PathVariable("id") long id, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        Optional<AppUser> userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        AppUser user = userOpt.get();
        if (auth != null && auth.getName().equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "You cannot disable your own active administrator account"));
        }

        user.setEnabled(!user.isEnabled());
        appUserRepository.save(user);

        return ResponseEntity.ok(Map.of("id", user.getId(), "enabled", user.isEnabled()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") long id, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        Optional<AppUser> userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
        }

        AppUser user = userOpt.get();
        if (auth != null && auth.getName().equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "You cannot delete your own active administrator account"));
        }

        if ("STUDENT".equalsIgnoreCase(user.getRole())) {
            studentRepository.findByEmail(user.getEmail())
                .ifPresent(s -> studentRepository.deleteById(s.getId()));
        }

        appUserRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    public static class CreateUserRequest {
        public String fullName;
        public String email;
        public String role;
        public String password;
        public String registrationNumber;
    }

    public static class ChangePasswordRequest {
        public String newPassword;
        public String confirmPassword;
    }
}
