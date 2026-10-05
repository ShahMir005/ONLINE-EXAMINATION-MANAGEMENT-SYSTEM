package edu.exampro.web.controller;

import edu.exampro.exception.ExamException;
import edu.exampro.model.AppUser;
import edu.exampro.model.Student;
import edu.exampro.repository.AppUserRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.web.dto.StudentForm;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StudentController {
    private final StudentRepository studentRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentController(StudentRepository studentRepository,
                             AppUserRepository appUserRepository,
                             PasswordEncoder passwordEncoder) {
        this.studentRepository = studentRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/students")
    public String listStudents(Model model) {
        List<Student> students = studentRepository.findAll();
        model.addAttribute("students", students);
        if (!model.containsAttribute("studentForm")) {
            model.addAttribute("studentForm", new StudentForm());
        }
        return "students";
    }

    @PostMapping("/students")
    public String addStudent(@ModelAttribute("studentForm") StudentForm form, RedirectAttributes redirectAttributes) {
        try {
            // Check if email already registered in students table
            if (studentRepository.findByEmail(form.getEmail()).isPresent()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                    "A student with email '" + form.getEmail() + "' already exists.");
                redirectAttributes.addFlashAttribute("studentForm", form);
                return "redirect:/students";
            }

            Student newStudent = new Student(null, form.getName(), form.getEmail(), form.getRegistrationNumber());
            Student saved = studentRepository.save(newStudent);

            // Also create AppUser authentication record if not exists
            if (appUserRepository.findByEmail(form.getEmail()).isEmpty()) {
                appUserRepository.save(new AppUser(
                    null,
                    saved.getName(),
                    saved.getEmail(),
                    passwordEncoder.encode("StudentPassword123!"),
                    "STUDENT",
                    true
                ));
            }

            redirectAttributes.addFlashAttribute("successMessage",
                "Student '" + saved.getName() + "' (ID: " + saved.getId() + ") successfully registered! (Login password: StudentPassword123!)");
        } catch (ExamException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("studentForm", form);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error registering student: " + e.getMessage());
            redirectAttributes.addFlashAttribute("studentForm", form);
        }
        return "redirect:/students";
    }

    @PostMapping("/students/{id}/delete")
    public String deleteStudent(@PathVariable("id") long id, RedirectAttributes redirectAttributes) {
        try {
            studentRepository.findById(id).ifPresent(s -> {
                appUserRepository.findByEmail(s.getEmail()).ifPresent(u -> appUserRepository.deleteById(u.getId()));
            });

            boolean deleted = studentRepository.deleteById(id);
            if (deleted) {
                redirectAttributes.addFlashAttribute("successMessage", "Student ID #" + id + " was deleted successfully.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Student ID #" + id + " was not found.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not delete student: " + e.getMessage());
        }
        return "redirect:/students";
    }
}
