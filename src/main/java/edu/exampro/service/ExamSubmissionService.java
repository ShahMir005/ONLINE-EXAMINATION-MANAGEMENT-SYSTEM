package edu.exampro.service;

import edu.exampro.exception.DuplicateSubmissionException;
import edu.exampro.exception.ExamException;
import edu.exampro.exception.ValidationException;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Question;
import edu.exampro.model.Student;
import edu.exampro.repository.AttemptRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Accepts submissions on a pool of worker threads.
 *
 * <p>The concurrent collections safely track in-flight submissions and provide one lock per
 * student and exam. The in-flight set rejects a second request while the first is running; the
 * lock keeps the stored-submission check and save together so two requests cannot both save.
 */
public final class ExamSubmissionService implements AutoCloseable {
    private static final int WORKER_THREADS = 4;
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private record SubmissionKey(long examId, long studentId) { }

    private final AttemptRepository attempts;
    private final ExecutorService executor = Executors.newFixedThreadPool(WORKER_THREADS);
    /** Holds student-and-exam keys while their submissions are being processed. */
    private final ConcurrentMap<SubmissionKey, ReentrantLock> locks = new ConcurrentHashMap<>();
    /** Rejects another request for the same student and exam until the current one finishes. */
    private final Set<String> inFlightSubmissions = ConcurrentHashMap.newKeySet();

    public ExamSubmissionService(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    /**
     * Checks input immediately, then uses a {@link CompletableFuture} to run the database work on
     * a worker thread without making the caller do that work itself.
     */
    public CompletableFuture<Attempt> submitAsync(Exam exam, Student student, Map<Long, Integer> answers) {
        requireStored(exam, student);
        requireValidAnswers(exam, answers);
        String submissionKey = student.getId() + ":" + exam.getId();
        if (!inFlightSubmissions.add(submissionKey)) {
            throw new DuplicateSubmissionException(exam.getId(), student.getId());
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return submit(exam, student, answers);
                } finally {
                    inFlightSubmissions.remove(submissionKey);
                }
            }, executor);
        } catch (RejectedExecutionException exception) {
            inFlightSubmissions.remove(submissionKey);
            throw exception;
        }
    }

    private Attempt submit(Exam exam, Student student, Map<Long, Integer> answers) {
        long examId = exam.getId();
        long studentId = student.getId();
        ReentrantLock lock = locks.computeIfAbsent(new SubmissionKey(examId, studentId), key -> new ReentrantLock());
        // The lock keeps the duplicate check and save together; a second thread must wait here.
        lock.lock();
        try {
            if (attempts.existsByExamAndStudent(examId, studentId)) {
                throw new DuplicateSubmissionException(examId, studentId);
            }
            Attempt attempt = new Attempt(null, examId, studentId, answers, Instant.now(), exam.score(answers));
            try {
                return attempts.save(attempt);
            } catch (ExamException exception) {
                if (attempts.existsByExamAndStudent(examId, studentId)) {
                    throw new DuplicateSubmissionException(examId, studentId);
                }
                throw exception;
            }
        } finally {
            lock.unlock();
        }
    }

    private static void requireStored(Exam exam, Student student) {
        if (exam == null || student == null) {
            throw new ValidationException("Exam and student are required");
        }
        if (exam.getId() == null || student.getId() == null) {
            throw new ValidationException("Exam and student must be stored before submission");
        }
    }

    /** Every answer must be for a question that belongs to this exam (checked with a Set). */
    private static void requireValidAnswers(Exam exam, Map<Long, Integer> answers) {
        if (answers == null) {
            throw new ValidationException("Answers are required");
        }
        Set<Long> examQuestionIds = new HashSet<>();
        for (Question<?> question : exam.getQuestions()) {
            examQuestionIds.add(question.getId());
        }
        for (Map.Entry<Long, Integer> answer : answers.entrySet()) {
            if (answer.getKey() == null || answer.getValue() == null) {
                throw new ValidationException("Answers cannot contain null values");
            }
            if (!examQuestionIds.contains(answer.getKey())) {
                throw new ValidationException(
                    "Question " + answer.getKey() + " does not belong to exam " + exam.getId());
            }
        }
    }

    /** Lets running submissions finish, then stops the worker threads. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
