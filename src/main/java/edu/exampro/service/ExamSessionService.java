package edu.exampro.service;

import edu.exampro.db.Database;
import edu.exampro.exception.ExamException;
import edu.exampro.model.Exam;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Manages active exam sessions, records examination start time when a candidate begins an exam,
 * and enforces the strict deadline: submissions must arrive before start + duration + 30 seconds.
 */
@Service
public class ExamSessionService {

    private static final String SELECT_SESSION =
        "SELECT start_time FROM exam_sessions WHERE exam_id = ? AND student_id = ?";

    private static final String INSERT_SESSION =
        "INSERT INTO exam_sessions (exam_id, student_id, start_time) VALUES (?, ?, ?)";

    private static final String DELETE_SESSION =
        "DELETE FROM exam_sessions WHERE exam_id = ? AND student_id = ?";

    /**
     * Records the start time when a student begins an exam.
     * If a session already exists for this (exam, student), returns the existing start time
     * to prevent timer tampering through browser reloads.
     */
    public Instant recordOrGetSession(long examId, long studentId) {
        Optional<Instant> existing = getSessionStartTime(examId, studentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        Instant now = Instant.now();
        try (Connection conn = Database.connection();
             PreparedStatement stmt = conn.prepareStatement(INSERT_SESSION)) {
            stmt.setLong(1, examId);
            stmt.setLong(2, studentId);
            stmt.setObject(3, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            stmt.executeUpdate();
            return now;
        } catch (SQLException e) {
            // Concurrent insert race: fetch and return existing session
            return getSessionStartTime(examId, studentId).orElse(now);
        }
    }

    /**
     * Looks up the recorded start time for an exam session.
     */
    public Optional<Instant> getSessionStartTime(long examId, long studentId) {
        try (Connection conn = Database.connection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_SESSION)) {
            stmt.setLong(1, examId);
            stmt.setLong(2, studentId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    OffsetDateTime odt = rs.getObject("start_time", OffsetDateTime.class);
                    return Optional.ofNullable(odt != null ? odt.toInstant() : null);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new ExamException("Could not look up exam session", e);
        }
    }

    /**
     * Validates that the submission is received within start + duration + 30 seconds grace period.
     * Throws ExamException if expired or no session exists.
     */
    public void validateSubmission(Exam exam, long studentId, Instant submissionTime) {
        Optional<Instant> startTimeOpt = getSessionStartTime(exam.getId(), studentId);
        if (startTimeOpt.isEmpty()) {
            throw new ExamException("Submission rejected: No active examination session found. You must start the examination before submitting.");
        }

        Instant startTime = startTimeOpt.get();
        Duration duration = exam.getDuration();
        Instant deadline = startTime.plus(duration).plusSeconds(30);

        if (submissionTime.isAfter(deadline)) {
            long secondsLate = Duration.between(deadline, submissionTime).getSeconds();
            throw new ExamException("Submission rejected: Examination time limit exceeded. Submissions must be received within the allotted duration ("
                + duration.toMinutes() + " minutes) plus the 30-second grace period. Submission was " + secondsLate + " seconds late.");
        }
    }

    /**
     * Removes an exam session (useful for resetting in tests or administration).
     */
    public void deleteSession(long examId, long studentId) {
        try (Connection conn = Database.connection();
             PreparedStatement stmt = conn.prepareStatement(DELETE_SESSION)) {
            stmt.setLong(1, examId);
            stmt.setLong(2, studentId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new ExamException("Could not delete exam session", e);
        }
    }
}
