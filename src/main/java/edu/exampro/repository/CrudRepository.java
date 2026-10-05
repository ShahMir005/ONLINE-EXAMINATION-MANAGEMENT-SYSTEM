package edu.exampro.repository;

import edu.exampro.model.Identifiable;
import java.util.List;
import java.util.Optional;

/** Generic CRUD contract: Create/Update = save, Read = findById/findAll, Delete = deleteById. */
public interface CrudRepository<T extends Identifiable> {
    /** Inserts when the entity has no id yet, otherwise updates it. */
    T save(T entity);

    Optional<T> findById(long id);

    List<T> findAll();

    /** Returns true when a row was actually deleted. */
    boolean deleteById(long id);
}
