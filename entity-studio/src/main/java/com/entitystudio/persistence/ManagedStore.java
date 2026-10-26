package com.entitystudio.persistence;

import com.entitystudio.EntityStudioPlugin;
import com.entitystudio.behavior.ManagedRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists plugin-managed allegiance/behavior records (UUID-keyed).
 * Invalid entries are quarantined with a log message, never loaded silently.
 */
public class ManagedStore {

    private static final int SCHEMA = 1;

    private final EntityStudioPlugin plugin;
    private final File file;

    public ManagedStore(EntityStudioPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "managed.yml");
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int schema = yaml.getInt("schema-version", 1);
        if (schema != SCHEMA) {
            plugin.getLogger().warning("managed.yml schema v" + schema
                    + " is not v" + SCHEMA + "; managed records will be re-saved, not merged.");
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("entities");
        if (root == null) {
            return;
        }
        int bad = 0;
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Quarantining managed.yml entry with bad UUID: " + key);
                bad++;
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                bad++;
                continue;
            }
            try {
                // Plain-map form feeds the pure codec: per-field quarantine
                // with the same defaults the loader always had.
                ManagedCodec.DecodeResult decoded =
                        ManagedCodec.decode(id, toPlainMap(s));
                for (String problem : decoded.problems()) {
                    plugin.getLogger().warning("Quarantining managed.yml field "
                            + key + "/" + problem);
                }
                if (!decoded.clean()) {
                    bad++;
                }
                plugin.allegiance().all().put(id, decoded.record());
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Quarantining corrupt managed.yml entry " + key + ": " + e.getMessage());
                bad++;
            }
        }
        if (bad > 0) {
            plugin.getLogger().warning("Quarantined " + bad + " invalid managed record(s).");
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA);
        for (ManagedRecord r : plugin.allegiance().all().values()) {
            // The codec owns the field list (now including type filters and
            // pursuit/clear flags); nested maps are flattened to dotted paths.
            writePlain(yaml, "entities." + r.entityUuid(), ManagedCodec.encode(r));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save managed.yml", e);
        }
    }

    /** ConfigurationSection → nested plain maps for the pure codec. */
    private static java.util.Map<String, Object> toPlainMap(ConfigurationSection section) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (section.isConfigurationSection(key)) {
                ConfigurationSection sub = section.getConfigurationSection(key);
                out.put(key, sub == null ? null : toPlainMap(sub));
            } else {
                out.put(key, section.get(key));
            }
        }
        return out;
    }

    /** Nested plain maps → dotted YAML paths. */
    @SuppressWarnings("unchecked")
    private static void writePlain(YamlConfiguration yaml, String base,
            java.util.Map<String, Object> map) {
        for (java.util.Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof java.util.Map<?, ?> sub) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                for (java.util.Map.Entry<?, ?> se : sub.entrySet()) {
                    m.put(String.valueOf(se.getKey()), se.getValue());
                }
                writePlain(yaml, base + "." + e.getKey(), m);
            } else {
                yaml.set(base + "." + e.getKey(), v);
            }
        }
    }

    public List<String> quarantinedOnLoad() {
        return List.of();
    }
}
