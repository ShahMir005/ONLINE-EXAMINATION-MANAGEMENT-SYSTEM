package edu.exampro.web.controller;

import edu.exampro.model.Attempt;
import edu.exampro.model.Exam;
import edu.exampro.model.Question;
import edu.exampro.model.Student;
import edu.exampro.repository.AttemptRepository;
import edu.exampro.repository.StudentRepository;
import edu.exampro.service.ExamCatalog;
import edu.exampro.web.dto.AttemptViewDto;
import edu.exampro.web.dto.QuestionResultDto;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ResultsController {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final AttemptRepository attemptRepository;
    private final StudentRepository studentRepository;
    private final ExamCatalog examCatalog;

    public ResultsController(AttemptRepository attemptRepository,
                             StudentRepository studentRepository,
                             ExamCatalog examCatalog) {
        this.attemptRepository = attemptRepository;
        this.studentRepository = studentRepository;
        this.examCatalog = examCatalog;
    }

    @GetMapping("/results")
    public String viewResults(@RequestParam(value = "examId", required = false) Long filterExamId,
                              Authentication auth,
                              Model model) {
        List<Exam> allExams = examCatalog.findAll();
        List<Student> allStudents = studentRepository.findAll();

        Map<Long, Exam> examMap = allExams.stream()
            .collect(Collectors.toMap(Exam::getId, e -> e, (e1, e2) -> e1));
        Map<Long, Student> studentMap = allStudents.stream()
            .collect(Collectors.toMap(Student::getId, s -> s, (s1, s2) -> s1));

        boolean isStudent = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        Student currentStudent = isStudent ? studentRepository.findByEmail(auth.getName()).orElse(null) : null;

        List<Attempt> rawAttempts = (filterExamId != null && filterExamId > 0)
            ? attemptRepository.findByExamId(filterExamId)
            : attemptRepository.findAll();

        // If student, filter attempts to only their own
        if (isStudent && currentStudent != null) {
            final long myId = currentStudent.getId();
            rawAttempts = rawAttempts.stream()
                .filter(a -> a.getStudentId() == myId)
                .toList();
        }

        List<AttemptViewDto> dtos = new ArrayList<>();
        int passedCount = 0;
        int totalScoreAccum = 0;
        int maxScoreSeen = 0;

        for (Attempt attempt : rawAttempts) {
            Exam exam = examMap.get(attempt.getExamId());
            if (exam == null) {
                exam = examCatalog.find(attempt.getExamId()).orElse(null);
            }
            Student student = studentMap.get(attempt.getStudentId());
            if (student == null) {
                student = studentRepository.findById(attempt.getStudentId()).orElse(null);
            }

            AttemptViewDto dto = new AttemptViewDto();
            dto.setAttemptId(attempt.getId());
            dto.setExamId(attempt.getExamId());
            dto.setExamTitle(exam != null ? exam.getTitle() : "Exam #" + attempt.getExamId());
            dto.setStudentId(attempt.getStudentId());
            dto.setStudentName(student != null ? student.getName() : "Student #" + attempt.getStudentId());
            dto.setStudentEmail(student != null ? student.getEmail() : "");
            dto.setStudentRegistration(student != null ? student.getRegistrationNumber() : "");
            dto.setScore(attempt.getScore());
            int totalMarks = exam != null ? exam.totalMarks() : 0;
            dto.setTotalMarks(totalMarks);
            int pct = totalMarks > 0 ? (int) Math.round((double) attempt.getScore() * 100 / totalMarks) : 0;
            dto.setPercentage(pct);
            boolean passed = totalMarks > 0 && attempt.getScore() >= (totalMarks / 2);
            dto.setPassed(passed);
            if (passed) passedCount++;
            totalScoreAccum += pct;
            if (attempt.getScore() > maxScoreSeen) maxScoreSeen = attempt.getScore();
            dto.setSubmittedAtFormatted(FORMATTER.format(attempt.getSubmittedAt()));
            dtos.add(dto);
        }

        int avgPct = dtos.isEmpty() ? 0 : Math.round((float) totalScoreAccum / dtos.size());
        int passRate = dtos.isEmpty() ? 0 : Math.round((float) passedCount * 100 / dtos.size());

        model.addAttribute("results", dtos);
        model.addAttribute("exams", allExams);
        model.addAttribute("selectedExamId", filterExamId);
        model.addAttribute("totalAttempts", dtos.size());
        model.addAttribute("avgPercentage", avgPct);
        model.addAttribute("maxScore", maxScoreSeen);
        model.addAttribute("passRate", passRate);
        model.addAttribute("isStudentRole", isStudent);

        return "results";
    }

    @GetMapping("/attempts/{id}")
    public String scorecard(@PathVariable("id") long id,
                            Authentication auth,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        Attempt attempt = attemptRepository.findById(id).orElse(null);
        if (attempt == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Attempt #" + id + " not found.");
            return "redirect:/results";
        }

        // Student authorization rule: A student can view only their own scorecards
        boolean isStudent = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        if (isStudent) {
            Student currentStudent = studentRepository.findByEmail(auth.getName()).orElse(null);
            if (currentStudent == null || attempt.getStudentId() != currentStudent.getId()) {
                throw new AccessDeniedException("Access denied: You are not authorized to view another student's scorecard.");
            }
        }

        Exam exam = examCatalog.find(attempt.getExamId()).orElse(null);
        Student student = studentRepository.findById(attempt.getStudentId()).orElse(null);

        int totalMarks = exam != null ? exam.totalMarks() : 0;
        int percentage = totalMarks > 0 ? (int) Math.round((double) attempt.getScore() * 100 / totalMarks) : 0;
        boolean passed = totalMarks > 0 && attempt.getScore() >= (totalMarks / 2);

        List<QuestionResultDto> questionResults = new ArrayList<>();
        if (exam != null) {
            int qNum = 1;
            for (Question<?> q : exam.getQuestions()) {
                QuestionResultDto qDto = new QuestionResultDto();
                qDto.setQuestionNumber(qNum++);
                qDto.setPrompt(q.getPrompt());
                qDto.setType(q.getType());
                qDto.setMarks(q.getMarks());

                Integer chosenOpt = attempt.getAnswers().get(q.getId());
                qDto.setSelectedOption(chosenOpt);

                List<String> choices = q.choices();
                int correctOpt = q.getCorrectOptionIndex();
                qDto.setCorrectOption(correctOpt);
                qDto.setCorrectText(correctOpt >= 0 && correctOpt < choices.size()
                    ? choices.get(correctOpt) : "Option " + correctOpt);

                if (chosenOpt != null && chosenOpt >= 0 && chosenOpt < choices.size()) {
                    qDto.setSelectedText(choices.get(chosenOpt));
                } else if (chosenOpt != null) {
                    qDto.setSelectedText("Option " + chosenOpt);
                } else {
                    qDto.setSelectedText("Unanswered");
                }

                boolean isCorrect = chosenOpt != null && q.isCorrectOption(chosenOpt);
                qDto.setCorrect(isCorrect);
                qDto.setAwardedMarks(isCorrect ? q.getMarks() : 0);

                questionResults.add(qDto);
            }
        }

        model.addAttribute("attempt", attempt);
        model.addAttribute("exam", exam);
        model.addAttribute("student", student);
        model.addAttribute("totalMarks", totalMarks);
        model.addAttribute("percentage", percentage);
        model.addAttribute("passed", passed);
        model.addAttribute("submittedAtFormatted", FORMATTER.format(attempt.getSubmittedAt()));
        model.addAttribute("questionResults", questionResults);

        return "scorecard";
    }
}
