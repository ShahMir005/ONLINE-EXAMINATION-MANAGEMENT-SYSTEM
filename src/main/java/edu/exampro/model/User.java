package edu.exampro.model;

import edu.exampro.exception.ValidationException;
import java.util.Locale;

/** Abstract parent of every person in the system (inheritance + abstraction). */
public abstract class User implements Identifiable {
    private Long id;
    private final String name;
    private final String email;

    protected User(Long id, String name, String email) {
        this.id = id;
        this.name = Validation.requireText(name, "Name");
        this.email = Validation.requireText(email, "Email").toLowerCase(Locale.ROOT);
        if (!this.email.contains("@")) {
            throw new ValidationException("Email must contain '@'");
        }
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    /** Every subclass names its own role (polymorphism). */
    public abstract String getRole();

    /** Subclasses extend this text with their own details (method overriding). */
    public String describe() {
        return getRole() + ": " + name + " <" + email + ">";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof User user && email.equals(user.email);
    }

    @Override
    public int hashCode() {
        return email.hashCode();
    }

    @Override
    public String toString() {
        return describe();
    }
}
