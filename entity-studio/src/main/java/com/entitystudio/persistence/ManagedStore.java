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
                ManagedRecord r = new ManagedRecord(id);
                r.entityType(s.getString("type", "UNKNOWN"));
                String owner = s.getString("allegiance.owner", null);
                if (owner != null && !owner.isBlank()) {
                    r.owner(UUID.fromString(owner));
                }
                for (String t : s.getStringList("allegiance.trusted")) {
                    r.trusted().add(UUID.fromString(t));
                }
                for (String t : s.getStringList("allegiance.enemies")) {
                    r.enemies().add(UUID.fromString(t));
                }
                r.defendOwner(s.getBoolean("allegiance.defend-owner", true));
                r.defendAllies(s.getBoolean("allegiance.defend-allies", false));
                r.retaliate(s.getBoolean("allegiance.retaliate", false));
                r.attackOwnerTarget(s.getBoolean("allegiance.attack-owner-target", false));
                try {
                    r.profile(ManagedRecord.Profile.valueOf(
                            s.getString("behavior.profile", "CUSTOM").toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    r.profile(ManagedRecord.Profile.CUSTOM);
                }
                r.aiEnabled(s.getBoolean("behavior.ai-enabled", true));
                if (s.getBoolean("behavior.anchored", false)) {
                    r.setAnchor(true, s.getBoolean("behavior.hard-anchor", false),
                            s.getString("behavior.world", null),
                            s.getDouble("behavior.x"), s.getDouble("behavior.y"), s.getDouble("behavior.z"),
                            (float) s.getDouble("behavior.yaw"), (float) s.getDouble("behavior.pitch"));
                }
                plugin.allegiance().all().put(id, r);
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
            String p = "entities." + r.entityUuid() + ".";
            yaml.set(p + "type", r.entityType());
            yaml.set(p + "allegiance.owner", r.owner() == null ? null : r.owner().toString());
            yaml.set(p + "allegiance.trusted", new ArrayList<>(r.trusted()).stream()
                    .map(UUID::toString).toList());
            yaml.set(p + "allegiance.enemies", new ArrayList<>(r.enemies()).stream()
                    .map(UUID::toString).toList());
            yaml.set(p + "allegiance.defend-owner", r.defendOwner());
            yaml.set(p + "allegiance.defend-allies", r.defendAllies());
            yaml.set(p + "allegiance.retaliate", r.retaliate());
            yaml.set(p + "allegiance.attack-owner-target", r.attackOwnerTarget());
            yaml.set(p + "behavior.profile", r.profile().name());
            yaml.set(p + "behavior.ai-enabled", r.aiEnabled());
            yaml.set(p + "behavior.anchored", r.anchored());
            yaml.set(p + "behavior.hard-anchor", r.hardAnchor());
            yaml.set(p + "behavior.world", r.anchorWorld());
            yaml.set(p + "behavior.x", r.anchorX());
            yaml.set(p + "behavior.y", r.anchorY());
            yaml.set(p + "behavior.z", r.anchorZ());
            yaml.set(p + "behavior.yaw", r.anchorYaw());
            yaml.set(p + "behavior.pitch", r.anchorPitch());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save managed.yml", e);
        }
    }

    public List<String> quarantinedOnLoad() {
        return List.of();
    }
}
