package com.entitystudio.data;

/**
 * One display row of the read-only advanced-data inspector.
 *
 * <p>Values are pre-truncated and pre-redacted at build time: renderers must
 * never see hidden data, so a REDACTED field carries no secret in {@code value}.
 */
public record DataField(
        /** Section id: identity, tags, pdc, or snbt. */
        String section,
        /** Stable dotted path, e.g. {@code snbt.Health} or {@code pdc:my:claims}. */
        String path,
        /** Human type label: string, int, long, float, double, boolean, byte,
         * short, int[], long[], byte[], compound, list, ... */
        String type,
        /** Display text (already truncated/redacted). Never a secret. */
        String value,
        DataStatus status,
        /** Where the value came from. Always {@link #PUBLIC_API}: no
         * version-specific internals are used anywhere in this inspector. */
        String source) {

    public static final String PUBLIC_API = "public-api";

    public boolean readable() {
        return status == DataStatus.READABLE;
    }
}
