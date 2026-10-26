package com.entitystudio.property;

/** Pure validation helpers shared by properties. Bukkit-free and unit tested. */
public final class Validators {

    private Validators() {
    }

    public static ValidationResult range(double value, double min, double max, String what) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return ValidationResult.fail(what + " must be a finite number.");
        }
        if (value < min || value > max) {
            return ValidationResult.fail(what + " must be between " + min + " and " + max + ".");
        }
        return ValidationResult.valid();
    }

    public static ValidationResult range(int value, int min, int max, String what) {
        if (value < min || value > max) {
            return ValidationResult.fail(what + " must be between " + min + " and " + max + ".");
        }
        return ValidationResult.valid();
    }

    public static ValidationResult nonBlank(String value, String what) {
        if (value == null || value.isBlank()) {
            return ValidationResult.fail(what + " must not be blank.");
        }
        return ValidationResult.valid();
    }

    public static ValidationResult maxLength(String value, int max, String what) {
        if (value != null && value.length() > max) {
            return ValidationResult.fail(what + " must be at most " + max + " characters.");
        }
        return ValidationResult.valid();
    }

    /** Parses a boolean accepting true/false/on/off/yes/no/1/0. */
    public static EntityProperty.ParseResult<Boolean> parseBoolean(String input) {
        if (input == null) {
            return EntityProperty.ParseResult.fail("Expected true or false.");
        }
        String s = input.trim().toLowerCase();
        return switch (s) {
            case "true", "on", "yes", "1" -> EntityProperty.ParseResult.ok(true);
            case "false", "off", "no", "0" -> EntityProperty.ParseResult.ok(false);
            default -> EntityProperty.ParseResult.fail("Expected true or false, got '" + input.trim() + "'.");
        };
    }

    public static EntityProperty.ParseResult<Double> parseDouble(String input, double min, double max, String what) {
        double v;
        try {
            v = Double.parseDouble(input.trim());
        } catch (NumberFormatException e) {
            return EntityProperty.ParseResult.fail("Expected a number for " + what + ".");
        }
        ValidationResult r = range(v, min, max, what);
        return r.ok() ? EntityProperty.ParseResult.ok(v) : EntityProperty.ParseResult.fail(r.message());
    }

    /** Parses any finite double (coordinates, angles). Rejects NaN/Infinity,
     * which {@link Double#parseDouble} would otherwise accept. */
    public static EntityProperty.ParseResult<Double> parseFiniteDouble(String input, String what) {
        double v;
        try {
            v = Double.parseDouble(input.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return EntityProperty.ParseResult.fail("Expected a number for " + what + ".");
        }
        if (!Double.isFinite(v)) {
            return EntityProperty.ParseResult.fail(what + " must be a finite number.");
        }
        return EntityProperty.ParseResult.ok(v);
    }

    public static EntityProperty.ParseResult<Integer> parseInt(String input, int min, int max, String what) {
        int v;
        try {
            v = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return EntityProperty.ParseResult.fail("Expected a whole number for " + what + ".");
        }
        ValidationResult r = range(v, min, max, what);
        return r.ok() ? EntityProperty.ParseResult.ok(v) : EntityProperty.ParseResult.fail(r.message());
    }
}
