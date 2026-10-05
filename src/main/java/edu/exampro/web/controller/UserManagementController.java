package edu.exampro.web.controller;

import edu.exampro.model.AppUser;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.web.dto.ChangePasswordForm;
import edu.exampro.web.dto.CreateUserForm;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class UserManagementController {

    private final AppUserRepository appUserRepository;
    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementController(AppUserRepository appUserRepository,
                                  StudentRepository studentRepository,
                                  PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String listUsers(Model model) {
        List<AppUser> users = appUserRepository.findAll();

        long adminCount = users.stream().filter(u -> "ADMIN".equalsIgnoreCase(u.getRole())).count();
        long teacherCount = users.stream().filter(u -> "TEACHER".equalsIgnoreCase(u.getRole())).count();
        long studentCount = users.stream().filter(u -> "STUDENT".equalsIgnoreCase(u.getRole())).count();

        model.addAttribute("users", users);
        model.addAttribute("totalUsers", users.size());
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("teacherCount", teacherCount);
        model.addAttribute("studentCount", studentCount);

        if (!model.containsAttribute("createForm")) {
            model.addAttribute("createForm", new CreateUserForm());
        }
        if (!model.containsAttribute("changePasswordForm")) {
            model.addAttribute("changePasswordForm", new ChangePasswordForm());
        }

        return "admin-users";
    }

    @PostMapping
    public String createUser(@ModelAttribute("createForm") CreateUserForm form,
                             RedirectAttributes redirectAttributes) {
        String name = form.getFullName() != null ? form.getFullName().trim() : "";
        String email = form.getEmail() != null ? form.getEmail().trim().toLowerCase(Locale.ROOT) : "";
        String role = form.getRole() != null ? form.getRole().trim().toUpperCase(Locale.ROOT) : "STUDENT";
        String password = form.getPassword() != null ? form.getPassword().trim() : "";

        // Form validations
        if (name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Full name cannot be blank.");
            redirectAttributes.addFlashAttribute("createForm", form);
            return "redirect:/admin/users";
        }

        if (email.isBlank() || !email.contains("@")) {
            redirectAttributes.addFlashAttribute("errorMessage", "A valid email address containing '@' is required.");
            redirectAttributes.addFlashAttribute("createForm", form);
            return "redirect:/admin/users";
        }

        if (!List.of("ADMIN", "TEACHER", "STUDENT").contains(role)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Role must be one of: ADMIN, TEACHER, or STUDENT.");
            redirectAttributes.addFlashAttribute("createForm", form);
            return "redirect:/admin/users";
        }

        if (password.length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage", "Password must be at least 6 characters long.");
            redirectAttributes.addFlashAttribute("createForm", form);
            return "redirect:/admin/users";
        }

        // Duplicate email validation in app_users
        if (appUserRepository.findByEmail(email).isPresent()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Validation Error: An account with email address '" + email + "' already exists!");
            redirectAttributes.addFlashAttribute("createForm", form);
            return "redirect:/admin/users";
        }

        // If role is STUDENT, also verify no duplicate in students table, then create Student record
        if ("STUDENT".equals(role)) {
            if (studentRepository.findByEmail(email).isPresent()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "Validation Error: A student with email '" + email + "' already exists in the student roster!");
                redirectAttributes.addFlashAttribute("createForm", form);
                return "redirect:/admin/users";
            }

            String reg = (form.getRegistrationNumber() != null && !form.getRegistrationNumber().isBlank())
                ? form.getRegistrationNumber().trim()
                : "STU-" + (System.currentTimeMillis() % 100000);

            studentRepository.save(new Student(null, name, email, reg));
        }

        // Always hash password with BCrypt - NEVER store plaintext passwords
        String passwordHash = passwordEncoder.encode(password);
        AppUser newUser = new AppUser(null, name, email, passwordHash, role, true);
        appUserRepository.save(newUser);

        redirectAttributes.addFlashAttribute("successMessage",
            "User account for '" + name + "' (" + role + ") successfully created with secure BCrypt hashing!");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/change-password")
    public String changePassword(@PathVariable("id") long id,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        Optional<AppUser> userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "User ID #" + id + " not found.");
            return "redirect:/admin/users";
        }

        AppUser user = userOpt.get();

        if (newPassword == null || newPassword.trim().length() < 6) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Password change failed: New password must be at least 6 characters long.");
            return "redirect:/admin/users";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Password change failed: Passwords do not match.");
            return "redirect:/admin/users";
        }

        // Store new password with BCrypt hash
        user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        appUserRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage",
            "Password securely updated with BCrypt hashing for account: " + user.getEmail());
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/toggle-status")
    public String toggleStatus(@PathVariable("id") long id,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        Optional<AppUser> userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "User ID #" + id + " not found.");
            return "redirect:/admin/users";
        }

        AppUser user = userOpt.get();
        if (auth != null && auth.getName().equalsIgnoreCase(user.getEmail())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Security policy: You cannot disable your own active administrator account.");
            return "redirect:/admin/users";
        }

        user.setEnabled(!user.isEnabled());
        appUserRepository.save(user);

        redirectAttributes.addFlashAttribute("successMessage",
            "Account " + user.getEmail() + " is now " + (user.isEnabled() ? "ACTIVE" : "DISABLED") + ".");
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable("id") long id,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        Optional<AppUser> userOpt = appUserRepository.findById(id);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "User ID #" + id + " not found.");
            return "redirect:/admin/users";
        }

        AppUser user = userOpt.get();
        if (auth != null && auth.getName().equalsIgnoreCase(user.getEmail())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                "Security policy: You cannot delete your own active administrator account.");
            return "redirect:/admin/users";
        }

        // If it was a student, also clean up student record if exists
        if ("STUDENT".equalsIgnoreCase(user.getRole())) {
            studentRepository.findByEmail(user.getEmail()).ifPresent(s -> studentRepository.deleteById(s.getId()));
        }

        appUserRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage",
            "User account '" + user.getEmail() + "' (ID: #" + id + ") has been deleted.");
        return "redirect:/admin/users";
    }
}
