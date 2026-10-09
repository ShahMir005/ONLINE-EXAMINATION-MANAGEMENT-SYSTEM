package edu.exampro;

import edu.exampro.exception.DuplicateSubmissionException;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Student;
import edu.exampro.model.TrueFalseQuestion;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.service.ExamSubmissionService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamSubmissionServiceConcurrencyTest {

    @Test
    void rejectsInFlightDuplicateAndReleasesKeyAfterFailure() {
        CountDownLatch firstCheckStarted = new CountDownLatch(1);
        CountDownLatch allowFirstCheckToFinish = new CountDownLatch(1);
        AtomicBoolean firstSave = new AtomicBoolean(true);
        AttemptRepository repository = new AttemptRepository() {
            @Override
            public Attempt save(Attempt attempt) {
                if (firstSave.getAndSet(false)) {
                    throw new IllegalStateException("Simulated storage failure");
                }
                attempt.setId(1L);
                return attempt;
            }

            @Override
            public boolean existsByExamAndStudent(long examId, long studentId) {
                if (firstCheckStarted.getCount() > 0) {
                    firstCheckStarted.countDown();
                    try {
                        if (!allowFirstCheckToFinish.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out waiting to continue submission");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted while waiting to continue submission", interrupted);
                    }
                }
                return false;
            }

            @Override
            public List<Attempt> findByExamId(long examId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<Attempt> findById(long id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Attempt> findAll() {
                throw new UnsupportedOperationException();
            }
        };

        Exam exam = new Exam(3L, "Concurrency Test", Duration.ofMinutes(10));
        exam.addQuestion(new TrueFalseQuestion(4L, "Concurrency is tested.", 1, true));
        Student student = new Student(7L, "Test Student", "student@example.com", "STU-7");
        Map<Long, Integer> answers = Map.of(4L, 0);

        try (ExamSubmissionService service = new ExamSubmissionService(repository)) {
            var firstSubmission = service.submitAsync(exam, student, answers);
            try {
                assertTrue(firstCheckStarted.await(5, TimeUnit.SECONDS), "First submission should start");
                assertThrows(DuplicateSubmissionException.class,
                    () -> service.submitAsync(exam, student, answers));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for first submission", interrupted);
            } finally {
                allowFirstCheckToFinish.countDown();
            }

            var failedSubmission = assertThrows(
                java.util.concurrent.CompletionException.class,
                firstSubmission::join);
            assertTrue(failedSubmission.getCause() instanceof IllegalStateException);

            Attempt retry = service.submitAsync(exam, student, answers).join();
            assertEquals(1L, retry.getId());
        }
    }
}
