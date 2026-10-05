package edu.exampro.repository;

import edu.exampro.db.Database;
import edu.exampro.db.Transactions;
import edu.exampro.exception.DuplicateSubmissionException;
import edu.exampro.exception.ExamException;
import edu.exampro.model.Attempt;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JdbcAttemptRepository implements AttemptRepository {
    private static final String EXISTS =
        "SELECT 1 FROM attempts WHERE exam_id = ? AND student_id = ?";
    private static final String INSERT_ATTEMPT =
        "INSERT INTO attempts(exam_id, student_id, score, submitted_at) VALUES (?, ?, ?, ?)";
    private static final String INSERT_ANSWER =
        "INSERT INTO answers(attempt_id, question_id, selected_option) VALUES (?, ?, ?)";
    private static final String SELECT_ATTEMPTS =
        "SELECT id, student_id, score, submitted_at FROM attempts WHERE exam_id = ? ORDER BY id";
    private static final String SELECT_ANSWERS =
        "SELECT a.attempt_id, a.question_id, a.selected_option FROM answers a "
            + "JOIN attempts t ON t.id = a.attempt_id "
            + "WHERE t.exam_id = ? ORDER BY a.attempt_id, a.question_id";

    /**
     * Transaction: duplicate check + attempt row + all answer rows are committed together.
     * Any failure (SQL or runtime) rolls everything back.
     */
    @Override
    public Attempt save(Attempt attempt) {
        long attemptId = Transactions.run("Could not save attempt", connection -> {
            if (exists(connection, attempt.getExamId(), attempt.getStudentId())) {
                throw new DuplicateSubmissionException(attempt.getExamId(), attempt.getStudentId());
            }
            long id = insertAttempt(connection, attempt);
            insertAnswers(connection, id, attempt.getAnswers());
            return id;
        });
        attempt.setId(attemptId);
        return attempt;
    }

    @Override
    public boolean existsByExamAndStudent(long examId, long studentId) {
        try (Connection connection = Database.connection()) {
            return exists(connection, examId, studentId);
        } catch (SQLException exception) {
            throw new ExamException("Could not check existing attempts", exception);
        }
    }

    @Override
    public List<Attempt> findByExamId(long examId) {
        try (Connection connection = Database.connection()) {
            Map<Long, Map<Long, Integer>> answersByAttempt = loadAnswers(connection, examId);
            List<Attempt> attempts = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_ATTEMPTS)) {
                statement.setLong(1, examId);
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        long id = row.getLong("id");
                        Instant submittedAt = row.getObject("submitted_at", OffsetDateTime.class).toInstant();
                        attempts.add(new Attempt(
                            id,
                            examId,
                            row.getLong("student_id"),
                            answersByAttempt.getOrDefault(id, Map.of()),
                            submittedAt,
                            row.getInt("score")));
                    }
                }
            }
            return attempts;
        } catch (SQLException exception) {
            throw new ExamException("Could not read attempts", exception);
        }
    }

    @Override
    public Optional<Attempt> findById(long id) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, exam_id, student_id, score, submitted_at FROM attempts WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                long examId = row.getLong("exam_id");
                long studentId = row.getLong("student_id");
                int score = row.getInt("score");
                Instant submittedAt = row.getObject("submitted_at", OffsetDateTime.class).toInstant();
                Map<Long, Integer> answers = loadAnswersForAttempt(connection, id);
                return Optional.of(new Attempt(id, examId, studentId, answers, submittedAt, score));
            }
        } catch (SQLException exception) {
            throw new ExamException("Could not read attempt", exception);
        }
    }

    @Override
    public List<Attempt> findAll() {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT id, exam_id, student_id, score, submitted_at FROM attempts ORDER BY submitted_at DESC, id DESC");
             ResultSet row = statement.executeQuery()) {
            List<Attempt> attempts = new ArrayList<>();
            while (row.next()) {
                long id = row.getLong("id");
                long examId = row.getLong("exam_id");
                long studentId = row.getLong("student_id");
                int score = row.getInt("score");
                Instant submittedAt = row.getObject("submitted_at", OffsetDateTime.class).toInstant();
                attempts.add(new Attempt(id, examId, studentId, Map.of(), submittedAt, score));
            }
            return attempts;
        } catch (SQLException exception) {
            throw new ExamException("Could not read all attempts", exception);
        }
    }

    private static Map<Long, Integer> loadAnswersForAttempt(Connection connection, long attemptId) throws SQLException {
        Map<Long, Integer> answers = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT question_id, selected_option FROM answers WHERE attempt_id = ? ORDER BY question_id")) {
            statement.setLong(1, attemptId);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    answers.put(row.getLong("question_id"), row.getInt("selected_option"));
                }
            }
        }
        return answers;
    }

    // ---- helpers -------------------------------------------------------------------------

    private static boolean exists(Connection connection, long examId, long studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(EXISTS)) {
            statement.setLong(1, examId);
            statement.setLong(2, studentId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }

    private static long insertAttempt(Connection connection, Attempt attempt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ATTEMPT, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, attempt.getExamId());
            statement.setLong(2, attempt.getStudentId());
            statement.setInt(3, attempt.getScore());
            statement.setObject(4, OffsetDateTime.ofInstant(attempt.getSubmittedAt(), ZoneOffset.UTC));
            statement.executeUpdate();
            return Database.generatedKey(statement);
        }
    }

    private static void insertAnswers(Connection connection, long attemptId, Map<Long, Integer> answers)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ANSWER)) {
            for (Map.Entry<Long, Integer> answer : answers.entrySet()) {
                statement.setLong(1, attemptId);
                statement.setLong(2, answer.getKey());
                statement.setInt(3, answer.getValue());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static Map<Long, Map<Long, Integer>> loadAnswers(Connection connection, long examId) throws SQLException {
        Map<Long, Map<Long, Integer>> result = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ANSWERS)) {
            statement.setLong(1, examId);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    result.computeIfAbsent(row.getLong("attempt_id"), key -> new LinkedHashMap<>())
                        .put(row.getLong("question_id"), row.getInt("selected_option"));
                }
            }
        }
        return result;
    }
}
