package edu.exampro.repository;

import edu.exampro.db.Database;
import edu.exampro.exception.ExamException;
import edu.exampro.model.ExamSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC CRUD for timed examination sessions. */
public final class ExamSessionRepository implements CrudRepository<ExamSession> {
    private static final String INSERT =
        "INSERT INTO exam_sessions(exam_id, student_id, start_time) VALUES (?, ?, ?)";
    private static final String UPDATE =
        "UPDATE exam_sessions SET exam_id = ?, student_id = ?, start_time = ? WHERE id = ?";
    private static final String SELECT_BY_ID =
        "SELECT id, exam_id, student_id, start_time FROM exam_sessions WHERE id = ?";
    private static final String SELECT_BY_EXAM_AND_STUDENT =
        "SELECT id, exam_id, student_id, start_time FROM exam_sessions WHERE exam_id = ? AND student_id = ?";
    private static final String SELECT_ALL =
        "SELECT id, exam_id, student_id, start_time FROM exam_sessions ORDER BY id";
    private static final String DELETE = "DELETE FROM exam_sessions WHERE id = ?";
    private static final String DELETE_BY_EXAM_AND_STUDENT =
        "DELETE FROM exam_sessions WHERE exam_id = ? AND student_id = ?";

    @Override
    public ExamSession save(ExamSession session) {
        return session.getId() == null ? insert(session) : update(session);
    }

    private ExamSession insert(ExamSession session) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, session.getExamId());
            statement.setLong(2, session.getStudentId());
            statement.setObject(3, OffsetDateTime.ofInstant(session.getStartTime(), ZoneOffset.UTC));
            statement.executeUpdate();
            session.setId(Database.generatedKey(statement));
            return session;
        } catch (SQLException exception) {
            throw new ExamException("Could not create exam session", exception);
        }
    }

    private ExamSession update(ExamSession session) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {
            statement.setLong(1, session.getExamId());
            statement.setLong(2, session.getStudentId());
            statement.setObject(3, OffsetDateTime.ofInstant(session.getStartTime(), ZoneOffset.UTC));
            statement.setLong(4, session.getId());
            if (statement.executeUpdate() == 0) {
                throw new ExamException("Cannot update: no exam session with id " + session.getId());
            }
            return session;
        } catch (SQLException exception) {
            throw new ExamException("Could not update exam session", exception);
        }
    }

    @Override
    public Optional<ExamSession> findById(long id) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(toExamSession(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ExamException("Could not read exam session", exception);
        }
    }

    public Optional<ExamSession> findByExamAndStudent(long examId, long studentId) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_EXAM_AND_STUDENT)) {
            statement.setLong(1, examId);
            statement.setLong(2, studentId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(toExamSession(row)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new ExamException("Could not read exam session", exception);
        }
    }

    @Override
    public List<ExamSession> findAll() {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet row = statement.executeQuery()) {
            List<ExamSession> sessions = new ArrayList<>();
            while (row.next()) {
                sessions.add(toExamSession(row));
            }
            return sessions;
        } catch (SQLException exception) {
            throw new ExamException("Could not list exam sessions", exception);
        }
    }

    @Override
    public boolean deleteById(long id) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new ExamException("Could not delete exam session", exception);
        }
    }

    public boolean deleteByExamAndStudent(long examId, long studentId) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_EXAM_AND_STUDENT)) {
            statement.setLong(1, examId);
            statement.setLong(2, studentId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new ExamException("Could not delete exam session", exception);
        }
    }

    private static ExamSession toExamSession(ResultSet row) throws SQLException {
        OffsetDateTime startTime = row.getObject("start_time", OffsetDateTime.class);
        return new ExamSession(
            row.getLong("id"),
            row.getLong("exam_id"),
            row.getLong("student_id"),
            startTime.toInstant());
    }
}
