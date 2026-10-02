package com.unbound.core.math;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Pure math for Multishot: area radius, target selection (deduplicated,
 * distance-sorted, budget-capped), extra arrow counts and spread angles.
 *
 * <p>Target selection is generic over the candidate payload so it can be
 * unit tested without server objects; production passes entities plus a
 * position extractor.</p>
 */
public final class MultishotMath {

    private MultishotMath() {
    }

    /** A selectable target: arbitrary reference plus position. */
    public record TargetCandidate<T>(T reference, double x, double y, double z) {
    }

    /** Area attack radius for the given level. */
    public static double areaRadius(double baseRadius, double radiusPerLevel, int level) {
        if (level < 1) {
            return 0.0;
        }
        return Math.max(0.0, baseRadius + radiusPerLevel * (level - 1));
    }

    /**
     * Selects up to {@code maxTargets} candidates within {@code radius} of the
     * center, nearest first.
     *
     * <ul>
     *   <li>excludes anything in {@code excluded} (attacker, main target,
     *       already-processed entities);</li>
     *   <li>deduplicates by reference identity (an entity is never selected
     *       twice);</li>
     *   <li>ignores candidates at the center itself.</li>
     * </ul>
     */
    public static <T> List<T> selectTargets(List<TargetCandidate<T>> candidates,
                                            double centerX, double centerY, double centerZ,
                                            double radius, int maxTargets,
                                            @Nullable Set<T> excluded) {
        if (candidates == null || candidates.isEmpty() || radius <= 0 || maxTargets <= 0) {
            return List.of();
        }
        double radiusSquared = radius * radius;
        Set<T> blocked = excluded == null ? Set.of() : excluded;
        LinkedHashSet<T> unique = new LinkedHashSet<>();
        List<Picked<T>> picked = new ArrayList<>();

        for (TargetCandidate<T> candidate : candidates) {
            if (candidate.reference() == null
                    || unique.contains(candidate.reference())
                    || blocked.contains(candidate.reference())) {
                continue;
            }
            double dx = candidate.x() - centerX;
            double dy = candidate.y() - centerY;
            double dz = candidate.z() - centerZ;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > radiusSquared) {
                continue;
            }
            unique.add(candidate.reference());
            picked.add(new Picked<>(candidate.reference(), distanceSquared));
        }

        // Sort by distance then cap.
        picked.sort(Comparator.comparingDouble(Picked::distanceSquared));
        List<T> result = new ArrayList<>(Math.min(maxTargets, picked.size()));
        for (Picked<T> entry : picked) {
            if (result.size() >= maxTargets) {
                break;
            }
            result.add(entry.reference());
        }
        return result;
    }

    private record Picked<T>(T reference, double distanceSquared) {
    }

    /** Extra arrows spawned on shoot for the given level. */
    public static int extraArrowCount(int level, int maxPerLevel, int totalCap) {
        if (level < 1) {
            return 0;
        }
        return Math.min(totalCap, maxPerLevel * level);
    }

    /**
     * Direction for extra arrow {@code index} (0-based): alternates left/right
     * of the base direction by increasing multiples of {@code spreadDegrees}.
     */
    public static Vector spreadDirection(Vector baseDirection, double spreadDegrees, int index) {
        Vector clone = baseDirection.clone();
        if (index == 0 || spreadDegrees <= 0) {
            return clone;
        }
        int step = (index + 1) / 2; // 1,1,2,2,3,3...
        double radians = Math.toRadians(spreadDegrees * step);
        return clone.rotateAroundY(index % 2 == 1 ? radians : -radians);
    }
}
