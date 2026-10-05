package edu.exampro;

import edu.exampro.db.DatabaseInitializer;
import edu.exampro.db.Transactions;
import edu.exampro.exception.DuplicateSubmissionException;
import edu.exampro.exception.ValidationException;
import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Instructor;
import edu.exampro.model.MultipleChoiceQuestion;
import edu.exampro.model.Question;
import edu.exampro.model.Student;
import edu.exampro.model.TrueFalseQuestion;
import edu.exampro.model.User;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.ExamRepository;
import edu.exampro.repository.JdbcAttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import edu.exampro.service.ExamSubmissionService;
import edu.exampro.service.InMemoryCache;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    @DisplayName("Multithreading: ExamSubmissionService concurrent submissions and ReentrantLock duplicate rejection")
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
            CompletableFuture<Attempt> future2 = submissionService.submitAsync(savedExam, candidate, answers);

            int successCount = 0;
            int duplicateCount = 0;

            try {
                Attempt a1 = future1.join();
                if (a1 != null) successCount++;
            } catch (CompletionException e) {
                if (e.getCause() instanceof DuplicateSubmissionException) duplicateCount++;
            }

            try {
                Attempt a2 = future2.join();
                if (a2 != null) successCount++;
            } catch (CompletionException e) {
                if (e.getCause() instanceof DuplicateSubmissionException) duplicateCount++;
            }

            // Exactly 1 must succeed and 1 must be rejected by the ReentrantLock
            assertEquals(1, successCount, "Exactly one submission must succeed");
            assertEquals(1, duplicateCount, "Exactly one submission must be rejected as duplicate");
        }
    }
}
