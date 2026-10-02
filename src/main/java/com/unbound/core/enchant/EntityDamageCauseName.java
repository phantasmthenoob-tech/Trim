package com.unbound.core.enchant;

/**
 * Subset of Bukkit damage causes used by veils, kept as its own enum so the
 * pure veil logic stays unit-testable without a server.
 */
public enum EntityDamageCauseName {
    CONTACT, FIRE, FIRE_TICK, LAVA, HOT_FLOOR, ENTITY_EXPLOSION,
    BLOCK_EXPLOSION, PROJECTILE, FALL
}
