package edu.exampro.model;

public final class Student extends User {
    private final String registrationNumber;

    public Student(Long id, String name, String email, String registrationNumber) {
        super(id, name, email);
        this.registrationNumber = Validation.requireText(registrationNumber, "Registration number");
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    @Override
    public String getRole() {
        return "STUDENT";
    }

    @Override
    public String describe() {
        return super.describe() + " [" + registrationNumber + "]";
    }
}
