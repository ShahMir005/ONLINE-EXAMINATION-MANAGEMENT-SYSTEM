package edu.exampro.model;

/** Anything that can be stored in the database and has a generated id. */
public interface Identifiable {
    Long getId();

    void setId(Long id);
}
