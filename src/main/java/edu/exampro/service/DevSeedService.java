package edu.exampro.service;

import edu.exampro.model.AppUser;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.StudentRepository;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Development Seed & Password Recovery Service.
 *
 * Automatically seeds default accounts for development mode:
 *  - Admin:   admin@exampro.local   / Admin@123
 *  - Teacher: teacher@exampro.local / Teacher@123
 *  - Student: student@exampro.local / Student@123
 *
 * Strictly executes ONLY in development mode (never in production).
 * Passwords are encrypted using BCrypt.
 */
public class DevSeedService {
    private static final Logger log = LoggerFactory.getLogger(DevSeedService.class);

    public static final String DEV_ADMIN_EMAIL = "admin@exampro.local";
    public static final String DEV_ADMIN_PASS = "Admin@123";

    public static final String DEV_TEACHER_EMAIL = "teacher@exampro.local";
    public static final String DEV_TEACHER_PASS = "Teacher@123";

    public static final String DEV_STUDENT_EMAIL = "student@exampro.local";
    public static final String DEV_STUDENT_PASS = "Student@123";

    /**
     * Determines whether the application is running in development mode.
     * Checks NODE_ENV, APP_ENV, spring.profiles.active, and app.env.
     * Never returns true if explicitly set to production / prod.
     */
    public static boolean isDevelopment() {
        String nodeEnv = System.getenv("NODE_ENV");
        String appEnv = System.getenv("APP_ENV");
        String springProfiles = System.getProperty("spring.profiles.active", System.getenv("SPRING_PROFILES_ACTIVE"));
        String configEnv = System.getProperty("app.env", "development");

        // Explicit production overrides
        if ("production".equalsIgnoreCase(nodeEnv) || "prod".equalsIgnoreCase(nodeEnv)
                || "production".equalsIgnoreCase(appEnv) || "prod".equalsIgnoreCase(appEnv)
                || "production".equalsIgnoreCase(springProfiles) || "prod".equalsIgnoreCase(springProfiles)
                || "production".equalsIgnoreCase(configEnv) || "prod".equalsIgnoreCase(configEnv)) {
            return false;
        }

        // Return true if any environment indicator specifies development/dev
        if ("development".equalsIgnoreCase(nodeEnv) || "dev".equalsIgnoreCase(nodeEnv)
                || "development".equalsIgnoreCase(appEnv) || "dev".equalsIgnoreCase(appEnv)
                || "development".equalsIgnoreCase(springProfiles) || "dev".equalsIgnoreCase(springProfiles)
                || "development".equalsIgnoreCase(configEnv) || "dev".equalsIgnoreCase(configEnv)) {
            return true;
        }

        return true; // Default to dev for local standalone runs
    }

    /**
     * Seeds default development accounts if they do not already exist.
     * Idempotent: checks for existence before creation.
     */
    public static void seedDevAccounts(AppUserRepository appUserRepository,
                                       StudentRepository studentRepository,
                                       PasswordEncoder passwordEncoder) {
        if (!isDevelopment()) {
            log.info("[ExamPro] Production environment detected. Skipping development seeding.");
            return;
        }

        log.info("[ExamPro] Development environment detected. Running dev seeding...");

        // 1. Admin account
        if (appUserRepository.findByEmail(DEV_ADMIN_EMAIL).isEmpty()) {
            appUserRepository.save(new AppUser(
                null, "Development Admin", DEV_ADMIN_EMAIL,
                passwordEncoder.encode(DEV_ADMIN_PASS), "ADMIN", true
            ));
        }

        // 2. Teacher account
        if (appUserRepository.findByEmail(DEV_TEACHER_EMAIL).isEmpty()) {
            appUserRepository.save(new AppUser(
                null, "Development Teacher", DEV_TEACHER_EMAIL,
                passwordEncoder.encode(DEV_TEACHER_PASS), "TEACHER", true
            ));
        }

        // 3. Student account
        if (appUserRepository.findByEmail(DEV_STUDENT_EMAIL).isEmpty()) {
            appUserRepository.save(new AppUser(
                null, "Development Student", DEV_STUDENT_EMAIL,
                passwordEncoder.encode(DEV_STUDENT_PASS), "STUDENT", true
            ));
        }

        // Ensure Student profile exists so student can take exams
        if (studentRepository.findByEmail(DEV_STUDENT_EMAIL).isEmpty()) {
            studentRepository.save(new Student(
                null, "Development Student", DEV_STUDENT_EMAIL, "STU-LOCAL-001"
            ));
        }

        printSeededCredentials();
    }

    /**
     * Reset any user's password locally.
     */
    public static boolean resetUserPassword(String email,
                                           String newPassword,
                                           AppUserRepository appUserRepository,
                                           PasswordEncoder passwordEncoder) {
        if (email == null || email.isBlank() || newPassword == null || newPassword.isBlank()) {
            System.err.println("[ExamPro Error] Email and newPassword must not be blank.");
            return false;
        }

        Optional<AppUser> userOpt = appUserRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (userOpt.isEmpty()) {
            System.err.println("[ExamPro Error] User with email '" + email + "' not found in database.");
            return false;
        }

        AppUser user = userOpt.get();
        user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        appUserRepository.save(user);

        System.out.println("======================================================================");
        System.out.println("[ExamPro] Password successfully reset!");
        System.out.println("  User:         " + user.getEmail() + " (" + user.getRole() + ")");
        System.out.println("  New Password: " + newPassword);
        System.out.println("======================================================================");
        return true;
    }

    public static void printSeededCredentials() {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("               ExamPro Development Seed Credentials                   ");
        System.out.println("======================================================================");
        System.out.println("  Role       Email                      Password");
        System.out.println("  ------------------------------------------------------------------");
        System.out.println("  Admin      " + DEV_ADMIN_EMAIL + "         " + DEV_ADMIN_PASS);
        System.out.println("  Teacher    " + DEV_TEACHER_EMAIL + "       " + DEV_TEACHER_PASS);
        System.out.println("  Student    " + DEV_STUDENT_EMAIL + "       " + DEV_STUDENT_PASS);
        System.out.println("======================================================================");
        System.out.println();
    }
}
