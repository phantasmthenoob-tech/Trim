package com.unbound.core.capability;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.unbound.core.enchant.ItemCapability;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Classifies items into {@link ItemCapability} sets. Results are cached per
 * material (materials are immutable), so repeated scans are map lookups —
 * cheap enough for hot event paths.
 */
public final class CapabilityScanner {

    private final Map<Material, Set<ItemCapability>> cache = new EnumMap<>(Material.class);

    public Set<ItemCapability> scan(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Set.of();
        }
        return scan(item.getType());
    }

    public Set<ItemCapability> scan(Material material) {
        Set<ItemCapability> cached = cache.get(material);
        if (cached != null) {
            return cached;
        }
        Set<ItemCapability> classified = classify(material);
        cache.put(material, classified);
        return classified;
    }

    private static Set<ItemCapability> classify(Material material) {
        if (material.isAir() || !material.isItem()) {
            return Set.of();
        }
        EnumSet<ItemCapability> capabilities = EnumSet.noneOf(ItemCapability.class);
        String name = material.name().toLowerCase(Locale.ROOT);

        if (material.getMaxDurability() > 0) {
            capabilities.add(ItemCapability.DURABLE);
        }
        if (material.isEdible() || name.equals("potion") || name.equals("honey_bottle")) {
            capabilities.add(ItemCapability.CONSUMABLE);
        }
        switch (name) {
            case "ender_pearl", "egg", "snowball", "experience_bottle",
                 "splash_potion", "lingering_potion" -> capabilities.add(ItemCapability.THROWABLE);
            case "bow" -> capabilities.add(ItemCapability.RANGED_BOW);
            case "crossbow" -> capabilities.add(ItemCapability.RANGED_CROSSBOW);
            case "trident" -> {
                capabilities.add(ItemCapability.TRIDENT);
                capabilities.add(ItemCapability.WEAPON_MELEE);
            }
            case "mace" -> capabilities.add(ItemCapability.WEAPON_MELEE);
            case "shears" -> capabilities.add(ItemCapability.SHEARS);
            case "fishing_rod" -> capabilities.add(ItemCapability.FISHING_ROD);
            case "shield" -> capabilities.add(ItemCapability.SHIELD);
            default -> {
            }
        }
        if (name.endsWith("_sword") || name.endsWith("_axe")) {
            capabilities.add(ItemCapability.WEAPON_MELEE);
            if (name.endsWith("_axe")) {
                capabilities.add(ItemCapability.TOOL_MINING);
            }
        }
        if (name.endsWith("_pickaxe") || name.endsWith("_shovel") || name.endsWith("_hoe")) {
            capabilities.add(ItemCapability.TOOL_MINING);
        }
        if (name.endsWith("_helmet") || name.endsWith("_chestplate")
                || name.endsWith("_leggings") || name.endsWith("_boots")
                || name.equals("turtle_helmet") || name.equals("wolf_armor")) {
            capabilities.add(ItemCapability.ARMOR);
        }
        return capabilities.isEmpty() ? Set.of() : capabilities;
    }
}
