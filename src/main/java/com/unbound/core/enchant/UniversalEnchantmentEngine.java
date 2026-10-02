package com.unbound.core.enchant;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of {@link EnchantmentDefinition}s plus the item/level plumbing
 * shared by handlers, effects and commands.
 *
 * <p>The engine knows nothing about individual enchantments — definitions are
 * registered by {@code VanillaDefinitions} at startup and on reload, so new
 * behaviors are added by writing a definition + effect, never by editing the
 * core.</p>
 */
public final class UniversalEnchantmentEngine {

    private final Logger logger;
    private final Map<String, EnchantmentDefinition> definitions = new LinkedHashMap<>();

    public UniversalEnchantmentEngine(Logger logger) {
        this.logger = logger;
    }

    public void register(EnchantmentDefinition definition) {
        definitions.put(definition.key(), definition);
    }

    public boolean unregister(String key) {
        return definitions.remove(key) != null;
    }

    public void clear() {
        definitions.clear();
    }

    public Collection<EnchantmentDefinition> definitions() {
        return java.util.Collections.unmodifiableCollection(definitions.values());
    }

    public Optional<EnchantmentDefinition> get(String key) {
        return Optional.ofNullable(definitions.get(key.toLowerCase(java.util.Locale.ROOT)));
    }

    /** Number of registered definitions. */
    public int size() {
        return definitions.size();
    }

    /**
     * Reads the enchantment level on the item for the given definition,
     * clamped to {@code 0..definition.maxLevel()}. Returns 0 when absent.
     */
    public int levelOf(ItemStack item, EnchantmentDefinition definition) {
        if (item == null || item.getType().isAir() || definition.handle() == null) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        int level = meta.getEnchantLevel(definition.handle());
        return clampLevel(level, definition);
    }

    /** Clamps a raw level to the definition's configured maximum. */
    public static int clampLevel(int level, EnchantmentDefinition definition) {
        if (level <= 0) {
            return 0;
        }
        return Math.min(level, definition.maxLevel());
    }

    /** True when the item carries the definition's enchantment at any level. */
    public boolean hasEnchantment(ItemStack item, EnchantmentDefinition definition) {
        return levelOf(item, definition) > 0;
    }

    /**
     * Applies the definition's enchantment to the item, bypassing vanilla
     * compatibility (that is the whole point of Unbound). Returns the level
     * actually applied, or 0 on failure.
     */
    public int applyToItem(ItemStack item, EnchantmentDefinition definition, int requestedLevel, boolean bypassMax) {
        Enchantment handle = definition.handle();
        if (item == null || item.getType().isAir() || handle == null) {
            return 0;
        }
        int level = requestedLevel <= 0 ? 1 : (bypassMax ? Math.min(requestedLevel, 255) : clampLevel(requestedLevel, definition));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        if (!meta.addEnchant(handle, level, true)) {
            return 0;
        }
        item.setItemMeta(meta);
        return level;
    }

    /** Logs a warning once per unknown key lookup (helper for commands). */
    public void warnUnknown(String key) {
        logger.warning(() -> "[Unbound] Unknown enchantment key: " + key);
    }

    @Nullable
    public EnchantmentDefinition findForHandle(Enchantment handle) {
        for (EnchantmentDefinition definition : definitions.values()) {
            if (definition.handle() == handle) {
                return definition;
            }
        }
        return null;
    }
}
