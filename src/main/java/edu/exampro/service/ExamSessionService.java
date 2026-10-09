package edu.exampro.service;

import edu.exampro.exception.ExamException;
import edu.exampro.model.Exam;
import edu.exampro.model.ExamSession;
import edu.exampro.repository.ExamSessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Manages active exam sessions, records examination start time when a candidate begins an exam,
 * and enforces the strict deadline: submissions must arrive before start + duration + 30 seconds.
 */
@Service
public class ExamSessionService {

    private final ExamSessionRepository examSessionRepository;

    public ExamSessionService(ExamSessionRepository examSessionRepository) {
        this.examSessionRepository = examSessionRepository;
    }

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
        try {
            examSessionRepository.save(new ExamSession(null, examId, studentId, now));
            return now;
        } catch (ExamException e) {
            // Concurrent insert race: fetch and return existing session
            return getSessionStartTime(examId, studentId).orElse(now);
        }
    }

    /**
     * Looks up the recorded start time for an exam session.
     */
    public Optional<Instant> getSessionStartTime(long examId, long studentId) {
        return examSessionRepository.findByExamAndStudent(examId, studentId)
            .map(ExamSession::getStartTime);
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
        examSessionRepository.deleteByExamAndStudent(examId, studentId);
    }
}
