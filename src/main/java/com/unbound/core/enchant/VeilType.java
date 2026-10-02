package com.unbound.core.enchant;

/**
 * Which damage family an active veil guards against. Mirrors the four
 * vanilla protection families plus the plain-protection "all but a smaller
 * multiplier" case.
 */
public enum VeilType {
    /** Protection: small multiplier against everything. */
    ALL,
    /** Fire Protection: fire/lava/hot floor damage. */
    FIRE,
    /** Blast Protection: explosions. */
    BLAST,
    /** Projectile Protection: arrows and other projectiles. */
    PROJECTILE,
    /** Feather Falling: fall damage. */
    FALL;

    /** True when this veil reduces damage of the given cause name. */
    public boolean covers(EntityDamageCauseName cause) {
        return switch (this) {
            case ALL -> true;
            case FIRE -> cause == EntityDamageCauseName.FIRE
                    || cause == EntityDamageCauseName.FIRE_TICK
                    || cause == EntityDamageCauseName.LAVA
                    || cause == EntityDamageCauseName.HOT_FLOOR;
            case BLAST -> cause == EntityDamageCauseName.ENTITY_EXPLOSION
                    || cause == EntityDamageCauseName.BLOCK_EXPLOSION;
            case PROJECTILE -> cause == EntityDamageCauseName.PROJECTILE;
            case FALL -> cause == EntityDamageCauseName.FALL;
        };
    }

    /** Damage reduction per veil level (a fraction of the event damage). */
    public double reductionPerLevel() {
        return switch (this) {
            // Plain protection: smaller, broad. Families: stronger, narrow.
            case ALL -> 0.02;
            case FIRE, BLAST, PROJECTILE -> 0.04;
            case FALL -> 0.05; // slightly above vanilla EPF feel for feather-falling veils
        };
    }
}
