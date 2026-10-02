package com.unbound.core.math;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultishotMathTest {

    @Test
    void areaRadiusScalesWithLevel() {
        assertEquals(0.0, MultishotMath.areaRadius(2.5, 1.0, 0));
        assertEquals(2.5, MultishotMath.areaRadius(2.5, 1.0, 1));
        assertEquals(3.5, MultishotMath.areaRadius(2.5, 1.0, 2));
        assertEquals(11.5, MultishotMath.areaRadius(2.5, 1.0, 10));
    }

    @Test
    void extraArrowCountIsCapped() {
        assertEquals(0, MultishotMath.extraArrowCount(0, 1, 8));
        assertEquals(1, MultishotMath.extraArrowCount(1, 1, 8));
        assertEquals(8, MultishotMath.extraArrowCount(10, 1, 8));
        assertEquals(6, MultishotMath.extraArrowCount(3, 2, 6));
    }

    @Test
    void selectTargetsDeduplicatesAndExcludes() {
        List<MultishotMath.TargetCandidate<String>> candidates = List.of(
                new MultishotMath.TargetCandidate<>("a", 1, 0, 0),
                new MultishotMath.TargetCandidate<>("a", 1, 0, 0), // duplicate
                new MultishotMath.TargetCandidate<>("b", 2, 0, 0),
                new MultishotMath.TargetCandidate<>("c", 0.5, 0, 0));

        List<String> selected = MultishotMath.selectTargets(candidates, 0, 0, 0, 5, 16, Set.of("b"));
        assertEquals(List.of("c", "a"), selected); // "b" excluded, dup "a" once, nearest first
    }

    @Test
    void selectTargetsRespectsRadiusAndMaxTargets() {
        List<MultishotMath.TargetCandidate<String>> candidates = List.of(
                new MultishotMath.TargetCandidate<>("near", 1, 0, 0),
                new MultishotMath.TargetCandidate<>("mid", 3, 0, 0),
                new MultishotMath.TargetCandidate<>("far", 10, 0, 0));

        assertEquals(List.of("near", "mid"),
                MultishotMath.selectTargets(candidates, 0, 0, 0, 5, 16, Set.of()));
        assertEquals(List.of("near"),
                MultishotMath.selectTargets(candidates, 0, 0, 0, 5, 1, Set.of()));
        // Radius 0 selects nothing (no area attack).
        assertTrue(MultishotMath.selectTargets(candidates, 0, 0, 0, 0, 16, Set.of()).isEmpty());
    }

    @Test
    void selectTargetsNeverReturnsMoreThanRequested() {
        List<MultishotMath.TargetCandidate<Integer>> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            candidates.add(new MultishotMath.TargetCandidate<>(i, i, 0, 0));
        }
        List<Integer> selected = MultishotMath.selectTargets(candidates, 0, 0, 0, 100, 16, new HashSet<>());
        assertEquals(16, selected.size());
        assertEquals(16, selected.stream().distinct().count());
        assertEquals(0, selected.get(0)); // nearest first
    }

    @Test
    void spreadDirectionAlternatesSides() {
        Vector base = new Vector(0, 0, 1); // pointing +z
        Vector left = MultishotMath.spreadDirection(base, 10, 1); // first extra -> +? side
        Vector right = MultishotMath.spreadDirection(base, 10, 2); // mirrored
        // Mirror symmetry around the base direction:
        assertEquals(-left.getX(), right.getX(), 1e-9);
        assertEquals(left.getZ(), right.getZ(), 1e-9);
        assertTrue(left.getX() != 0);
        // index 0 keeps the base direction
        Vector center = MultishotMath.spreadDirection(base, 10, 0);
        assertEquals(base.getX(), center.getX(), 1e-9);
        assertEquals(base.getZ(), center.getZ(), 1e-9);
        // Second pair is wider than the first.
        double first = Math.abs(MultishotMath.spreadDirection(base, 10, 1).getX());
        double second = Math.abs(MultishotMath.spreadDirection(base, 10, 3).getX());
        assertTrue(second > first);
    }
}
