package edu.exampro.repository;

import edu.exampro.db.Database;
import edu.exampro.exception.ExamException;
import edu.exampro.model.Student;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** JDBC CRUD for students. Every query uses PreparedStatement, so no input is concatenated into SQL. */
public final class StudentRepository implements CrudRepository<Student> {
    private static final String INSERT =
        "INSERT INTO students(name, email, registration_number) VALUES (?, ?, ?)";
    private static final String UPDATE =
        "UPDATE students SET name = ?, email = ?, registration_number = ? WHERE id = ?";
    private static final String SELECT_BY_ID =
        "SELECT id, name, email, registration_number FROM students WHERE id = ?";
    private static final String SELECT_BY_EMAIL =
        "SELECT id, name, email, registration_number FROM students WHERE email = ?";
    private static final String SELECT_ALL =
        "SELECT id, name, email, registration_number FROM students ORDER BY id";
    private static final String DELETE = "DELETE FROM students WHERE id = ?";

    @Override
    public Student save(Student student) {
        return student.getId() == null ? insert(student) : update(student);
    }

    private Student insert(Student student) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, student.getName());
            statement.setString(2, student.getEmail());
            statement.setString(3, student.getRegistrationNumber());
            statement.executeUpdate();
            student.setId(Database.generatedKey(statement));
            return student;
        } catch (SQLException exception) {
            throw new ExamException("Could not create student", exception);
        }
    }

    private Student update(Student student) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setString(1, student.getName());
            statement.setString(2, student.getEmail());
            statement.setString(3, student.getRegistrationNumber());
            statement.setLong(4, student.getId());
            if (statement.executeUpdate() == 0) {
                throw new ExamException("Cannot update: no student with id " + student.getId());
            }
            return student;
        } catch (SQLException exception) {
            throw new ExamException("Could not update student", exception);
        }
    }

    @Override
    public Optional<Student> findById(long id) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(toStudent(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ExamException("Could not read student", exception);
        }
    }

    public Optional<Student> findByEmail(String email) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_EMAIL)) {
            statement.setString(1, email.trim().toLowerCase(Locale.ROOT));
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(toStudent(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ExamException("Could not read student by email", exception);
        }
    }

    @Override
    public List<Student> findAll() {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet row = statement.executeQuery()) {
            List<Student> students = new ArrayList<>();
            while (row.next()) {
                students.add(toStudent(row));
            }
            return students;
        } catch (SQLException exception) {
            throw new ExamException("Could not list students", exception);
        }
    }

    @Override
    public boolean deleteById(long id) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new ExamException("Could not delete student", exception);
        }
    }

    private static Student toStudent(ResultSet row) throws SQLException {
        return new Student(
            row.getLong("id"),
            row.getString("name"),
            row.getString("email"),
            row.getString("registration_number"));
    }
}
