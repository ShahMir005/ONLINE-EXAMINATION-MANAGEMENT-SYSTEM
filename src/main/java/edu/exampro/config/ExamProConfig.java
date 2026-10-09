package edu.exampro.config;

import edu.exampro.app.ExamProApplication;
import edu.exampro.db.DatabaseInitializer;
import edu.exampro.model.AppUser;
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

            // In development mode, seed default dev accounts (admin, teacher, student)
            if (edu.exampro.service.DevSeedService.isDevelopment()) {
                edu.exampro.service.DevSeedService.seedDevAccounts(appUserRepository, studentRepository, passwordEncoder);
            }

            // Seed the initial administrator, or perform an explicitly requested local recovery.
            var existingAdmin = appUserRepository.findByEmail("admin@exampro.edu");
            boolean resetAdminPassword = Boolean.parseBoolean(
                System.getenv("EXAMPRO_RESET_ADMIN_PASSWORD"));
            if (existingAdmin.isEmpty()) {
                String adminPassword = System.getenv("ADMIN_PASSWORD");
                if (adminPassword == null || adminPassword.isBlank()) {
                    adminPassword = System.getenv("EXAMPRO_ADMIN_PASSWORD");
                }
                if (adminPassword != null && !adminPassword.isBlank()) {
                    log.info("Seeding initial administrator account with password from environment variable...");
                    appUserRepository.save(new AppUser(
                        null, "System Administrator", "admin@exampro.edu",
                        passwordEncoder.encode(adminPassword.trim()), "ADMIN", true));
                } else if (!edu.exampro.service.DevSeedService.isDevelopment()) {
                    throw new IllegalStateException(
                        "Administrator setup requires ADMIN_PASSWORD (or EXAMPRO_ADMIN_PASSWORD). "
                            + "Set it before starting ExamPro.");
                }
            } else if (resetAdminPassword) {
                String adminPassword = requiredAdminPassword();
                AppUser admin = existingAdmin.get();
                admin.setPasswordHash(passwordEncoder.encode(adminPassword.trim()));
                admin.setRole("ADMIN");
                admin.setEnabled(true);
                appUserRepository.save(admin);
                log.warn("The administrator password was reset through the local recovery option.");
            }
        };
    }

    /** Reads a password only when creating or explicitly recovering the administrator account. */
    private static String requiredAdminPassword() {
        String adminPassword = System.getenv("ADMIN_PASSWORD");
        if (adminPassword == null || adminPassword.isBlank()) {
            adminPassword = System.getenv("EXAMPRO_ADMIN_PASSWORD");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                "Administrator setup requires ADMIN_PASSWORD (or EXAMPRO_ADMIN_PASSWORD). "
                    + "Set it before starting ExamPro.");
        }
        return adminPassword;
    }
}
