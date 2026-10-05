package edu.exampro.app;

import edu.exampro.db.DatabaseInitializer;
import edu.exampro.exception.ExamException;
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
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Console demo that exercises every Review 1 topic, one numbered section at a time, and Spring Boot application. */
@SpringBootApplication(scanBasePackages = "edu.exampro")
public class ExamProApplication {
    private static final String DEMO_EXAM_TITLE = "ExamPro demo exam";

    public ExamProApplication() { }

    public static void main(String[] args) {
        if (args.length > 0 && "--console".equalsIgnoreCase(args[0])) {
            try {
                run();
            } catch (ExamException exception) {
                System.err.println("ExamPro stopped: " + exception.getMessage());
                if (exception.getCause() != null) {
                    System.err.println("Cause: " + exception.getCause().getMessage());
                }
            }
            return;
        }

        DatabaseInitializer.initialize();
        SpringApplication.run(ExamProApplication.class, args);
    }

    public static void run() {
        DatabaseInitializer.initialize();
        StudentRepository students = new StudentRepository();
        ExamRepository examRepository = new ExamRepository();
        AttemptRepository attempts = new JdbcAttemptRepository();
        ExamCatalog catalog = new ExamCatalog(examRepository);

        removeDemoExamsFromEarlierRuns(examRepository);

        showPolymorphism();
        showStudentCrud(students);

        Student ada = findOrCreate(students, "Ada Lovelace", "ada@exampro.edu", "STU-101");
        Student alan = findOrCreate(students, "Alan Turing", "alan@exampro.edu", "STU-102");
        Student grace = findOrCreate(students, "Grace Hopper", "grace@exampro.edu", "STU-103");

        Exam exam = createDemoExam(catalog);
        showCollections(exam);
        showExamCrud(catalog, examRepository, exam);
        showTransactionRollback(examRepository);
        showConcurrentSubmissions(attempts, exam, ada, alan, grace);
        showResults(attempts, students, exam);
    }

    // ---- 1. OOP ---------------------------------------------------------------------------

    private static void showPolymorphism() {
        section("1. OOP: inheritance and polymorphism");
        List<User> users = List.of(
            new Instructor(null, "Dr. Smith", "smith@exampro.edu", "Computer Science"),
            new Student(null, "Demo Student", "demo@exampro.edu", "STU-000"));
        for (User user : users) {
            // The same call runs different code depending on the real class of the object.
            System.out.println("  " + user.describe());
        }
        try {
            new Student(null, " ", "bad", "STU-X");
        } catch (ExamException exception) {
            System.out.println("  Custom exception caught: " + exception.getMessage());
        }
    }

    // ---- 2. JDBC CRUD ----------------------------------------------------------------------

    private static void showStudentCrud(StudentRepository students) {
        section("2. JDBC CRUD with PreparedStatement (students)");
        students.findByEmail("temp@exampro.edu").ifPresent(old -> students.deleteById(old.getId()));

        Student created = students.save(new Student(null, "Temp Student", "temp@exampro.edu", "STU-TMP"));
        System.out.println("  CREATE -> id " + created.getId() + ": " + created.describe());

        Student read = students.findById(created.getId()).orElseThrow();
        System.out.println("  READ   -> " + read.describe());

        students.save(new Student(created.getId(), "Temp Student", "temp@exampro.edu", "STU-TMP-2"));
        System.out.println("  UPDATE -> " + students.findById(created.getId()).orElseThrow().describe());

        boolean deleted = students.deleteById(created.getId());
        System.out.println("  DELETE -> removed=" + deleted + ", still exists=" + students.findById(created.getId()).isPresent());
    }

    // ---- 3. Collections and generics --------------------------------------------------------

    private static void showCollections(Exam exam) {
        section("3. Collections and generics (List, Set, Map)");
        Set<String> types = new TreeSet<>();
        Map<String, Integer> marksByType = new TreeMap<>();
        for (Question<?> question : exam.getQuestions()) {
            System.out.printf("  [%s] %s (%d marks, choices: %s)%n",
                question.getType(), question.getPrompt(), question.getMarks(), question.choices());
            types.add(question.getType());
            marksByType.merge(question.getType(), question.getMarks(), Integer::sum);
        }
        System.out.println("  Question types (Set): " + types);
        System.out.println("  Marks per type (Map): " + marksByType);
    }

    private static void showExamCrud(ExamCatalog catalog, ExamRepository examRepository, Exam exam) {
        section("4. Exam read, update and cache (InMemoryCache<T>)");
        System.out.println("  Cached right after creation: " + catalog.isCached(exam.getId()));
        System.out.println("  Loaded from database: " + examRepository.findById(exam.getId()).orElseThrow().getTitle());

        exam.setTitle(DEMO_EXAM_TITLE + " (updated)");
        catalog.update(exam);
        System.out.println("  After UPDATE, database says: " + examRepository.findById(exam.getId()).orElseThrow().getTitle());
    }

    // ---- 4. Transactions --------------------------------------------------------------------

    private static void showTransactionRollback(ExamRepository examRepository) {
        section("5. JDBC transaction: commit and rollback");
        int before = examRepository.findAll().size();

        Exam broken = new Exam(null, "Broken exam", Duration.ofMinutes(10));
        broken.addQuestion(new TrueFalseQuestion(null, "A perfectly valid question", 1, true));
        broken.addQuestion(new TrueFalseQuestion(null, "x".repeat(1001), 1, true)); // too long for the column

        try {
            examRepository.createWithQuestions(broken);
            System.out.println("  Unexpected: the broken exam was saved");
            examRepository.deleteById(broken.getId());
        } catch (ExamException exception) {
            String reason = exception.getCause() == null ? exception.getMessage()
                : exception.getCause().getMessage().lines().findFirst().orElse("");
            System.out.println("  Second question failed: " + reason);
        }
        int after = examRepository.findAll().size();
        System.out.println("  Exams before=" + before + ", after=" + after
            + " -> the first question was rolled back too (broken exam id: " + broken.getId() + ")");
    }

    // ---- 5. Multithreading ------------------------------------------------------------------

    private static void showConcurrentSubmissions(
            AttemptRepository attempts, Exam exam, Student ada, Student alan, Student grace) {
        section("6. Multithreading: concurrent submissions");
        Map<Long, Integer> graceAnswers = answers(exam, 1, 0, 1);
        try (ExamSubmissionService submissions = new ExamSubmissionService(attempts)) {
            CompletableFuture<Attempt> adaFuture = submissions.submitAsync(exam, ada, answers(exam, 1, 1, 1));
            CompletableFuture<Attempt> alanFuture = submissions.submitAsync(exam, alan, answers(exam, 0, 1, 0));
            // Grace submits the SAME exam twice at the same moment: exactly one may succeed.
            CompletableFuture<Attempt> graceFirst = submissions.submitAsync(exam, grace, graceAnswers);
            CompletableFuture<Attempt> graceSecond = submissions.submitAsync(exam, grace, graceAnswers);

            report("Ada Lovelace", adaFuture, exam);
            report("Alan Turing", alanFuture, exam);
            report("Grace Hopper (try 1)", graceFirst, exam);
            report("Grace Hopper (try 2)", graceSecond, exam);
        }
    }

    private static void report(String label, CompletableFuture<Attempt> future, Exam exam) {
        try {
            Attempt attempt = future.join();
            System.out.printf("  %-22s saved, score %d/%d%n", label, attempt.getScore(), exam.totalMarks());
        } catch (CompletionException exception) {
            System.out.printf("  %-22s rejected: %s%n", label, exception.getCause().getMessage());
        }
    }

    private static void showResults(AttemptRepository attempts, StudentRepository students, Exam exam) {
        section("7. Final results read back from the database");
        List<Attempt> saved = attempts.findByExamId(exam.getId());
        saved.sort(Comparator.comparingInt(Attempt::getScore).reversed());
        for (Attempt attempt : saved) {
            String name = students.findById(attempt.getStudentId()).map(Student::getName).orElse("unknown");
            System.out.printf("  %-14s %2d/%d  (%d answers, submitted %s)%n",
                name, attempt.getScore(), exam.totalMarks(), attempt.getAnswers().size(), attempt.getSubmittedAt());
        }
        System.out.println("  Exactly " + saved.size() + " attempts stored (expected 3).");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static Exam createDemoExam(ExamCatalog catalog) {
        Exam exam = new Exam(null, DEMO_EXAM_TITLE, Duration.ofMinutes(30));
        exam.addQuestion(new MultipleChoiceQuestion(null, "Which feature enables runtime polymorphism?", 5,
            List.of("Overloading", "Overriding", "Encapsulation", "Generics"), 1));
        exam.addQuestion(new MultipleChoiceQuestion(null, "Which JDBC class is safe for parameterized SQL?", 5,
            List.of("Statement", "PreparedStatement", "ResultSet", "DriverManager"), 1));
        exam.addQuestion(new TrueFalseQuestion(null, "An interface can be instantiated directly with new.", 2, false));
        return catalog.create(exam);
    }

    /** Builds questionId -> chosen option index, in the order the questions appear in the exam. */
    private static Map<Long, Integer> answers(Exam exam, int... chosenOptions) {
        Map<Long, Integer> answers = new LinkedHashMap<>();
        List<Question<?>> questions = exam.getQuestions();
        for (int i = 0; i < chosenOptions.length; i++) {
            answers.put(questions.get(i).getId(), chosenOptions[i]);
        }
        return answers;
    }

    private static Student findOrCreate(StudentRepository students, String name, String email, String registration) {
        return students.findByEmail(email)
            .orElseGet(() -> students.save(new Student(null, name, email, registration)));
    }

    private static void removeDemoExamsFromEarlierRuns(ExamRepository examRepository) {
        int removed = 0;
        for (Exam old : examRepository.findAll()) {
            if (old.getTitle().startsWith(DEMO_EXAM_TITLE) && examRepository.deleteById(old.getId())) {
                removed++;
            }
        }
        if (removed > 0) {
            System.out.println("Removed " + removed + " demo exam(s) left by an earlier run.");
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }
}
