package com.entitystudio.capability;

/**
 * Typed capabilities an entity may expose. Features are discovered through
 * these — not through instanceof chains scattered across the GUI.
 */
public enum Capability {
    /** EntityEquipment slots (living entities, armor stands). */
    EQUIPMENT,
    /** Generic container inventory (chest boats, chest minecarts, hoppers...). */
    CONTAINER_INVENTORY,
    /** Horse/donkey/mule/llama storage + saddle/armor. */
    MOUNT_INVENTORY,
    /** Full player inventory + ender chest (online; special offline flow). */
    PLAYER_INVENTORY,
    /** Merchant offers (villagers, wandering traders). */
    MERCHANT_OFFERS,
    /** Native taming API (wolves, cats, horses, parrots...). */
    TAMEABLE_OWNER,
    /** Mob targeting API (hostiles, wolves, golems...). */
    MOB_TARGETING,
    /** Bukkit Attribute API (max health, speed, armor...). */
    ATTRIBUTES,
    /** Age manipulation (babies/adults, age lock). */
    AGEABLE_STATE,
    /** Potion effects. */
    EFFECTS,
    /** Plugin-managed allegiance (owner/trusted/enemies for any mob). */
    PLUGIN_ALLEGIANCE,
    /** Read-only advanced data browser (no unsafe writes). */
    ADVANCED_DATA,
}
