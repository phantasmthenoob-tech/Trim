package com.entitystudio.property;

import org.bukkit.entity.Entity;

/**
 * A typed, readable/writable entity property.
 * Display lives in the GUI; mutation lives here. Setters must never run
 * before {@link #validate} and permission checks succeed.
 */
public interface EntityProperty<T> {

    /** Stable internal id, e.g. "health", "villager.profession". */
    String id();

    String displayName();

    String description();

    PropertyType type();

    RiskLevel risk();

    /** Whether a reliable rollback exists (drives undo eligibility). */
    boolean reversible();

    /** Whether a safe setter exists for this entity (never pretend otherwise). */
    Support support(Entity entity);

    T read(Entity entity);

    ValidationResult validate(Entity entity, T value);

    void write(Entity entity, T value);

    /** Parses chat/GUI text input into a value, or null with an error message. */
    ParseResult<T> parse(String input);

    enum PropertyType {
        BOOLEAN, NUMBER, TEXT, ENUM, ENTITY_REF
    }

    enum RiskLevel {
        NORMAL, ADVANCED, DESTRUCTIVE
    }

    enum Support {
        SUPPORTED, PARTIAL, UNSUPPORTED
    }

    record ParseResult<T>(T value, String error) {
        public boolean ok() {
            return error == null;
        }

        public static <T> ParseResult<T> ok(T value) {
            return new ParseResult<>(value, null);
        }

        public static <T> ParseResult<T> fail(String error) {
            return new ParseResult<>(null, error);
        }
    }
}
