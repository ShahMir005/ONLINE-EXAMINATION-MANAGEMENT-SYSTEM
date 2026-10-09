package edu.exampro.repository;

import edu.exampro.model.Attempt;
import java.util.List;

/** What the submission service needs from storage. An interface, so it can be replaced in tests. */
public interface AttemptRepository {
    /** Saves the attempt and its answers atomically. Duplicate-submission policy is handled by the service. */
    Attempt save(Attempt attempt);

    boolean existsByExamAndStudent(long examId, long studentId);

    List<Attempt> findByExamId(long examId);

    java.util.Optional<Attempt> findById(long id);

    List<Attempt> findAll();
}
