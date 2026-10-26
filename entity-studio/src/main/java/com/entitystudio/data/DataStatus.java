package com.entitystudio.data;

/**
 * Read status of one inspected value. The inspector never invents values:
 * anything that could not be read is shown with its status, never a guess.
 */
public enum DataStatus {
    /** Value was read through the public API and is shown (possibly truncated). */
    READABLE,
    /** The value exists but is hidden: a sensitive key or another player's data. */
    REDACTED,
    /** A public-API read was attempted and failed (unsupported entity, error). */
    UNAVAILABLE,
    /** No safe public-API read path exists for this data. */
    UNSUPPORTED,
}
