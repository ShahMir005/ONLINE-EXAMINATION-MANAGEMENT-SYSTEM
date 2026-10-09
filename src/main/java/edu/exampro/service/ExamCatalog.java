package edu.exampro.service;

import edu.exampro.exception.ValidationException;
import edu.exampro.model.Exam;
import edu.exampro.repository.ExamRepository;
import java.util.List;
import java.util.Optional;

/** Reads exams through an InMemoryCache so repeated lookups do not hit the database. */
public final class ExamCatalog {
    private final ExamRepository repository;
    private final InMemoryCache<Exam> cache = new InMemoryCache<>();

    public ExamCatalog(ExamRepository repository) {
        this.repository = repository;
    }

    public Exam create(Exam exam) {
        if (exam.getId() != null) {
            throw new ValidationException("Exam is already stored with id " + exam.getId());
        }
        if (exam.getQuestions().isEmpty()) {
            throw new ValidationException("An exam needs at least one question");
        }
        Exam stored = repository.createWithQuestions(exam);
        cache.put(stored);
        return stored;
    }

    /** Cache first, database second. Worst case under a race: two threads load the same exam once each. */
    public Optional<Exam> find(long id) {
        Optional<Exam> cached = cache.get(id);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<Exam> loaded = repository.findById(id);
        loaded.ifPresent(cache::put);
        return loaded;
    }

    public boolean isCached(long id) {
        return cache.get(id).isPresent();
    }

    public Exam update(Exam exam) {
        repository.save(exam);
        cache.put(exam);
        return exam;
    }

    public boolean delete(long id) {
        cache.remove(id);
        return repository.deleteById(id);
    }

    public List<Exam> findAll() {
        List<Exam> exams = repository.findAll();
        for (Exam exam : exams) {
            if (exam.getId() != null) {
                cache.put(exam);
            }
        }
        return exams;
    }
}
