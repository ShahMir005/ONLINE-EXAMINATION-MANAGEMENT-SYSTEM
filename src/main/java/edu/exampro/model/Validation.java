package edu.exampro.model;

import edu.exampro.exception.ValidationException;

/** Small helpers so every model class validates its input the same way. */
final class Validation {
    private Validation() { }

    static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(label + " is required");
        }
        return value.trim();
    }

    static int requirePositive(int value, String label) {
        if (value <= 0) {
            throw new ValidationException(label + " must be greater than zero");
        }
        return value;
    }

    static <T> T requireNonNull(T value, String label) {
        if (value == null) {
            throw new ValidationException(label + " is required");
        }
        return value;
    }
}
