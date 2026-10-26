package com.entitystudio.property;

/** Outcome of validating a proposed property value. Pure and unit tested. */
public record ValidationResult(boolean ok, String message) {
    public static ValidationResult valid() {
        return new ValidationResult(true, "");
    }

    public static ValidationResult fail(String message) {
        return new ValidationResult(false, message);
    }
}
