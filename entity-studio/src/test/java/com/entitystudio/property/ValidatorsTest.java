package com.entitystudio.property;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorsTest {

    @Test
    void rangeChecks() {
        assertTrue(Validators.range(5, 0, 10, "x").ok());
        assertTrue(Validators.range(0, 0, 10, "x").ok());
        assertTrue(Validators.range(10, 0, 10, "x").ok());
        assertFalse(Validators.range(-1, 0, 10, "x").ok());
        assertFalse(Validators.range(11, 0, 10, "x").ok());
        assertFalse(Validators.range(Double.NaN, 0, 10, "x").ok());
    }

    @Test
    void booleanParsing() {
        assertEquals(true, Validators.parseBoolean("true").value());
        assertEquals(true, Validators.parseBoolean("ON").value());
        assertEquals(false, Validators.parseBoolean("off").value());
        assertFalse(Validators.parseBoolean("maybe").ok());
    }

    @Test
    void numberParsingRespectsBounds() {
        assertEquals(5, Validators.parseInt("5", 0, 10, "x").value());
        assertFalse(Validators.parseInt("99", 0, 10, "x").ok());
        assertFalse(Validators.parseInt("abc", 0, 10, "x").ok());
        assertEquals(2.5, Validators.parseDouble("2.5", 0, 5, "x").value());
        assertFalse(Validators.parseDouble("9.9", 0, 5, "x").ok());
    }

    @Test
    void nonFiniteDoublesRejected() {
        assertFalse(Validators.range(Double.POSITIVE_INFINITY, 0, 10, "x").ok());
        assertFalse(Validators.range(Double.NEGATIVE_INFINITY, 0, 10, "x").ok());
        assertFalse(Validators.parseDouble("NaN", 0, 5, "x").ok());
        assertFalse(Validators.parseDouble("Infinity", 0, Double.MAX_VALUE, "x").ok());
        assertFalse(Validators.parseDouble("-5", 0, Double.MAX_VALUE, "x").ok());
        assertTrue(Validators.parseDouble("0", 0, Double.MAX_VALUE, "x").ok());
    }

    @Test
    void finiteDoubleParsing() {
        assertEquals(1.5, Validators.parseFiniteDouble("1.5", "x").value());
        assertEquals(-30.0, Validators.parseFiniteDouble("-30", "x").value());
        assertFalse(Validators.parseFiniteDouble("NaN", "x").ok());
        assertFalse(Validators.parseFiniteDouble("Infinity", "x").ok());
        assertFalse(Validators.parseFiniteDouble("-Infinity", "x").ok());
        assertFalse(Validators.parseFiniteDouble("abc", "x").ok());
        assertFalse(Validators.parseFiniteDouble(null, "x").ok());
    }

    @Test
    void strings() {
        assertFalse(Validators.nonBlank("  ", "name").ok());
        assertTrue(Validators.nonBlank("Bob", "name").ok());
        assertFalse(Validators.maxLength("toolong", 3, "name").ok());
        assertTrue(Validators.maxLength("ok", 3, "name").ok());
    }
}
