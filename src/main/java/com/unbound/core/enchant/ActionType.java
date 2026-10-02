package com.unbound.core.enchant;

/**
 * The kind of action an enchantment effect can react to. Handlers translate
 * Bukkit events into actions; definitions declare which actions they support.
 *
 * <p>Actions are deliberately coarse (one per event family). Fine-grained
 * decisions (which item, which target, which damage type) belong to the
 * {@link EnchantmentEffect#applies(EnchantmentContext)} / execute stage, not
 * to the action taxonomy.</p>
 */
public enum ActionType {
    /** A living entity attacked another entity with a held item (damage-modification phase). */
    MELEE_ATTACK,
    /** The melee attack actually landed (post-cancellation phase for Multishot/Punch). */
    MELEE_IMPACT,
    /** A player shot a projectile from an item (bow, trident throw...). */
    PROJECTILE_SHOOT,
    /** A player-launched projectile damaged something (damage-modification phase). */
    PROJECTILE_HIT,
    /** The projectile hit actually landed (post-cancellation phase for Punch). */
    PROJECTILE_IMPACT,
    /** A mob died with a player as killer (drop processing). */
    ENTITY_DEATH,
    /** A block was broken and produced drops. */
    BLOCK_BREAK,
    /** A player placed a block (Infinity block restore). */
    BLOCK_PLACE,
    /** A player began damaging a block (charge/use start). */
    BLOCK_DAMAGE_START,
    /** A player finished consuming food, a potion, milk, etc. */
    ITEM_CONSUME,
    /** A player launched a throwable consumable (pearl, egg, potion...). */
    ITEM_LAUNCH,
    /** A player item entered cooldown. */
    ITEM_COOLDOWN,
    /** Thorns swept nearby enemies after the wearer was hit. */
    THORNS_TRIGGER,
    /** A player right-clicked a Riptide item to self-launch. */
    RIPTIDE_LAUNCH,
    /** A player died (totem resurrection, effects cleanup). */
    PLAYER_DEATH,
    /** An item took durability damage. */
    DURABILITY_DAMAGE,
    /** A player gained experience (orbs, fishing, bottles...). */
    XP_GAIN,
    /** An enchanting table offered / applied enchantments. */
    ENCHANT_OFFER,
    /** Held item or hand changed (attribute upkeep). */
    HELD_ITEM_CHANGE,
    /** Player joined. */
    PLAYER_JOIN,
    /** Player quit. */
    PLAYER_QUIT;

    /**
     * Actions whose dispatching is not gated by a single "the" item's
     * enchantment level (attribute upkeep, XP scanning across the whole
     * inventory). Effects for these actions perform their own per-item level
     * checks; the context level may legitimately be 0.
     */
    public static final java.util.Set<ActionType> LEVEL_AGNOSTIC = java.util.Set.of(
            HELD_ITEM_CHANGE, PLAYER_JOIN, PLAYER_QUIT, XP_GAIN);
}
