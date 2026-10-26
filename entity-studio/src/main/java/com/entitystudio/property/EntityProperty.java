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

    /** Short applicability scope, e.g. "Living entities" (shown in the GUI). */
    String scope();

    /** What happens when a write is rejected or fails mid-way. Never promises
     * more than the implementation delivers. */
    default String failureBehavior() {
        return "Validated before write; invalid values are rejected with nothing applied.";
    }

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

    /** Explicit support states — the GUI renders each one honestly and the
     * guarded write path only accepts {@link #writable} states. */
    enum Support {
        /** Readable and writable through the generic property editor. */
        SUPPORTED,
        /** Readable; the generic write covers only part of the effect. */
        PARTIAL,
        /** Would be supported, but is not available for this entity right now. */
        UNAVAILABLE,
        /** Readable only; no setter exists and none is pretended. */
        READ_ONLY,
        /** Needs a dedicated editor flow (confirm, snapshot, or another page). */
        SPECIAL,
        /** Not applicable — hidden from the editor entirely. */
        UNSUPPORTED;

        /** States the generic {@code apply} write path accepts. */
        public boolean writable() {
            return this == SUPPORTED || this == PARTIAL;
        }
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
