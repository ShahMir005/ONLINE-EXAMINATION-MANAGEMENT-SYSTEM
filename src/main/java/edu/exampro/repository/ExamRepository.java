package edu.exampro.repository;

import edu.exampro.db.Database;
import edu.exampro.db.Transactions;
import edu.exampro.exception.ExamException;
import edu.exampro.exception.ValidationException;
import edu.exampro.model.Exam;
import edu.exampro.model.MultipleChoiceQuestion;
import edu.exampro.model.Question;
import edu.exampro.model.TrueFalseQuestion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC repository for exams and their questions. */
public final class ExamRepository implements CrudRepository<Exam> {
    private static final String INSERT_EXAM =
        "INSERT INTO exams(title, duration_minutes) VALUES (?, ?)";
    private static final String INSERT_QUESTION =
        "INSERT INTO questions(exam_id, question_type, prompt, marks, correct_option) VALUES (?, ?, ?, ?, ?)";
    private static final String INSERT_OPTION =
        "INSERT INTO question_options(question_id, option_index, option_text) VALUES (?, ?, ?)";
    private static final String UPDATE_EXAM =
        "UPDATE exams SET title = ?, duration_minutes = ? WHERE id = ?";
    private static final String SELECT_EXAM =
        "SELECT title, duration_minutes FROM exams WHERE id = ?";
    private static final String SELECT_EXAM_IDS = "SELECT id FROM exams ORDER BY id";
    private static final String SELECT_QUESTIONS =
        "SELECT id, question_type, prompt, marks, correct_option FROM questions WHERE exam_id = ? ORDER BY id";
    private static final String SELECT_OPTIONS =
        "SELECT o.question_id, o.option_text FROM question_options o "
            + "JOIN questions q ON q.id = o.question_id "
            + "WHERE q.exam_id = ? ORDER BY o.question_id, o.option_index";
    private static final String DELETE_EXAM = "DELETE FROM exams WHERE id = ?";

    /** The ids the database generated; applied to the objects only after the commit succeeded. */
    private record StoredIds(long examId, List<Long> questionIds) { }

    /**
     * One transaction: the exam, all its questions and all their options are saved together,
     * or nothing is saved at all.
     */
    public Exam createWithQuestions(Exam exam) {
        if (exam.getId() != null) {
            throw new ValidationException("Exam is already stored with id " + exam.getId());
        }
        if (exam.getQuestions().isEmpty()) {
            throw new ValidationException("An exam needs at least one question");
        }
        StoredIds ids = Transactions.run("Could not create exam", connection -> {
            long examId = insertExam(connection, exam);
            List<Long> questionIds = new ArrayList<>();
            for (Question<?> question : exam.getQuestions()) {
                questionIds.add(insertQuestion(connection, examId, question));
            }
            return new StoredIds(examId, questionIds);
        });
        exam.setId(ids.examId());
        for (int i = 0; i < ids.questionIds().size(); i++) {
            exam.getQuestions().get(i).setId(ids.questionIds().get(i));
        }
        return exam;
    }

    /** New exams are created with their questions; for stored exams only title and duration are updated. */
    @Override
    public Exam save(Exam exam) {
        if (exam.getId() == null) {
            return createWithQuestions(exam);
        }
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_EXAM)) {
            statement.setString(1, exam.getTitle());
            statement.setInt(2, Math.toIntExact(exam.getDuration().toMinutes()));
            statement.setLong(3, exam.getId());
            if (statement.executeUpdate() == 0) {
                throw new ExamException("Cannot update: no exam with id " + exam.getId());
            }
            return exam;
        } catch (SQLException exception) {
            throw new ExamException("Could not update exam", exception);
        }
    }

    @Override
    public Optional<Exam> findById(long id) {
        try (Connection connection = Database.connection()) {
            return load(connection, id);
        } catch (SQLException exception) {
            throw new ExamException("Could not read exam", exception);
        }
    }

    @Override
    public List<Exam> findAll() {
        try (Connection connection = Database.connection()) {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_EXAM_IDS);
                 ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    ids.add(row.getLong(1));
                }
            }
            List<Exam> exams = new ArrayList<>();
            for (long id : ids) {
                load(connection, id).ifPresent(exams::add);
            }
            return exams;
        } catch (SQLException exception) {
            throw new ExamException("Could not list exams", exception);
        }
    }

    /** Questions, options and attempts of the exam are removed too (ON DELETE CASCADE). */
    @Override
    public boolean deleteById(long id) {
        try (Connection connection = Database.connection();
             PreparedStatement statement = connection.prepareStatement(DELETE_EXAM)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new ExamException("Could not delete exam", exception);
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private static long insertExam(Connection connection, Exam exam) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_EXAM, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, exam.getTitle());
            statement.setInt(2, Math.toIntExact(exam.getDuration().toMinutes()));
            statement.executeUpdate();
            return Database.generatedKey(statement);
        }
    }

    private static long insertQuestion(Connection connection, long examId, Question<?> question) throws SQLException {
        long questionId;
        try (PreparedStatement statement = connection.prepareStatement(INSERT_QUESTION, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, examId);
            statement.setString(2, question.getType());
            statement.setString(3, question.getPrompt());
            statement.setInt(4, question.getMarks());
            statement.setInt(5, question.getCorrectOptionIndex());
            statement.executeUpdate();
            questionId = Database.generatedKey(statement);
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_OPTION)) {
            List<String> choices = question.choices();
            for (int index = 0; index < choices.size(); index++) {
                statement.setLong(1, questionId);
                statement.setInt(2, index);
                statement.setString(3, choices.get(index));
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return questionId;
    }

    private static Optional<Exam> load(Connection connection, long id) throws SQLException {
        Exam exam;
        try (PreparedStatement statement = connection.prepareStatement(SELECT_EXAM)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                exam = new Exam(id, row.getString("title"), Duration.ofMinutes(row.getInt("duration_minutes")));
            }
        }
        Map<Long, List<String>> optionsByQuestion = loadOptions(connection, id);
        try (PreparedStatement statement = connection.prepareStatement(SELECT_QUESTIONS)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    long questionId = row.getLong("id");
                    exam.addQuestion(buildQuestion(
                        questionId,
                        row.getString("question_type"),
                        row.getString("prompt"),
                        row.getInt("marks"),
                        row.getInt("correct_option"),
                        optionsByQuestion.getOrDefault(questionId, List.of())));
                }
            }
        }
        return Optional.of(exam);
    }

    private static Map<Long, List<String>> loadOptions(Connection connection, long examId) throws SQLException {
        Map<Long, List<String>> options = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_OPTIONS)) {
            statement.setLong(1, examId);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    options.computeIfAbsent(row.getLong("question_id"), key -> new ArrayList<>())
                        .add(row.getString("option_text"));
                }
            }
        }
        return options;
    }

    /** Factory: turns a database row back into the right Question subclass. */
    private static Question<?> buildQuestion(
            long id, String type, String prompt, int marks, int correctOption, List<String> options) {
        return switch (type) {
            case "MCQ" -> new MultipleChoiceQuestion(id, prompt, marks, options, correctOption);
            case "TF" -> new TrueFalseQuestion(id, prompt, marks, correctOption == 0);
            default -> throw new ExamException("Unknown question type in database: " + type);
        };
    }
}
