package com.unbound.core.math;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;

/**
 * Pure math for drop enchantments (Fortune, Looting).
 *
 * <p>Unbound's drop bonuses are <b>additive</b> by design: vanilla already
 * applies Fortune/Looting to any item carrying them (its checks read the held
 * item, not the item class). Unbound therefore adds an independent extra roll
 * on top of the vanilla result instead of trying to reproduce vanilla loot
 * tables.</p>
 */
public final class DropMath {

    private DropMath() {
    }

    /** Probability that one qualifying drop stack is duplicated: {@code perLevel * level}, capped at 90%. */
    public static double extraBonusChance(int level, double perLevel) {
        if (level < 1 || perLevel <= 0) {
            return 0.0;
        }
        return Math.min(0.9, perLevel * level);
    }

    /** Deterministic roll (inject a seeded Random in tests). */
    public static boolean rollDuplicate(java.util.random.RandomGenerator random, double chance) {
        if (chance <= 0) {
            return false;
        }
        if (chance >= 1) {
            return true;
        }
        return random.nextDouble() < chance;
    }

    /**
     * Whether a block qualifies for the Fortune bonus: any ore block,
     * ancient debris, or a configured extra block.
     */
    public static boolean isFortuneBlock(Material block, Set<String> extraBlocks) {
        String name = block.name().toLowerCase(Locale.ROOT);
        if (name.endsWith("_ore") || name.equals("ancient_debris")) {
            return true;
        }
        return extraBlocks.contains(name);
    }

    /**
     * Whether a dropped item qualifies for the Fortune bonus: configured
     * drops plus any raw resource (raw_iron, raw_gold, raw_copper...).
     */
    public static boolean isFortuneDrop(Material drop, Set<String> extraDrops) {
        String name = drop.name().toLowerCase(Locale.ROOT);
        if (name.startsWith("raw_")) {
            return true;
        }
        return extraDrops.contains(name);
    }
}
