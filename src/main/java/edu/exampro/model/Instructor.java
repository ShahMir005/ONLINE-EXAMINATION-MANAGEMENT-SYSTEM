package edu.exampro.model;

public final class Instructor extends User {
    private final String department;

    public Instructor(Long id, String name, String email, String department) {
        super(id, name, email);
        this.department = Validation.requireText(department, "Department");
    }

    public String getDepartment() {
        return department;
    }

    @Override
    public String getRole() {
        return "INSTRUCTOR";
    }

    @Override
    public String describe() {
        return super.describe() + " (" + department + ")";
    }
}
