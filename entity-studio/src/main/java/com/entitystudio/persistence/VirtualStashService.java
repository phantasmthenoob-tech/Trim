package com.entitystudio.persistence;

import com.entitystudio.EntityStudioPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persistent per-entity stash for entities with no native inventory
 * (cows, pigs, creepers, ...). Keyed by entity UUID, 27 slots, saved to
 * stashes.yml. This is plugin-provided storage — clearly labeled as such in
 * the GUI — not a vanilla entity inventory.
 */
public class VirtualStashService {

    public static final int STASH_SIZE = 27;
    private static final int SCHEMA = 1;

    private final EntityStudioPlugin plugin;
    private final File file;
    private final Map<UUID, Inventory> live = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> pending = new ConcurrentHashMap<>();

    public VirtualStashService(EntityStudioPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stashes.yml");
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (yaml.getInt("schema-version", 1) != SCHEMA) {
            plugin.getLogger().warning("stashes.yml schema mismatch; stashes will be re-saved.");
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("stashes");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Quarantining stash with bad UUID: " + key);
                continue;
            }
            ConfigurationSection slots = root.getConfigurationSection(key + ".slots");
            if (slots == null) {
                continue;
            }
            ItemStack[] arr = new ItemStack[STASH_SIZE];
            for (String s : slots.getKeys(false)) {
                int idx;
                try {
                    idx = Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (idx < 0 || idx >= STASH_SIZE) {
                    continue;
                }
                try {
                    Object raw = slots.get(s);
                    if (raw instanceof ConfigurationSection cs) {
                        arr[idx] = ItemStack.deserialize(cs.getValues(false));
                    } else if (raw instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) map;
                        arr[idx] = ItemStack.deserialize(m);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Quarantining corrupt stash slot " + key + "/" + s, e);
                }
            }
            pending.put(id, arr);
        }
    }

    /** Returns the live stash inventory, materializing saved contents on demand. */
    public Inventory stashFor(UUID entityUuid) {
        return live.computeIfAbsent(entityUuid, id -> {
            Inventory inv = Bukkit.createInventory(null, STASH_SIZE,
                    Component.text("Entity stash"));
            ItemStack[] arr = pending.remove(id);
            if (arr != null) {
                for (int i = 0; i < STASH_SIZE && i < arr.length; i++) {
                    if (arr[i] != null) {
                        inv.setItem(i, arr[i]);
                    }
                }
            }
            return inv;
        });
    }

    public boolean hasStash(UUID entityUuid) {
        return live.containsKey(entityUuid) || pending.containsKey(entityUuid);
    }

    /** Persists one stash immediately (called after every GUI mutation). */
    public void saveOne(UUID entityUuid) {
        Inventory inv = live.get(entityUuid);
        if (inv == null) {
            return;
        }
        YamlConfiguration yaml = file.exists()
                ? YamlConfiguration.loadConfiguration(file)
                : new YamlConfiguration();
        yaml.set("schema-version", SCHEMA);
        writeStash(yaml, entityUuid, inv);
        saveQuietly(yaml);
    }

    /** Persists everything (called on disable). */
    public void save() {
        for (UUID id : pending.keySet()) {
            stashFor(id);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", SCHEMA);
        for (Map.Entry<UUID, Inventory> e : live.entrySet()) {
            writeStash(yaml, e.getKey(), e.getValue());
        }
        saveQuietly(yaml);
    }

    /** Drops a stash entirely (e.g. when Studio removes the entity). */
    public void drop(UUID entityUuid) {
        live.remove(entityUuid);
        pending.remove(entityUuid);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("stashes." + entityUuid, null);
        saveQuietly(yaml);
    }

    private void writeStash(YamlConfiguration yaml, UUID id, Inventory inv) {
        String base = "stashes." + id + ".slots.";
        for (int i = 0; i < STASH_SIZE; i++) {
            ItemStack s = inv.getItem(i);
            yaml.set(base + i, s == null ? null : s.serialize());
        }
    }

    private void saveQuietly(YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save stashes.yml", e);
        }
    }
}
