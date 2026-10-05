package edu.exampro.config;

import edu.exampro.app.ExamProApplication;
import edu.exampro.db.DatabaseInitializer;
import edu.exampro.model.AppUser;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.ExamRepository;
import edu.exampro.repository.JdbcAttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import edu.exampro.service.ExamSubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class ExamProConfig {
    private static final Logger log = LoggerFactory.getLogger(ExamProConfig.class);

    @Bean
    public StudentRepository studentRepository() {
        return new StudentRepository();
    }

    @Bean
    public ExamRepository examRepository() {
        return new ExamRepository();
    }

    @Bean
    public AttemptRepository attemptRepository() {
        return new JdbcAttemptRepository();
    }

    @Bean
    public AppUserRepository appUserRepository() {
        return new AppUserRepository();
    }

    @Bean
    public ExamCatalog examCatalog(ExamRepository examRepository) {
        return new ExamCatalog(examRepository);
    }

    @Bean(destroyMethod = "close")
    public ExamSubmissionService examSubmissionService(AttemptRepository attemptRepository) {
        return new ExamSubmissionService(attemptRepository);
    }

    @Bean
    public ApplicationRunner initDatabaseAndSeed(StudentRepository studentRepository,
                                                ExamRepository examRepository,
                                                AppUserRepository appUserRepository,
                                                PasswordEncoder passwordEncoder) {
        return args -> {
            log.info("Ensuring database schema is initialized...");
            DatabaseInitializer.initialize();

            // Seed Review 1 demo data if empty
            if (studentRepository.findAll().isEmpty() || examRepository.findAll().isEmpty()) {
                log.info("Database is empty or missing exams. Running Review 1 initialization...");
                ExamProApplication.run();
                log.info("Initial Review 1 demo data initialized successfully!");
            }

            // Seed user accounts for authentication if empty
            if (appUserRepository.findAll().isEmpty()) {
                log.info("Seeding role-based authentication user accounts with BCrypt hashes...");
                
                // 1. Admin
                appUserRepository.save(new AppUser(
                    null, "System Administrator", "admin@exampro.edu",
                    passwordEncoder.encode("AdminPassword123!"), "ADMIN", true));

                // 2. Teacher
                appUserRepository.save(new AppUser(
                    null, "Professor Charles Babbage", "teacher@exampro.edu",
                    passwordEncoder.encode("TeacherPassword123!"), "TEACHER", true));

                // 3. Students
                appUserRepository.save(new AppUser(
                    null, "Ada Lovelace", "ada@exampro.edu",
                    passwordEncoder.encode("StudentPassword123!"), "STUDENT", true));

                appUserRepository.save(new AppUser(
                    null, "Alan Turing", "alan@exampro.edu",
                    passwordEncoder.encode("StudentPassword123!"), "STUDENT", true));

                log.info("Seeded 4 authentication accounts: admin, teacher, ada, alan.");
            }

            // Ensure students corresponding to ada and alan exist in students table
            if (studentRepository.findByEmail("ada@exampro.edu").isEmpty()) {
                studentRepository.save(new Student(null, "Ada Lovelace", "ada@exampro.edu", "STU-101"));
            }
            if (studentRepository.findByEmail("alan@exampro.edu").isEmpty()) {
                studentRepository.save(new Student(null, "Alan Turing", "alan@exampro.edu", "STU-102"));
            }
        };
    }
}
