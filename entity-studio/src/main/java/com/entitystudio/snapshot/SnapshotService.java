package com.entitystudio.snapshot;

import com.entitystudio.EntityStudioPlugin;
import com.entitystudio.behavior.ManagedRecord;
import com.entitystudio.property.EntityProperty;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Entity snapshots (full capture, schema-versioned) and presets (named
 * snapshots with selective application + preview of pending changes).
 * Snapshots cover supported state only — never a raw NBT rewrite.
 */
public class SnapshotService {

    public static final int SCHEMA = 1;

    public enum Section {
        PROPERTIES, EQUIPMENT, BEHAVIOR,
    }

    public record PreviewEntry(String label, String from, String to) {
    }

    private final EntityStudioPlugin plugin;
    private final File snapshotsFile;
    private final File presetsFile;

    public SnapshotService(EntityStudioPlugin plugin) {
        this.plugin = plugin;
        this.snapshotsFile = new File(plugin.getDataFolder(), "snapshots.yml");
        this.presetsFile = new File(plugin.getDataFolder(), "presets.yml");
    }

    // ---------------------------------------------------------------- capture

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> capture(Entity entity) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schema-version", SCHEMA);
        out.put("type", entity.getType().name());
        Map<String, String> props = new LinkedHashMap<>();
        for (EntityProperty<?> p : plugin.properties().listFor(entity)) {
            try {
                props.put(p.id(), String.valueOf(((EntityProperty) p).read(entity)));
            } catch (Exception e) {
                props.put(p.id(), "<unreadable>");
            }
        }
        out.put("properties", props);
        if (entity instanceof LivingEntity living && living.getEquipment() != null) {
            EntityEquipment eq = living.getEquipment();
            Map<String, Object> slots = new LinkedHashMap<>();
            slots.put("main-hand", eq.getItemInMainHand().serialize());
            slots.put("off-hand", eq.getItemInOffHand().serialize());
            slots.put("helmet", eq.getHelmet() == null ? null : eq.getHelmet().serialize());
            slots.put("chestplate", eq.getChestplate() == null ? null : eq.getChestplate().serialize());
            slots.put("leggings", eq.getLeggings() == null ? null : eq.getLeggings().serialize());
            slots.put("boots", eq.getBoots() == null ? null : eq.getBoots().serialize());
            out.put("equipment", slots);
        }
        ManagedRecord rec = plugin.allegiance().get(entity.getUniqueId());
        if (rec != null) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("profile", rec.profile().name());
            b.put("owner", rec.owner() == null ? null : rec.owner().toString());
            b.put("trusted", rec.trusted().stream().map(UUID::toString).toList());
            b.put("enemies", rec.enemies().stream().map(UUID::toString).toList());
            b.put("defend-owner", rec.defendOwner());
            b.put("defend-allies", rec.defendAllies());
            b.put("retaliate", rec.retaliate());
            out.put("behavior", b);
        }
        return out;
    }

    // ----------------------------------------------------------------- store

    @SuppressWarnings("unchecked")
    private YamlConfiguration load(File f) {
        if (!f.exists()) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(f);
    }

    private void save(YamlConfiguration yaml, File f) {
        try {
            yaml.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + f.getName(), e);
        }
    }

    public void saveSnapshot(String name, Entity entity) {
        YamlConfiguration yaml = load(snapshotsFile);
        yaml.set("schema-version", SCHEMA);
        yaml.set("snapshots." + name + ".owner", entity.getUniqueId().toString());
        yaml.set("snapshots." + name + ".type", entity.getType().name());
        yaml.set("snapshots." + name + ".data", capture(entity));
        save(yaml, snapshotsFile);
    }

    public void savePreset(String name, Entity entity) {
        YamlConfiguration yaml = load(presetsFile);
        yaml.set("schema-version", SCHEMA);
        yaml.set("presets." + name + ".type", entity.getType().name());
        yaml.set("presets." + name + ".data", capture(entity));
        save(yaml, presetsFile);
    }

    public Set<String> snapshotNames() {
        ConfigurationSection s = load(snapshotsFile).getConfigurationSection("snapshots");
        return s == null ? Set.of() : s.getKeys(false);
    }

    public Set<String> presetNames() {
        ConfigurationSection s = load(presetsFile).getConfigurationSection("presets");
        return s == null ? Set.of() : s.getKeys(false);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(File f, String section, String name) {
        ConfigurationSection root = load(f).getConfigurationSection(section + "." + name + ".data");
        if (root == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : root.getKeys(true)) {
            if (root.isConfigurationSection(key)) {
                continue;
            }
            out.put(key, root.get(key));
        }
        return Map.of("flat", (Object) out, "root", root);
    }

    // ---------------------------------------------------------------- preview

    /** Compares a preset against the entity; shows exactly what would change. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<PreviewEntry> previewPreset(String name, Entity entity, Set<Section> sections) {
        List<PreviewEntry> out = new ArrayList<>();
        Map<String, Object> packed = dataOf(presetsFile, "presets", name);
        if (packed == null) {
            return out;
        }
        ConfigurationSection root = (ConfigurationSection) packed.get("root");
        if (sections.contains(Section.PROPERTIES)) {
            ConfigurationSection props = root.getConfigurationSection("properties");
            if (props != null) {
                for (String id : props.getKeys(false)) {
                    EntityProperty prop = plugin.properties().byId(id);
                    if (prop == null || prop.support(entity) == EntityProperty.Support.UNSUPPORTED) {
                        out.add(new PreviewEntry(id, "—", "(unsupported here, skipped)"));
                        continue;
                    }
                    String current;
                    try {
                        current = String.valueOf(((EntityProperty) prop).read(entity));
                    } catch (Exception e) {
                        current = "<unreadable>";
                    }
                    String target = String.valueOf(props.get(id));
                    if (!current.equals(target)) {
                        out.add(new PreviewEntry(prop.displayName() + " (" + id + ")", current, target));
                    }
                }
            }
        }
        if (sections.contains(Section.EQUIPMENT) && root.isSet("equipment")) {
            out.add(new PreviewEntry("Equipment set", "current loadout", "preset loadout (6 slots)"));
        }
        if (sections.contains(Section.BEHAVIOR) && root.isSet("behavior")) {
            ConfigurationSection b = root.getConfigurationSection("behavior");
            out.add(new PreviewEntry("Behavior profile",
                    currentProfile(entity), String.valueOf(b.getString("profile", "?"))));
        }
        return out;
    }

    private String currentProfile(Entity entity) {
        ManagedRecord r = plugin.allegiance().get(entity.getUniqueId());
        if (r == null) {
            return "none";
        }
        return r.profile().name();
    }

    // ----------------------------------------------------------------- apply

    /** Applies a preset selectively; returns [changed, skipped] counts. */
    public int[] applyPreset(String name, Entity entity, Set<Section> sections,
            org.bukkit.entity.Player actor) {
        ConfigurationSection root = load(presetsFile)
                .getConfigurationSection("presets." + name + ".data");
        if (root == null) {
            return new int[] { 0, 0 };
        }
        return applyData(root, entity, sections, actor);
    }

    /** Restores a snapshot's data onto the entity (best-effort, reported). */
    public int[] applySnapshot(String name, Entity entity, org.bukkit.entity.Player actor) {
        ConfigurationSection root = load(snapshotsFile)
                .getConfigurationSection("snapshots." + name + ".data");
        if (root == null) {
            return new int[] { 0, 0 };
        }
        return applyData(root, entity, java.util.EnumSet.allOf(Section.class), actor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int[] applyData(ConfigurationSection root, Entity entity, Set<Section> sections,
            org.bukkit.entity.Player actor) {
        int changed = 0;
        int skipped = 0;
        if (sections.contains(Section.PROPERTIES)) {
            ConfigurationSection props = root.getConfigurationSection("properties");
            if (props != null) {
                for (String id : props.getKeys(false)) {
                    EntityProperty prop = plugin.properties().byId(id);
                    if (prop == null || prop.support(entity) == EntityProperty.Support.UNSUPPORTED) {
                        skipped++;
                        continue;
                    }
                    EntityProperty.ParseResult parsed = prop.parse(String.valueOf(props.get(id)));
                    if (!parsed.ok()) {
                        skipped++;
                        continue;
                    }
                    String err = plugin.properties().apply(actor, entity, prop, parsed.value(),
                            com.entitystudio.permission.StudioPermissions.DATA_EDIT,
                            plugin.history(), true);
                    if (err == null) {
                        changed++;
                    } else {
                        skipped++;
                    }
                }
            }
        }
        if (sections.contains(Section.EQUIPMENT) && entity instanceof LivingEntity living
                && living.getEquipment() != null) {
            ConfigurationSection eq = root.getConfigurationSection("equipment");
            if (eq != null && actor.hasPermission(
                    com.entitystudio.permission.StudioPermissions.INV_EDIT)) {
                EntityEquipment slots = living.getEquipment();
                changed += setSlot(eq, "main-hand", slots::setItemInMainHand) ? 1 : 0;
                changed += setSlot(eq, "off-hand", slots::setItemInOffHand) ? 1 : 0;
                changed += setSlot(eq, "helmet", slots::setHelmet) ? 1 : 0;
                changed += setSlot(eq, "chestplate", slots::setChestplate) ? 1 : 0;
                changed += setSlot(eq, "leggings", slots::setLeggings) ? 1 : 0;
                changed += setSlot(eq, "boots", slots::setBoots) ? 1 : 0;
            }
        }
        if (sections.contains(Section.BEHAVIOR) && actor.hasPermission(
                com.entitystudio.permission.StudioPermissions.BEHAVIOR)) {
            ConfigurationSection b = root.getConfigurationSection("behavior");
            if (b != null) {
                ManagedRecord r = plugin.allegiance().getOrCreate(entity);
                try {
                    r.profile(ManagedRecord.Profile.valueOf(b.getString("profile", "CUSTOM")));
                    changed++;
                } catch (IllegalArgumentException e) {
                    skipped++;
                }
            }
        }
        return new int[] { changed, skipped };
    }

    private boolean setSlot(ConfigurationSection eq, String key,
            java.util.function.Consumer<ItemStack> setter) {
        if (!eq.isSet(key)) {
            return false;
        }
        try {
            Object raw = eq.get(key);
            if (raw == null) {
                setter.accept(null);
                return true;
            }
            if (raw instanceof ConfigurationSection sec) {
                setter.accept(ItemStack.deserialize(sec.getValues(false)));
            } else if (raw instanceof Map<?, ?> map) {
                setter.accept(ItemStack.deserialize((Map<String, Object>) map));
            } else {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

}
