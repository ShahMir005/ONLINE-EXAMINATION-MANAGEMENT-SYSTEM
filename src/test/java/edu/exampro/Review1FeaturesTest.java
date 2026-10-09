package edu.exampro;

import edu.exampro.db.DatabaseInitializer;
import edu.exampro.db.Transactions;
import edu.exampro.exception.DuplicateSubmissionException;
import edu.exampro.exception.ValidationException;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.ExamSession;
import edu.exampro.model.Instructor;
import edu.exampro.model.MultipleChoiceQuestion;
import edu.exampro.model.Question;
import edu.exampro.model.Student;
import edu.exampro.model.TrueFalseQuestion;
import edu.exampro.model.User;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.ExamRepository;
import edu.exampro.repository.ExamSessionRepository;
import edu.exampro.repository.JdbcAttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import edu.exampro.service.ExamSubmissionService;
import edu.exampro.service.InMemoryCache;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Review1FeaturesTest {

    @BeforeAll
    public static void setup() {
        DatabaseInitializer.initialize();
    }

    @Test
    @DisplayName("OOP: Polymorphism and method overriding across User subclasses")
    public void testPolymorphismAndUserSubclasses() {
        User instructor = new Instructor(1L, "Dr. Ada", "ada.inst@exampro.edu", "CS");
        User student = new Student(2L, "Alan", "alan.stud@exampro.edu", "STU-001");

        assertEquals("INSTRUCTOR", instructor.getRole());
        assertEquals("STUDENT", student.getRole());

        assertTrue(instructor.describe().contains("INSTRUCTOR: Dr. Ada"));
        assertTrue(instructor.describe().contains("(CS)"));

        assertTrue(student.describe().contains("STUDENT: Alan"));
        assertTrue(student.describe().contains("[STU-001]"));

        assertThrows(ValidationException.class, () -> new Student(null, "", "bad@email.com", "STU-X"));
        assertThrows(ValidationException.class, () -> new Student(null, "Good", "no-at-sign", "STU-X"));
    }

    @Test
    @DisplayName("OOP: Generic Question<T> hierarchy with MultipleChoiceQuestion and TrueFalseQuestion")
    public void testQuestionHierarchyAndScoring() {
        Question<Integer> mcq = new MultipleChoiceQuestion(
            1L, "Which keyword is used for inheritance?", 5,
            List.of("implements", "extends", "inherits", "super"), 1);

        assertEquals("MCQ", mcq.getType());
        assertEquals(4, mcq.choices().size());
        assertEquals(1, mcq.getCorrectOptionIndex());
        assertEquals(5, mcq.marksFor(1));
        assertEquals(0, mcq.marksFor(0));
        assertEquals(0, mcq.marksFor(null));

        Question<Boolean> tf = new TrueFalseQuestion(2L, "Java supports primitive types.", 3, true);
        assertEquals("TF", tf.getType());
        assertEquals(2, tf.choices().size());
        assertEquals(0, tf.getCorrectOptionIndex());
        assertEquals(3, tf.marksFor(0)); // True = 0
        assertEquals(0, tf.marksFor(1)); // False = 1
    }

    @Test
    @DisplayName("Collections & Generics: InMemoryCache<T> thread-safe cache operations")
    public void testInMemoryCache() {
        InMemoryCache<Student> cache = new InMemoryCache<>();
        Student s1 = new Student(100L, "John Doe", "john@exampro.edu", "STU-100");
        cache.put(s1);

        assertEquals(1, cache.size());
        Optional<Student> found = cache.get(100L);
        assertTrue(found.isPresent());
        assertEquals("John Doe", found.get().getName());

        cache.remove(100L);
        assertTrue(cache.get(100L).isEmpty());
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("JDBC CRUD: StudentRepository full CRUD with PreparedStatement")
    public void testStudentCrud() {
        StudentRepository repo = new StudentRepository();
        String testEmail = "test_crud_" + System.currentTimeMillis() + "@exampro.edu";

        Student student = new Student(null, "Test Student", testEmail, "STU-TEST-" + System.currentTimeMillis());
        Student saved = repo.save(student);
        assertNotNull(saved.getId());

        Optional<Student> fetched = repo.findById(saved.getId());
        assertTrue(fetched.isPresent());
        assertEquals("Test Student", fetched.get().getName());

        Student updated = new Student(saved.getId(), "Updated Name", testEmail, saved.getRegistrationNumber());
        repo.save(updated);
        assertEquals("Updated Name", repo.findById(saved.getId()).orElseThrow().getName());

        assertTrue(repo.deleteById(saved.getId()));
        assertTrue(repo.findById(saved.getId()).isEmpty());
    }

    @Test
    @DisplayName("JDBC CRUD: ExamSessionRepository")
    public void testExamSessionCrud() {
        StudentRepository studentRepository = new StudentRepository();
        ExamCatalog examCatalog = new ExamCatalog(new ExamRepository());
        ExamSessionRepository sessionRepository = new ExamSessionRepository();

        long suffix = System.currentTimeMillis();
        Student student = studentRepository.save(
            new Student(null, "Session Test Student", "session_" + suffix + "@exampro.edu", "STU-" + suffix));
        Exam exam = new Exam(null, "Session Test Exam " + suffix, Duration.ofMinutes(15));
        exam.addQuestion(new TrueFalseQuestion(null, "Session CRUD test question.", 1, true));
        Exam savedExam = examCatalog.create(exam);
        Instant startTime = Instant.now();

        ExamSession saved = sessionRepository.save(
            new ExamSession(null, savedExam.getId(), student.getId(), startTime));
        assertNotNull(saved.getId());

        ExamSession fetched = sessionRepository.findById(saved.getId()).orElseThrow();
        assertEquals(savedExam.getId(), fetched.getExamId());
        assertEquals(student.getId(), fetched.getStudentId());
        assertEquals(startTime, fetched.getStartTime());
        assertTrue(sessionRepository.findByExamAndStudent(savedExam.getId(), student.getId()).isPresent());

        assertTrue(sessionRepository.deleteById(saved.getId()));
        assertTrue(sessionRepository.findById(saved.getId()).isEmpty());
    }

    @Test
    @DisplayName("JDBC Transaction: Rollback on database failure")
    public void testTransactionRollback() {
        ExamRepository examRepository = new ExamRepository();
        int initialCount = examRepository.findAll().size();

        Exam broken = new Exam(null, "Broken Exam " + System.currentTimeMillis(), Duration.ofMinutes(15));
        broken.addQuestion(new TrueFalseQuestion(null, "Valid question prompt", 2, true));
        // Prompt exceeding column length (VARCHAR 1000)
        broken.addQuestion(new TrueFalseQuestion(null, "Z".repeat(1005), 2, true));

        assertThrows(Exception.class, () -> examRepository.createWithQuestions(broken));

        // Verify rollback: count of exams must not have increased
        int postCount = examRepository.findAll().size();
        assertEquals(initialCount, postCount);
    }

    @Test
    @DisplayName("Multithreading: ExamSubmissionService rejects concurrent duplicate submissions")
    public void testConcurrentSubmissionsAndDuplicateLock() {
        StudentRepository studentRepo = new StudentRepository();
        ExamRepository examRepo = new ExamRepository();
        AttemptRepository attemptRepo = new JdbcAttemptRepository();
        ExamCatalog catalog = new ExamCatalog(examRepo);

        String email = "concurrent_" + System.currentTimeMillis() + "@exampro.edu";
        Student candidate = studentRepo.save(
            new Student(null, "Concurrent Candidate", email, "REG-" + System.currentTimeMillis()));

        Exam exam = new Exam(null, "Concurrency Test Exam " + System.currentTimeMillis(), Duration.ofMinutes(20));
        exam.addQuestion(new TrueFalseQuestion(null, "Concurrent thread safety is crucial.", 5, true));
        Exam savedExam = catalog.create(exam);

        try (ExamSubmissionService submissionService = new ExamSubmissionService(attemptRepo)) {
            Map<Long, Integer> answers = Map.of(savedExam.getQuestions().get(0).getId(), 0);

            // Fire two simultaneous submissions for the SAME student and exam
            CompletableFuture<Attempt> future1 = submissionService.submitAsync(savedExam, candidate, answers);
            int successCount = 0;
            int duplicateCount = 0;
            CompletableFuture<Attempt> future2 = null;
            try {
                future2 = submissionService.submitAsync(savedExam, candidate, answers);
            } catch (DuplicateSubmissionException e) {
                duplicateCount++;
            }

            try {
                Attempt a1 = future1.join();
                if (a1 != null) successCount++;
            } catch (CompletionException e) {
                if (e.getCause() instanceof DuplicateSubmissionException) duplicateCount++;
            }

            if (future2 != null) {
                try {
                    Attempt a2 = future2.join();
                    if (a2 != null) successCount++;
                } catch (CompletionException e) {
                    if (e.getCause() instanceof DuplicateSubmissionException) duplicateCount++;
                }
            }

            // Exactly 1 must succeed and 1 must be rejected as a duplicate.
            assertEquals(1, successCount, "Exactly one submission must succeed");
            assertEquals(1, duplicateCount, "Exactly one submission must be rejected as duplicate");
        }
    }

    @Test
    @DisplayName("Multithreading: Set rejects an in-flight duplicate submission for the same student and exam")
    public void testInFlightDuplicateSubmissionRejectedBySet() throws Exception {
        StudentRepository studentRepo = new StudentRepository();
        ExamRepository examRepo = new ExamRepository();
        JdbcAttemptRepository jdbcAttempts = new JdbcAttemptRepository();
        CountDownLatch firstSubmissionChecking = new CountDownLatch(1);
        CountDownLatch allowFirstSubmission = new CountDownLatch(1);
        AttemptRepository attemptRepo = new AttemptRepository() {
            @Override
            public Attempt save(Attempt attempt) {
                return jdbcAttempts.save(attempt);
            }

            @Override
            public boolean existsByExamAndStudent(long examId, long studentId) {
                firstSubmissionChecking.countDown();
                try {
                    if (!allowFirstSubmission.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to continue submission");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting to continue submission", interrupted);
                }
                return jdbcAttempts.existsByExamAndStudent(examId, studentId);
            }

            @Override
            public List<Attempt> findByExamId(long examId) {
                return jdbcAttempts.findByExamId(examId);
            }

            @Override
            public Optional<Attempt> findById(long id) {
                return jdbcAttempts.findById(id);
            }

            @Override
            public List<Attempt> findAll() {
                return jdbcAttempts.findAll();
            }
        };
        ExamCatalog catalog = new ExamCatalog(examRepo);

        String email = "inflight_" + System.currentTimeMillis() + "@exampro.edu";
        Student candidate = studentRepo.save(
            new Student(null, "In-flight Candidate", email, "REG-" + System.currentTimeMillis()));
        Exam exam = new Exam(null, "In-flight Duplicate Test " + System.currentTimeMillis(),
            Duration.ofMinutes(20));
        exam.addQuestion(new TrueFalseQuestion(null, "The duplicate guard is thread-safe.", 5, true));
        Exam savedExam = catalog.create(exam);
        Map<Long, Integer> answers = Map.of(savedExam.getQuestions().get(0).getId(), 0);

        try (ExamSubmissionService submissionService = new ExamSubmissionService(attemptRepo)) {
            CompletableFuture<Attempt> firstSubmission =
                submissionService.submitAsync(savedExam, candidate, answers);
            try {
                assertTrue(firstSubmissionChecking.await(5, TimeUnit.SECONDS),
                    "First submission should reach the repository while remaining in flight");
                assertThrows(DuplicateSubmissionException.class,
                    () -> submissionService.submitAsync(savedExam, candidate, answers));
            } finally {
                allowFirstSubmission.countDown();
            }

            assertNotNull(firstSubmission.join().getId());
        }
    }
}
