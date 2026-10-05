package edu.exampro.model;

import edu.exampro.exception.ValidationException;
import java.util.Locale;

/** User account for authentication and role-based access control. */
public class AppUser implements Identifiable {
    private Long id;
    private String fullName;
    private String email;
    private String passwordHash;
    private String role; // ADMIN, TEACHER, STUDENT
    private boolean enabled;

    public AppUser() { }

    public AppUser(Long id, String fullName, String email, String passwordHash, String role, boolean enabled) {
        this.id = id;
        this.fullName = Validation.requireText(fullName, "Full name");
        this.email = Validation.requireText(email, "Email").toLowerCase(Locale.ROOT);
        if (!this.email.contains("@")) {
            throw new ValidationException("Email must contain '@'");
        }
        this.passwordHash = Validation.requireText(passwordHash, "Password hash");
        this.role = Validation.requireText(role, "Role").toUpperCase(Locale.ROOT);
        this.enabled = enabled;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
