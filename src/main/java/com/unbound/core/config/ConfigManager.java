package com.unbound.core.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Bukkit-facing config owner: saves the default config.yml on first run,
 * reads it into a plain nested map (with bundled defaults applied for missing
 * keys), and hands that map to the pure {@link ConfigParser}. Reload replaces
 * the immutable {@link UnboundConfig} reference — consumers read the current
 * value through {@link #current()}.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;
    private final Logger logger;
    private volatile UnboundConfig current;

    public ConfigManager(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /** Loads (or reloads) config.yml. Returns the new config. */
    public UnboundConfig load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration raw = plugin.getConfig();
        // JavaPlugin.reloadConfig() already installs the bundled config.yml as
        // defaults; make sure lookups see them and persist any missing keys.
        raw.options().copyDefaults(true);
        plugin.saveConfig();

        UnboundConfig parsed = ConfigParser.parse(toRawMap(raw));
        current = parsed;
        logger.fine(() -> "[Unbound] Config loaded: " + String.join(", ", parsed.debugSummaryLines()));
        return parsed;
    }

    public UnboundConfig current() {
        UnboundConfig config = current;
        if (config == null) {
            synchronized (this) {
                if (current == null) {
                    return load();
                }
                return current;
            }
        }
        return config;
    }

    /** Converts Bukkit's tree into a plain nested map for the pure parser. */
    private static Map<String, Object> toRawMap(FileConfiguration configuration) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : configuration.getKeys(false)) {
            result.put(key, unwrap(configuration.get(key)));
        }
        return result;
    }

    private static Object unwrap(@Nullable Object value) {
        if (value instanceof MemorySection section) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key, unwrap(section.get(key)));
            }
            return map;
        }
        return value;
    }
}
