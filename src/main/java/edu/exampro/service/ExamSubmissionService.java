package edu.exampro.service;

import edu.exampro.exception.DuplicateSubmissionException;
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
 * <p>Concurrency rule: for ONE (exam, student) pair, the "already submitted?" check and the save
 * happen under the same lock, so two simultaneous submissions can never both succeed. Different
 * students use different locks and run in parallel.
 */
public final class ExamSubmissionService implements AutoCloseable {
    private static final int WORKER_THREADS = 4;
    private static final long SHUTDOWN_WAIT_SECONDS = 10;

    private record SubmissionKey(long examId, long studentId) { }

    private final AttemptRepository attempts;
    private final ExecutorService executor = Executors.newFixedThreadPool(WORKER_THREADS);
    private final ConcurrentMap<SubmissionKey, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final Set<String> inFlightSubmissions = ConcurrentHashMap.newKeySet();

    public ExamSubmissionService(AttemptRepository attempts) {
        this.attempts = attempts;
    }

    /** Invalid input fails immediately; storage work runs on a worker thread. */
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
        lock.lock();
        try {
            if (attempts.existsByExamAndStudent(examId, studentId)) {
                throw new DuplicateSubmissionException(examId, studentId);
            }
            Attempt attempt = new Attempt(null, examId, studentId, answers, Instant.now(), exam.score(answers));
            return attempts.save(attempt);
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
