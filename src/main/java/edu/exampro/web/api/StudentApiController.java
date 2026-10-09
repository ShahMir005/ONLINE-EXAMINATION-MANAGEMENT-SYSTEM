package edu.exampro.web.api;

import edu.exampro.model.AppUser;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.StudentRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * REST API for student management — restricted to administrators.
 *
 * Endpoints:
 *   GET    /api/students       — List all registered students (ADMIN only)
 *   POST   /api/students       — Register a new student and create login account (ADMIN only)
 *   DELETE /api/students/{id}  — Remove student and associated account (ADMIN only)
 */
@RestController
@RequestMapping("/api/students")
public class StudentApiController {

    private final StudentRepository studentRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentApiController(StudentRepository studentRepository,
                                AppUserRepository appUserRepository,
                                PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.appUserRepository = appUserRepository;
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
    public ResponseEntity<?> listStudents(Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        List<Student> students = studentRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", s.getId());
            map.put("name", s.getName());
            map.put("email", s.getEmail());
            map.put("registrationNumber", s.getRegistrationNumber());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<?> createStudent(@RequestBody StudentRequest req, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        if (req.name == null || req.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name is required"));
        }
        if (req.email == null || req.email.isBlank() || !req.email.contains("@")) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid email address is required"));
        }
        if (req.registrationNumber == null || req.registrationNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Registration number is required"));
        }

        String email = req.email.trim();
        if (studentRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "A student with email '" + email + "' is already registered"));
        }

        Student newStudent = new Student(null, req.name.trim(), email, req.registrationNumber.trim());
        Student saved = studentRepository.save(newStudent);

        // Automatically create login account for the student if not already present with a random one-time password
        String oneTimePassword = edu.exampro.security.PasswordGenerator.generateOneTimePassword();
        if (appUserRepository.findByEmail(email).isEmpty()) {
            appUserRepository.save(new AppUser(
                null,
                saved.getName(),
                saved.getEmail(),
                passwordEncoder.encode(oneTimePassword),
                "STUDENT",
                true
            ));
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("name", saved.getName());
        resp.put("email", saved.getEmail());
        resp.put("registrationNumber", saved.getRegistrationNumber());
        resp.put("oneTimePassword", oneTimePassword);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStudent(@PathVariable("id") long id, Authentication auth) {
        if (!isAdmin(auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied: Administrator permissions required"));
        }

        Optional<Student> maybeStudent = studentRepository.findById(id);
        if (maybeStudent.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Student not found"));
        }

        Student student = maybeStudent.get();
        // Remove associated user authentication account
        appUserRepository.findByEmail(student.getEmail())
            .ifPresent(u -> appUserRepository.deleteById(u.getId()));

        studentRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    public static class StudentRequest {
        public String name;
        public String email;
        public String registrationNumber;
        public String password;
    }
}
