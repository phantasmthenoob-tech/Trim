package com.unbound.core.enchant;

import java.util.EnumSet;
import java.util.Set;

/**
 * What an item is meaningfully capable of. Universal applicability is decided
 * from capabilities ("can this item attack?") instead of item types
 * ("is it a sword?"), so effects adapt to the item rather than being hard
 * locked to vanilla tool classes.
 */
public enum ItemCapability {
    /** Item has durability and can take durability damage. */
    DURABLE,
    /** Item is eaten or drunk through the normal consume flow. */
    CONSUMABLE,
    /** Item is consumed when thrown/launched (pearls, eggs, potions...). */
    THROWABLE,
    /** Item performs meaningful melee attacks (swords, axes, tridents, maces). */
    WEAPON_MELEE,
    /** Item shoots arrows from a draw (bow). */
    RANGED_BOW,
    /** Item is a crossbow. */
    RANGED_CROSSBOW,
    /** Item is a trident. */
    TRIDENT,
    /** Item is wearable armor. */
    ARMOR,
    /** Item is a mining-class tool (pickaxe, shovel, axe, hoe). */
    TOOL_MINING,
    /** Item is shears. */
    SHEARS,
    /** Item is a fishing rod. */
    FISHING_ROD,
    /** Item is a shield. */
    SHIELD;

    public static Set<ItemCapability> of(ItemCapability... capabilities) {
        return capabilities.length == 0 ? EnumSet.noneOf(ItemCapability.class) : EnumSet.copyOf(java.util.Arrays.asList(capabilities));
    }
}
