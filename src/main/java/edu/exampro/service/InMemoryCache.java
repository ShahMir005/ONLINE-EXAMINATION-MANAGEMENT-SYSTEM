package edu.exampro.service;

import edu.exampro.exception.ValidationException;
import edu.exampro.model.Identifiable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reusable, type-safe cache (generics) backed by a thread-safe Map. */
public final class InMemoryCache<T extends Identifiable> {
    private final Map<Long, T> entries = new ConcurrentHashMap<>();

    public void put(T item) {
        if (item.getId() == null) {
            throw new ValidationException("Only stored items (with an id) can be cached");
        }
        entries.put(item.getId(), item);
    }

    public Optional<T> get(long id) {
        return Optional.ofNullable(entries.get(id));
    }

    public void remove(long id) {
        entries.remove(id);
    }

    /** A snapshot copy, so callers cannot modify the cache by accident. */
    public Collection<T> values() {
        return List.copyOf(entries.values());
    }

    public int size() {
        return entries.size();
    }
}
