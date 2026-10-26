package com.entitystudio.snapshot;

import com.entitystudio.EntityStudioPlugin;
import com.entitystudio.behavior.AllegianceService;
import com.entitystudio.behavior.ManagedRecord;
import com.entitystudio.history.EditRecord;
import com.entitystudio.property.EntityProperty;
import com.entitystudio.snapshot.SnapshotPlan.ApplyOutcome;
import com.entitystudio.snapshot.SnapshotPlan.FieldPlan;
import com.entitystudio.snapshot.SnapshotPlan.PlannedOp;
import com.entitystudio.snapshot.SnapshotPlan.Status;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Entity snapshots (full capture, schema-versioned) and presets (named
 * snapshots with selective application + preview of pending changes).
 * Snapshots cover supported state only — never a raw NBT rewrite.
 *
 * <p>Merge semantics: absent fields mean "leave the target unchanged".
 * Explicit resets require intentional sentinels ({@code __empty__} for an
 * equipment slot, {@code __none__} for the allegiance owner) because YAML
 * cannot distinguish an explicit null from a missing key. Unknown fields
 * and unknown schema versions are never silently applied.
 *
 * <p>Every apply validates the whole change before mutating anything, then
 * executes in section order. A midway failure triggers best-effort rollback
 * of completed ops — reported honestly, never promised as transactional.
 */
public class SnapshotService {

    public static final String SCHEMA_KEY = "schema-version";
    public static final String TYPE_KEY = "type";

    /** Snapshot/preset names: filesystem- and YAML-safe, no path tricks. */
    public static boolean isValidEntryName(String name) {
        return name != null && name.matches("[A-Za-z0-9 _-]{1,32}");
    }

    public enum Section {
        PROPERTIES,
        ATTRIBUTES,
        EFFECTS,
        EQUIPMENT,
        BEHAVIOR,
    }

    /** Result of an apply: counts, warnings, and honest rollback reporting. */
    public record ApplyResult(int changed, int skipped, List<String> warnings,
            boolean rolledBack, int reverted, List<String> rollbackErrors) {
    }

    private static final Set<String> KNOWN_DATA_KEYS = Set.of(SCHEMA_KEY, TYPE_KEY,
            "properties", "attributes", "effects", "equipment", "behavior");

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
        out.put(SCHEMA_KEY, SnapshotPlan.SCHEMA);
        out.put(TYPE_KEY, entity.getType().name());
        Map<String, String> props = new LinkedHashMap<>();
        for (EntityProperty<?> p : plugin.properties().listFor(entity)) {
            try {
                // Unreadable values are left absent (absent = unchanged) so a
                // snapshot can never poison a restore with a "<unreadable>"
                // string that no parser accepts.
                Object v = ((EntityProperty) p).read(entity);
                if (v != null) {
                    props.put(p.id(), String.valueOf(v));
                }
            } catch (Exception e) {
                // absent = unchanged
            }
        }
        out.put("properties", props);
        if (entity instanceof Attributable attr) {
            Map<String, Double> attrs = new LinkedHashMap<>();
            for (Attribute key : Registry.ATTRIBUTE) {
                AttributeInstance inst = attr.getAttribute(key);
                if (inst != null) {
                    attrs.put(key.getKey().getKey(), inst.getBaseValue());
                }
            }
            out.put("attributes", attrs);
        }
        if (entity instanceof LivingEntity living) {
            List<Map<String, Object>> fx = new ArrayList<>();
            for (PotionEffect e : living.getActivePotionEffects()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", e.getType().getKey().getKey());
                m.put("duration", e.getDuration());
                m.put("amplifier", e.getAmplifier());
                m.put("ambient", e.isAmbient());
                m.put("particles", e.hasParticles());
                fx.add(m);
            }
            out.put("effects", fx);
        }
        if (entity instanceof LivingEntity living && living.getEquipment() != null) {
            EntityEquipment eq = living.getEquipment();
            Map<String, Object> slots = new LinkedHashMap<>();
            slots.put("main-hand", slotData(eq.getItemInMainHand()));
            slots.put("off-hand", slotData(eq.getItemInOffHand()));
            slots.put("helmet", slotData(eq.getHelmet()));
            slots.put("chestplate", slotData(eq.getChestplate()));
            slots.put("leggings", slotData(eq.getLeggings()));
            slots.put("boots", slotData(eq.getBoots()));
            out.put("equipment", slots);
        }
        ManagedRecord rec = plugin.allegiance().get(entity.getUniqueId());
        if (rec != null) {
            out.put("behavior", behaviorData(rec));
        }
        return out;
    }

    private static Object slotData(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return SnapshotPlan.EMPTY_SLOT;
        }
        return stack.serialize();
    }

    /** Full plugin-managed behavior record as plain data. */
    static Map<String, Object> behaviorData(ManagedRecord r) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("profile", r.profile().name());
        b.put("ai-enabled", r.aiEnabled());
        b.put("owner", r.owner() == null ? SnapshotPlan.NO_OWNER : r.owner().toString());
        b.put("trusted", r.trusted().stream().map(UUID::toString).sorted().toList());
        b.put("enemies", r.enemies().stream().map(UUID::toString).sorted().toList());
        b.put("allowed-types", new ArrayList<>(new LinkedHashSet<>(r.allowedTypes())));
        b.put("excluded-types", new ArrayList<>(new LinkedHashSet<>(r.excludedTypes())));
        b.put("defend-owner", r.defendOwner());
        b.put("defend-allies", r.defendAllies());
        b.put("retaliate", r.retaliate());
        b.put("attack-owner-target", r.attackOwnerTarget());
        b.put("pursue-outside-range", r.pursueOutsideRange());
        b.put("clear-invalid-target", r.clearInvalidTarget());
        b.put("friendly-fire", r.friendlyFireProtect());
        Map<String, Object> anchor = new LinkedHashMap<>();
        anchor.put("anchored", r.anchored());
        anchor.put("hard", r.hardAnchor());
        anchor.put("world", r.anchorWorld());
        anchor.put("x", r.anchorX());
        anchor.put("y", r.anchorY());
        anchor.put("z", r.anchorZ());
        anchor.put("yaw", r.anchorYaw());
        anchor.put("pitch", r.anchorPitch());
        b.put("anchor", anchor);
        return b;
    }

    // ----------------------------------------------------------------- store

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
        yaml.set(SCHEMA_KEY, SnapshotPlan.SCHEMA);
        yaml.set("snapshots." + name + ".type", entity.getType().name());
        yaml.set("snapshots." + name + ".data", capture(entity));
        save(yaml, snapshotsFile);
    }

    public void savePreset(String name, Entity entity) {
        YamlConfiguration yaml = load(presetsFile);
        yaml.set(SCHEMA_KEY, SnapshotPlan.SCHEMA);
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

    /** Seeds a few realistic example presets on first run (no overwrite). */
    public void seedExamples() {
        if (presetsFile.exists()) {
            return;
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set(SCHEMA_KEY, SnapshotPlan.SCHEMA);
            yaml.set("presets.Armed Elite.type", "ZOMBIE");
            yaml.set("presets.Armed Elite.data", armedElite());
            yaml.set("presets.Personal Guard.type", "ZOMBIE");
            yaml.set("presets.Personal Guard.data", personalGuard());
            yaml.set("presets.Stationary Display.type", "ZOMBIE");
            yaml.set("presets.Stationary Display.data", stationaryDisplay());
            yaml.save(presetsFile);
            plugin.getLogger().info("Seeded example presets: Armed Elite, Personal Guard,"
                    + " Stationary Display.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not seed example presets", e);
        }
    }

    private static Map<String, Object> armedElite() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(SCHEMA_KEY, SnapshotPlan.SCHEMA);
        data.put(TYPE_KEY, "ZOMBIE");
        Map<String, Object> eq = new LinkedHashMap<>();
        eq.put("main-hand", new ItemStack(org.bukkit.Material.DIAMOND_SWORD).serialize());
        eq.put("helmet", new ItemStack(org.bukkit.Material.DIAMOND_HELMET).serialize());
        eq.put("chestplate", new ItemStack(org.bukkit.Material.DIAMOND_CHESTPLATE).serialize());
        eq.put("leggings", new ItemStack(org.bukkit.Material.DIAMOND_LEGGINGS).serialize());
        eq.put("boots", new ItemStack(org.bukkit.Material.DIAMOND_BOOTS).serialize());
        data.put("equipment", eq);
        data.put("attributes", Map.of("max_health", 40.0));
        List<Map<String, Object>> fx = new ArrayList<>();
        Map<String, Object> fire = new LinkedHashMap<>();
        fire.put("type", "fire_resistance");
        fire.put("duration", 1728000);
        fire.put("amplifier", 0);
        fx.add(fire);
        data.put("effects", fx);
        return data;
    }

    private static Map<String, Object> personalGuard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(SCHEMA_KEY, SnapshotPlan.SCHEMA);
        data.put(TYPE_KEY, "ZOMBIE");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("profile", "GUARD");
        b.put("ai-enabled", true);
        b.put("owner", SnapshotPlan.NO_OWNER);
        b.put("trusted", List.of());
        b.put("enemies", List.of());
        b.put("defend-owner", true);
        b.put("defend-allies", true);
        b.put("retaliate", true);
        b.put("attack-owner-target", false);
        b.put("friendly-fire", true);
        data.put("behavior", b);
        return data;
    }

    private static Map<String, Object> stationaryDisplay() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(SCHEMA_KEY, SnapshotPlan.SCHEMA);
        data.put(TYPE_KEY, "ZOMBIE");
        data.put("properties", Map.of("silent", "true", "invulnerable", "true"));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("profile", "STATIONARY");
        b.put("ai-enabled", false);
        b.put("defend-owner", false);
        b.put("defend-allies", false);
        b.put("retaliate", false);
        b.put("attack-owner-target", false);
        data.put("behavior", b);
        return data;
    }

    /** Deep-copies a stored data section into plain nested maps/lists. */
    @SuppressWarnings("unchecked")
    static Object plain(Object raw) {
        if (raw instanceof ConfigurationSection sec) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : sec.getKeys(false)) {
                out.put(key, plain(sec.get(key)));
            }
            return out;
        }
        if (raw instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object e : list) {
                out.add(plain(e));
            }
            return out;
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), plain(e.getValue()));
            }
            return out;
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> storedData(File f, String section, String name) {
        ConfigurationSection root = load(f).getConfigurationSection(section + "." + name + ".data");
        if (root == null) {
            return null;
        }
        Object p = plain(root);
        return p instanceof Map ? (Map<String, Object>) p : null;
    }

    // ---------------------------------------------------------------- preview

    /** Compares a preset against the entity: every stored field gets a status. */
    public List<FieldPlan> previewPreset(String name, Entity entity, Set<Section> sections) {
        PlanBundle bundle = buildPlan(presetsFile, "presets", name, entity, sections, null);
        return bundle.plans();
    }

    /** Compares a snapshot against the entity (full-section preview). */
    public List<FieldPlan> previewSnapshot(String name, Entity entity) {
        PlanBundle bundle =
                buildPlan(snapshotsFile, "snapshots", name, entity, EnumSet.allOf(Section.class), null);
        return bundle.plans();
    }

    // ----------------------------------------------------------------- apply

    /** Applies a preset selectively; validates everything before mutating. */
    public ApplyResult applyPreset(String name, Entity entity, Set<Section> sections, Player actor) {
        PlanBundle bundle = buildPlan(presetsFile, "presets", name, entity, sections, actor);
        if (bundle.refused() != null) {
            return new ApplyResult(0, 0, List.of(bundle.refused()), false, 0, List.of());
        }
        return execute(bundle);
    }

    /** Restores a snapshot's data onto the entity (all sections, validated first). */
    public ApplyResult applySnapshot(String name, Entity entity, Player actor) {
        PlanBundle bundle = buildPlan(snapshotsFile, "snapshots", name, entity,
                EnumSet.allOf(Section.class), actor);
        if (bundle.refused() != null) {
            return new ApplyResult(0, 0, List.of(bundle.refused()), false, 0, List.of());
        }
        return execute(bundle);
    }

    private ApplyResult execute(PlanBundle bundle) {
        ApplyOutcome out = SnapshotPlan.runAll(bundle.ops(), bundle.warnings());
        // Ops that never ran (after a midway stop) count as skipped, not changed.
        int neverRan = out.failedMidway() ? bundle.ops().size() - out.changed() : 0;
        return new ApplyResult(out.changed(), bundle.skipped() + neverRan, out.warnings(),
                out.failedMidway(), out.reverted(), out.rollbackErrors());
    }

    // ------------------------------------------------------------ plan build

    /** Everything known before the first mutation: ops, display rows, counts. */
    private record PlanBundle(List<PlannedOp> ops, List<FieldPlan> plans, int skipped,
            List<String> warnings, String refused) {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private PlanBundle buildPlan(File f, String section, String name, Entity entity,
            Set<Section> sections, Player actor) {
        List<PlannedOp> ops = new ArrayList<>();
        List<FieldPlan> plans = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int[] skipped = {0};
        java.util.function.Consumer<FieldPlan> track = p -> {
            plans.add(p);
            if (p.status() != Status.CHANGE) {
                skipped[0]++;
            }
        };

        Map<String, Object> data = storedData(f, section, name);
        if (data == null) {
            return new PlanBundle(ops, plans, 1, warnings, "No such entry: " + name);
        }
        // Schema gate: unknown versions refuse the whole apply.
        SnapshotPlan.SchemaDecision schema = SnapshotPlan.checkSchema(data.get(SCHEMA_KEY));
        if (schema == SnapshotPlan.SchemaDecision.REFUSE) {
            return new PlanBundle(ops, plans, 1, warnings,
                    "Unsupported snapshot schema '" + data.get(SCHEMA_KEY)
                            + "' — entry not applied. (Supports v1 legacy and v2.)");
        }
        if (schema == SnapshotPlan.SchemaDecision.LEGACY_WARN) {
            warnings.add("Legacy v1 entry: attributes/effects sections unavailable.");
        }
        // Type gate: warn loudly, apply supported fields anyway.
        SnapshotPlan.typeMismatchWarning(
                data.get(TYPE_KEY) == null ? null : String.valueOf(data.get(TYPE_KEY)),
                entity.getType().name()).ifPresent(warnings::add);
        // Unknown top-level sections are reported, never applied.
        for (String key : data.keySet()) {
            if (!KNOWN_DATA_KEYS.contains(key)) {
                FieldPlan p = SnapshotPlan.planUnknown("entry",
                        "Unknown section '" + key + "'", "ignored — never applied");
                track.accept(p);
                warnings.add("Unknown section '" + key + "' ignored.");
            }
        }

        if (sections.contains(Section.PROPERTIES)) {
            planProperties(data, entity, actor, ops, track, warnings);
        }
        if (sections.contains(Section.ATTRIBUTES)) {
            planAttributes(data, entity, actor, ops, track, warnings);
        }
        if (sections.contains(Section.EFFECTS)) {
            planEffects(data, entity, actor, ops, track, warnings);
        }
        if (sections.contains(Section.EQUIPMENT)) {
            planEquipment(data, entity, actor, ops, track, warnings);
        }
        if (sections.contains(Section.BEHAVIOR)) {
            planBehavior(data, entity, actor, ops, track, warnings);
        }
        return new PlanBundle(ops, plans, skipped[0], warnings, null);
    }

    // ------------------------------------------------------- section planners

    private Object sectionData(Map<String, Object> data, String key) {
        Object o = data.get(key);
        return o instanceof Map ? o : null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void planProperties(Map<String, Object> data, Entity entity, Player actor,
            List<PlannedOp> ops, java.util.function.Consumer<FieldPlan> track,
            List<String> warnings) {
        Object raw = sectionData(data, "properties");
        if (raw == null) {
            return;
        }
        Map<String, Object> stored = (Map<String, Object>) raw;
        if (actor != null && !plugin.permitted(actor,
                com.entitystudio.permission.StudioPermissions.DATA_EDIT)) {
            warnings.add("Missing permission for properties — section skipped.");
            for (String id : stored.keySet()) {
                track.accept(SnapshotPlan.planUnsupported("properties", id, null,
                        "no permission"));
            }
            return;
        }
        for (Map.Entry<String, Object> e : stored.entrySet()) {
            String id = e.getKey();
            String target = String.valueOf(e.getValue());
            EntityProperty prop = plugin.properties().byId(id);
            if (prop == null) {
                track.accept(SnapshotPlan.planUnknown("properties", id,
                        "not registered — never applied"));
                warnings.add("Unknown property '" + id + "' ignored.");
                continue;
            }
            EntityProperty.Support support = prop.support(entity);
            if (!support.writable()) {
                String current = safeRead(prop, entity);
                track.accept(SnapshotPlan.planUnsupported("properties",
                        prop.displayName() + " (" + id + ")", current,
                        support.name().toLowerCase() + " here — skipped"));
                continue;
            }
            EntityProperty.ParseResult parsed = prop.parse(target);
            if (!parsed.ok()) {
                track.accept(new FieldPlan("properties",
                        prop.displayName() + " (" + id + ")", safeRead(prop, entity), target,
                        Status.INVALID, parsed.error()));
                continue;
            }
            String err = ((EntityProperty) prop).validate(entity, parsed.value()).ok() ? null
                    : ((EntityProperty) prop).validate(entity, parsed.value()).message();
            if (err != null) {
                track.accept(new FieldPlan("properties",
                        prop.displayName() + " (" + id + ")", safeRead(prop, entity), target,
                        Status.INVALID, err));
                continue;
            }
            String current = safeRead(prop, entity);
            FieldPlan plan = SnapshotPlan.planField("properties",
                    prop.displayName() + " (" + id + ")", current, String.valueOf(parsed.value()),
                    true, null);
            track.accept(plan);
            if (plan.status() != Status.CHANGE) {
                continue;
            }
            EntityProperty propF = prop;
            Object valueF = parsed.value();
            ops.add(new PlannedOp() {
                String before;

                public FieldPlan plan() {
                    return plan;
                }

                public void execute() throws Exception {
                    before = safeRead(propF, entity);
                    String err2 = com.entitystudio.property.PropertyRegistry.apply(actor, entity,
                            propF, valueF,
                            com.entitystudio.permission.StudioPermissions.DATA_EDIT,
                            node -> actor == null || plugin.permitted(actor, node),
                            plugin.history(), true);
                    if (err2 != null) {
                        throw new IllegalStateException(err2);
                    }
                }

                public void revert() throws Exception {
                    EntityProperty.ParseResult old = propF.parse(before);
                    if (!old.ok()) {
                        throw new IllegalStateException("Prior value no longer parses.");
                    }
                    Object v = old.value();
                    if (!((EntityProperty) propF).validate(entity, v).ok()) {
                        throw new IllegalStateException("Prior value no longer valid.");
                    }
                    ((EntityProperty) propF).write(entity, v);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private String safeRead(EntityProperty prop, Entity entity) {
        try {
            Object v = ((EntityProperty) prop).read(entity);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    private void planAttributes(Map<String, Object> data, Entity entity, Player actor,
            List<PlannedOp> ops, java.util.function.Consumer<FieldPlan> track,
            List<String> warnings) {
        Object raw = sectionData(data, "attributes");
        if (raw == null) {
            return;
        }
        Map<String, Object> stored = (Map<String, Object>) raw;
        if (!(entity instanceof Attributable attr)) {
            warnings.add("Attributes need a living entity — section skipped.");
            for (String key : stored.keySet()) {
                track.accept(SnapshotPlan.planUnsupported("attributes", key, null,
                        "not attributable here — skipped"));
            }
            return;
        }
        if (actor != null && !plugin.permitted(actor,
                com.entitystudio.permission.StudioPermissions.DATA_EDIT)) {
            warnings.add("Missing permission for attributes — section skipped.");
            for (String key : stored.keySet()) {
                track.accept(SnapshotPlan.planUnsupported("attributes", key, null,
                        "no permission"));
            }
            return;
        }
        for (Map.Entry<String, Object> e : stored.entrySet()) {
            String key = e.getKey();
            Attribute match = null;
            for (Attribute a : Registry.ATTRIBUTE) {
                if (a.getKey().getKey().equalsIgnoreCase(key)) {
                    match = a;
                }
            }
            if (match == null) {
                track.accept(SnapshotPlan.planUnknown("attributes", key,
                        "no such attribute — never applied"));
                warnings.add("Unknown attribute '" + key + "' ignored.");
                continue;
            }
            double v;
            try {
                v = Double.parseDouble(String.valueOf(e.getValue()));
            } catch (NumberFormatException ex) {
                track.accept(new FieldPlan("attributes", key, currentAttr(attr, match),
                        String.valueOf(e.getValue()), Status.INVALID, "expected a number"));
                continue;
            }
            if (!Double.isFinite(v) || v < 0 || v > Double.MAX_VALUE) {
                track.accept(new FieldPlan("attributes", key, currentAttr(attr, match),
                        String.valueOf(e.getValue()), Status.INVALID, "out of range (0..max)"));
                continue;
            }
            final Attribute matchF = match;
            AttributeInstance inst = attr.getAttribute(matchF);
            if (inst == null) {
                track.accept(SnapshotPlan.planUnsupported("attributes", key, null,
                        "not present on this entity — skipped"));
                continue;
            }
            String current = String.valueOf(inst.getBaseValue());
            FieldPlan plan = SnapshotPlan.planField("attributes", key, current,
                    String.valueOf(v), true, null);
            track.accept(plan);
            if (plan.status() != Status.CHANGE) {
                continue;
            }
            AttributeInstance instF = inst;
            double vF = v;
            double oldF = inst.getBaseValue();
            ops.add(new PlannedOp() {
                public FieldPlan plan() {
                    return plan;
                }

                public void execute() {
                    instF.setBaseValue(vF);
                    if (actor != null && plugin.getConfig()
                            .getBoolean("settings.history.enabled", true)) {
                        plugin.history().record(new EditRecord(actor.getUniqueId(),
                                entity.getUniqueId(), entity.getType().name(), "Attribute " + key,
                                String.valueOf(oldF), String.valueOf(vF), true, "NORMAL", () -> {
                                    Entity live = Bukkit.getEntity(entity.getUniqueId());
                                    if (live instanceof Attributable a && live.isValid()
                                            && !live.isDead()) {
                                        AttributeInstance ai = a.getAttribute(matchF);
                                        if (ai != null) {
                                            ai.setBaseValue(oldF);
                                        }
                                    }
                                }));
                    }
                }

                public void revert() {
                    instF.setBaseValue(oldF);
                }
            });
        }
    }

    private static String currentAttr(Attributable attr, Attribute match) {
        AttributeInstance inst = attr.getAttribute(match);
        return inst == null ? null : String.valueOf(inst.getBaseValue());
    }

    @SuppressWarnings("unchecked")
    private void planEffects(Map<String, Object> data, Entity entity, Player actor,
            List<PlannedOp> ops, java.util.function.Consumer<FieldPlan> track,
            List<String> warnings) {
        Object raw = data.get("effects");
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?>)) {
            warnings.add("Effects section is malformed — skipped.");
            return;
        }
        if (!(entity instanceof LivingEntity living)) {
            warnings.add("Effects need a living entity — section skipped.");
            track.accept(SnapshotPlan.planUnsupported("effects", "all effects", null,
                    "not a living entity — skipped"));
            return;
        }
        if (actor != null && !plugin.permitted(actor,
                com.entitystudio.permission.StudioPermissions.DATA_EDIT)) {
            warnings.add("Missing permission for effects — section skipped.");
            track.accept(SnapshotPlan.planUnsupported("effects", "all effects", null,
                    "no permission"));
            return;
        }
        for (Object entry : (List<?>) raw) {
            if (!(entry instanceof Map)) {
                track.accept(new FieldPlan("effects", "malformed entry", "—", "—",
                        Status.INVALID, "not an effect map"));
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) plain(entry);
            String typeName = String.valueOf(m.getOrDefault("type", "?"));
            PotionEffectType type = resolveEffectType(typeName);
            if (type == null) {
                track.accept(new FieldPlan("effects", typeName, "—", "—",
                        Status.INVALID, "unknown effect type"));
                continue;
            }
            int dur;
            int amp;
            try {
                dur = Integer.parseInt(String.valueOf(m.getOrDefault("duration", "0")));
                amp = Integer.parseInt(String.valueOf(m.getOrDefault("amplifier", "0")));
            } catch (NumberFormatException e) {
                track.accept(new FieldPlan("effects", type.getKey().getKey(), "—", "—",
                        Status.INVALID, "duration/amplifier must be numbers"));
                continue;
            }
            if (dur < 1 || dur > 20 * 60 * 60 * 24 || amp < 0 || amp > 255) {
                track.accept(new FieldPlan("effects", type.getKey().getKey(), "—", "—",
                        Status.INVALID, "out of range (1..1728000 ticks, amp 0..255)"));
                continue;
            }
            PotionEffect before = living.getPotionEffect(type);
            String from = before == null ? "absent"
                    : before.getAmplifier() + "x" + before.getDuration() + "t";
            String to = amp + "x" + dur + "t";
            FieldPlan plan = SnapshotPlan.planField("effects", "Effect " + type.getKey().getKey(),
                    from, to, true, null);
            if (plan.status() == Status.UNCHANGED) {
                track.accept(plan);
                continue;
            }
            // Present section never removes unlisted effects (absent = unchanged);
            // each listed effect is added/replaced individually.
            track.accept(new FieldPlan(plan.section(), plan.label(), from, to, Status.CHANGE,
                    "listed effects are added, unlisted ones are left alone"));
            PotionEffect fx = new PotionEffect(type, dur, amp);
            PotionEffect beforeF = before == null ? null : new PotionEffect(before.getType(),
                    before.getDuration(), before.getAmplifier(), before.isAmbient(),
                    before.hasParticles(), before.hasIcon());
            ops.add(new PlannedOp() {
                public FieldPlan plan() {
                    return plan;
                }

                public void execute() {
                    living.addPotionEffect(fx);
                    if (actor != null && plugin.getConfig()
                            .getBoolean("settings.history.enabled", true)) {
                        plugin.history().record(new EditRecord(actor.getUniqueId(),
                                entity.getUniqueId(), entity.getType().name(),
                                "Add effect " + type.getKey().getKey(), from, to, true, "NORMAL",
                                () -> {
                                    Entity live = Bukkit.getEntity(entity.getUniqueId());
                                    if (live instanceof LivingEntity l && live.isValid()
                                            && !live.isDead()) {
                                        if (beforeF == null) {
                                            l.removePotionEffect(type);
                                        } else {
                                            l.addPotionEffect(beforeF);
                                        }
                                    }
                                }));
                    }
                }

                public void revert() {
                    if (beforeF == null) {
                        living.removePotionEffect(type);
                    } else {
                        living.addPotionEffect(beforeF);
                    }
                }
            });
        }
    }

    private static PotionEffectType resolveEffectType(String name) {
        for (PotionEffectType t : PotionEffectType.values()) {
            if (t.getKey().getKey().equalsIgnoreCase(name) || t.getName().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------- equipment section

    private static final String[] SLOT_KEYS =
            {"main-hand", "off-hand", "helmet", "chestplate", "leggings", "boots"};

    @SuppressWarnings("unchecked")
    private void planEquipment(Map<String, Object> data, Entity entity, Player actor,
            List<PlannedOp> ops, java.util.function.Consumer<FieldPlan> track,
            List<String> warnings) {
        Object raw = sectionData(data, "equipment");
        if (raw == null) {
            return;
        }
        Map<String, Object> stored = (Map<String, Object>) raw;
        if (!(entity instanceof LivingEntity living) || living.getEquipment() == null) {
            warnings.add("Equipment needs an entity with equipment slots — section skipped.");
            for (String key : stored.keySet()) {
                track.accept(SnapshotPlan.planUnsupported("equipment", key, null,
                        "no equipment slots here — skipped"));
            }
            return;
        }
        if (actor != null && !plugin.permitted(actor,
                com.entitystudio.permission.StudioPermissions.INV_EDIT)) {
            warnings.add("Missing permission for equipment — section skipped.");
            for (String key : stored.keySet()) {
                track.accept(SnapshotPlan.planUnsupported("equipment", key, null,
                        "no permission"));
            }
            return;
        }
        // Unknown slot keys are reported, never applied.
        for (String key : stored.keySet()) {
            boolean known = false;
            for (String s : SLOT_KEYS) {
                if (s.equals(key)) {
                    known = true;
                }
            }
            if (!known) {
                track.accept(SnapshotPlan.planUnknown("equipment", key,
                        "not an equipment slot — never applied"));
                warnings.add("Unknown equipment slot '" + key + "' ignored.");
            }
        }
        // Validate every present slot BEFORE any mutation.
        Map<String, ItemStack> parsed = new LinkedHashMap<>();
        Map<String, FieldPlan> slotPlans = new LinkedHashMap<>();
        boolean[] ok = {true};
        for (String key : SLOT_KEYS) {
            if (!stored.containsKey(key)) {
                continue; // absent = unchanged
            }
            Object v = stored.get(key);
            String current = currentSlot(living, key);
            if (SnapshotPlan.isEmptySlot(v)) {
                FieldPlan p = current == null
                        ? new FieldPlan("equipment", "Slot " + key, "empty", "empty",
                                Status.UNCHANGED, "already empty")
                        : new FieldPlan("equipment", "Slot " + key, current, "empty",
                                Status.CHANGE, "explicit clear (" + SnapshotPlan.EMPTY_SLOT + ")");
                track.accept(p);
                slotPlans.put(key, p);
                parsed.put(key, null);
                continue;
            }
            ItemStack stack = deserializeSlot(key, v, warnings, track, current);
            if (stack == null) {
                ok[0] = false;
                continue;
            }
            parsed.put(key, stack);
            FieldPlan p = new FieldPlan("equipment", "Slot " + key, current,
                    stack.getType().name().toLowerCase(), Status.CHANGE, "");
            track.accept(p);
            slotPlans.put(key, p);
        }
        if (!ok[0]) {
            warnings.add("Equipment section has invalid entries — valid slots still apply;"
                    + " invalid ones are skipped.");
        }
        if (parsed.isEmpty()) {
            return;
        }
        ops.add(new PlannedOp() {
            ItemStack[] before;

            public FieldPlan plan() {
                return new FieldPlan("equipment", "Equipment set (" + parsed.size() + " slot(s))",
                        "current loadout", "preset loadout", Status.CHANGE, "");
            }

            public void execute() {
                before = snapshotLoadout(living);
                EntityEquipment eq = living.getEquipment();
                for (Map.Entry<String, ItemStack> e : parsed.entrySet()) {
                    setSlotByKey(eq, e.getKey(), e.getValue() == null ? null : e.getValue().clone());
                }
                recordEquipmentRestore(actor, entity, before);
            }

            public void revert() {
                restoreLoadout(living, before);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private ItemStack deserializeSlot(String key, Object v, List<String> warnings,
            java.util.function.Consumer<FieldPlan> track, String current) {
        try {
            if (v instanceof Map<?, ?> map) {
                return ItemStack.deserialize((Map<String, Object>) map);
            }
            track.accept(new FieldPlan("equipment", "Slot " + key, current, String.valueOf(v),
                    Status.INVALID, "not an item map or " + SnapshotPlan.EMPTY_SLOT));
            warnings.add("Equipment slot '" + key + "' is malformed — skipped.");
            return null;
        } catch (Exception e) {
            track.accept(new FieldPlan("equipment", "Slot " + key, current, "?", Status.INVALID,
                    "cannot deserialize: " + e.getMessage()));
            warnings.add("Equipment slot '" + key + "' cannot be read — skipped.");
            return null;
        }
    }

    private static String currentSlot(LivingEntity living, String key) {
        EntityEquipment eq = living.getEquipment();
        if (eq == null) {
            return null;
        }
        ItemStack s = switch (key) {
            case "main-hand" -> eq.getItemInMainHand();
            case "off-hand" -> eq.getItemInOffHand();
            case "helmet" -> eq.getHelmet();
            case "chestplate" -> eq.getChestplate();
            case "leggings" -> eq.getLeggings();
            case "boots" -> eq.getBoots();
            default -> null;
        };
        return s == null || s.getType().isAir() ? null : s.getType().name().toLowerCase();
    }

    private static void setSlotByKey(EntityEquipment eq, String key, ItemStack stack) {
        switch (key) {
            case "main-hand" -> eq.setItemInMainHand(stack);
            case "off-hand" -> eq.setItemInOffHand(stack);
            case "helmet" -> eq.setHelmet(stack);
            case "chestplate" -> eq.setChestplate(stack);
            case "leggings" -> eq.setLeggings(stack);
            case "boots" -> eq.setBoots(stack);
            default -> {
            }
        }
    }

    private ItemStack[] snapshotLoadout(LivingEntity living) {
        ItemStack[] snap = new ItemStack[6];
        EntityEquipment eq = living.getEquipment();
        if (eq == null) {
            return snap;
        }
        ItemStack[] live = {eq.getItemInMainHand(), eq.getItemInOffHand(), eq.getHelmet(),
                eq.getChestplate(), eq.getLeggings(), eq.getBoots()};
        for (int i = 0; i < 6; i++) {
            snap[i] = live[i] == null || live[i].getType().isAir() ? null : live[i].clone();
        }
        return snap;
    }

    private void restoreLoadout(LivingEntity living, ItemStack[] snap) {
        EntityEquipment eq = living.getEquipment();
        if (eq == null || snap == null) {
            return;
        }
        eq.setItemInMainHand(snap[0] == null ? null : snap[0].clone());
        eq.setItemInOffHand(snap[1] == null ? null : snap[1].clone());
        eq.setHelmet(snap[2] == null ? null : snap[2].clone());
        eq.setChestplate(snap[3] == null ? null : snap[3].clone());
        eq.setLeggings(snap[4] == null ? null : snap[4].clone());
        eq.setBoots(snap[5] == null ? null : snap[5].clone());
    }

    /** Single undoable record for a bulk equipment replacement. */
    private void recordEquipmentRestore(Player actor, Entity entity, ItemStack[] before) {
        if (actor == null) {
            return;
        }
        ItemStack[] snap = new ItemStack[6];
        for (int i = 0; i < 6; i++) {
            snap[i] = before[i] == null ? null : before[i].clone();
        }
        plugin.history().record(new EditRecord(actor.getUniqueId(), entity.getUniqueId(),
                entity.getType().name(), "Restore equipment set",
                describeLoadout(snap), "preset loadout", true, "ADVANCED", () -> {
                    Entity e = Bukkit.getEntity(entity.getUniqueId());
                    if (!(e instanceof LivingEntity l) || !e.isValid() || e.isDead()) {
                        throw new IllegalStateException("Entity is gone.");
                    }
                    restoreLoadout(l, snap);
                }));
    }

    private String describeLoadout(ItemStack[] snap) {
        String[] names = {"hand", "off", "helm", "chest", "legs", "boots"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snap.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names[i]).append(":")
                    .append(snap[i] == null ? "empty" : snap[i].getType().name().toLowerCase());
        }
        return sb.toString();
    }

    // -------------------------------------------------------- behavior section

    @SuppressWarnings("unchecked")
    private void planBehavior(Map<String, Object> data, Entity entity, Player actor,
            List<PlannedOp> ops, java.util.function.Consumer<FieldPlan> track,
            List<String> warnings) {
        Object raw = sectionData(data, "behavior");
        if (raw == null) {
            return;
        }
        Map<String, Object> stored = (Map<String, Object>) raw;
        if (actor != null && !plugin.permitted(actor,
                com.entitystudio.permission.StudioPermissions.ALLEGIANCE)) {
            warnings.add("Missing permission for behavior — section skipped.");
            for (String key : stored.keySet()) {
                track.accept(SnapshotPlan.planUnsupported("behavior", key, null,
                        "no permission"));
            }
            return;
        }
        boolean isMob = entity instanceof Mob;
        // Unknown behavior keys are reported, never applied.
        Set<String> known = Set.of("profile", "ai-enabled", "owner", "trusted", "enemies",
                "allowed-types", "excluded-types", "defend-owner", "defend-allies", "retaliate",
                "attack-owner-target", "pursue-outside-range", "clear-invalid-target",
                "friendly-fire", "anchor");
        for (String key : stored.keySet()) {
            if (!known.contains(key)) {
                track.accept(SnapshotPlan.planUnknown("behavior", key,
                        "not a behavior field — never applied"));
                warnings.add("Unknown behavior field '" + key + "' ignored.");
            }
        }
        ManagedRecord live = plugin.allegiance().getOrCreate(entity);
        BehaviorTarget target = parseBehaviorTarget(stored, isMob, live, track, warnings);
        if (target == null) {
            return; // every present field was invalid/unsupported
        }
        if (target.empty()) {
            return; // all present fields already match
        }
        ops.add(new PlannedOp() {
            BehaviorSnapshot before;
            boolean beforeAi;
            boolean beforeAware;

            public FieldPlan plan() {
                return new FieldPlan("behavior", "Behavior (" + target.describe() + ")",
                        "current rules", "preset rules", Status.CHANGE, target.note);
            }

            public void execute() {
                before = BehaviorSnapshot.take(live);
                beforeAi = entity instanceof LivingEntity l && l.hasAI();
                beforeAware = entity instanceof Mob m && m.isAware();
                target.apply(entity, live);
                recordBehaviorRestore(actor, entity, before, beforeAi, beforeAware);
            }

            public void revert() {
                before.restore(live);
                if (entity instanceof LivingEntity l) {
                    l.setAI(beforeAi);
                }
                if (entity instanceof Mob m) {
                    m.setAware(beforeAware);
                }
            }
        });
    }

    /** Validated behavior change: every field parsed before anything mutates. */
    private static final class BehaviorTarget {
        ManagedRecord.Profile profile;
        boolean profileSet;
        Boolean aiEnabled;
        UUID owner;
        boolean ownerSet;
        Set<UUID> trusted;
        Set<UUID> enemies;
        Set<String> allowedTypes;
        Set<String> excludedTypes;
        final Map<String, Boolean> flags = new LinkedHashMap<>();
        boolean anchorSet;
        boolean anchored;
        boolean hard;
        String world;
        double x, y, z;
        float yaw, pitch;
        String note = "";

        boolean empty() {
            return !profileSet && aiEnabled == null && !ownerSet && trusted == null
                    && enemies == null && allowedTypes == null && excludedTypes == null
                    && flags.isEmpty() && !anchorSet;
        }

        String describe() {
            List<String> parts = new ArrayList<>();
            if (profileSet) {
                parts.add("profile " + profile.name().toLowerCase());
            }
            if (!flags.isEmpty()) {
                parts.add(flags.size() + " rule(s)");
            }
            if (ownerSet) {
                parts.add("owner");
            }
            if (trusted != null || enemies != null) {
                parts.add("lists");
            }
            if (allowedTypes != null || excludedTypes != null) {
                parts.add("filters");
            }
            if (anchorSet) {
                parts.add("anchor");
            }
            if (aiEnabled != null) {
                parts.add("ai");
            }
            return parts.isEmpty() ? "no changes" : String.join(", ", parts);
        }

        /** Applies profile first (coherent brain baseline), then stored scalars. */
        void apply(Entity entity, ManagedRecord rec) {
            if (profileSet && entity instanceof Mob m) {
                AllegianceService.applyProfile(m, profile, rec);
            }
            if (aiEnabled != null) {
                rec.aiEnabled(aiEnabled);
                if (entity instanceof LivingEntity l) {
                    l.setAI(aiEnabled);
                }
            }
            if (ownerSet) {
                rec.owner(owner);
            }
            if (trusted != null) {
                rec.trusted().clear();
                rec.trusted().addAll(trusted);
            }
            if (enemies != null) {
                rec.enemies().clear();
                rec.enemies().addAll(enemies);
            }
            if (allowedTypes != null) {
                rec.allowedTypes().clear();
                rec.allowedTypes().addAll(allowedTypes);
            }
            if (excludedTypes != null) {
                rec.excludedTypes().clear();
                rec.excludedTypes().addAll(excludedTypes);
            }
            for (Map.Entry<String, Boolean> f : flags.entrySet()) {
                setFlag(rec, f.getKey(), f.getValue());
            }
            if (anchorSet) {
                rec.setAnchor(anchored, hard, world, x, y, z, yaw, pitch);
            }
        }

        static void setFlag(ManagedRecord rec, String key, boolean v) {
            switch (key) {
                case "defend-owner" -> rec.defendOwner(v);
                case "defend-allies" -> rec.defendAllies(v);
                case "retaliate" -> rec.retaliate(v);
                case "attack-owner-target" -> rec.attackOwnerTarget(v);
                case "pursue-outside-range" -> rec.pursueOutsideRange(v);
                case "clear-invalid-target" -> rec.clearInvalidTarget(v);
                case "friendly-fire" -> rec.friendlyFireProtect(v);
                default -> {
                }
            }
        }
    }

    /** Full snapshot of a managed record + brain state for undo/rollback. */
    private record BehaviorSnapshot(ManagedRecord.Profile profile, boolean aiEnabled, UUID owner,
            Set<UUID> trusted, Set<UUID> enemies, Set<String> allowed, Set<String> excluded,
            boolean defendOwner, boolean defendAllies, boolean retaliate, boolean joinAttack,
            boolean pursue, boolean clearInvalid, boolean friendlyFire,
            boolean anchored, boolean hard, String world,
            double x, double y, double z, float yaw, float pitch) {

        static BehaviorSnapshot take(ManagedRecord r) {
            return new BehaviorSnapshot(r.profile(), r.aiEnabled(), r.owner(),
                    new LinkedHashSet<>(r.trusted()), new LinkedHashSet<>(r.enemies()),
                    new LinkedHashSet<>(r.allowedTypes()), new LinkedHashSet<>(r.excludedTypes()),
                    r.defendOwner(), r.defendAllies(), r.retaliate(), r.attackOwnerTarget(),
                    r.pursueOutsideRange(), r.clearInvalidTarget(), r.friendlyFireProtect(),
                    r.anchored(), r.hardAnchor(), r.anchorWorld(), r.anchorX(), r.anchorY(),
                    r.anchorZ(), r.anchorYaw(), r.anchorPitch());
        }

        void restore(ManagedRecord r) {
            r.profile(profile);
            r.aiEnabled(aiEnabled);
            r.owner(owner);
            r.trusted().clear();
            r.trusted().addAll(trusted);
            r.enemies().clear();
            r.enemies().addAll(enemies);
            r.allowedTypes().clear();
            r.allowedTypes().addAll(allowed);
            r.excludedTypes().clear();
            r.excludedTypes().addAll(excluded);
            r.defendOwner(defendOwner);
            r.defendAllies(defendAllies);
            r.retaliate(retaliate);
            r.attackOwnerTarget(joinAttack);
            r.pursueOutsideRange(pursue);
            r.clearInvalidTarget(clearInvalid);
            r.friendlyFireProtect(friendlyFire);
            r.setAnchor(anchored, hard, world, x, y, z, yaw, pitch);
        }
    }

    @SuppressWarnings("unchecked")
    private BehaviorTarget parseBehaviorTarget(Map<String, Object> stored, boolean isMob,
            ManagedRecord current,
            java.util.function.Consumer<FieldPlan> track, List<String> warnings) {
        BehaviorTarget t = new BehaviorTarget();
        // profile
        if (stored.containsKey("profile")) {
            String name = String.valueOf(stored.get("profile"));
            try {
                ManagedRecord.Profile p = ManagedRecord.Profile.valueOf(name.toUpperCase());
                if (!isMob) {
                    track.accept(SnapshotPlan.planUnsupported("behavior", "profile " + name,
                            current.profile().name(), "profiles need a mob — skipped"));
                } else if (p == current.profile()) {
                    track.accept(new FieldPlan("behavior", "profile " + name.toLowerCase(),
                            p.name(), p.name(), Status.UNCHANGED, "already set"));
                } else {
                    t.profile = p;
                    t.profileSet = true;
                    track.accept(new FieldPlan("behavior", "profile " + name.toLowerCase(),
                            current.profile().name().toLowerCase(), name.toLowerCase(),
                            Status.CHANGE, "applies brain baseline, then stored rules"));
                }
            } catch (IllegalArgumentException e) {
                track.accept(new FieldPlan("behavior", "profile", current.profile().name(), name,
                        Status.INVALID, "unknown profile"));
                warnings.add("Unknown behavior profile '" + name + "' — skipped.");
            }
        }
        // ai-enabled
        if (stored.containsKey("ai-enabled")) {
            Object v = stored.get("ai-enabled");
            if (!(v instanceof Boolean)) {
                track.accept(new FieldPlan("behavior", "ai-enabled", String.valueOf(current.aiEnabled()),
                        String.valueOf(v), Status.INVALID, "expected true/false"));
            } else if ((Boolean) v == current.aiEnabled()) {
                track.accept(new FieldPlan("behavior", "ai-enabled", String.valueOf(v),
                        String.valueOf(v), Status.UNCHANGED, "already set"));
            } else {
                t.aiEnabled = (Boolean) v;
                track.accept(SnapshotPlan.planField("behavior", "ai-enabled",
                        String.valueOf(current.aiEnabled()), String.valueOf(v), true, null));
            }
        }
        // owner (__none__ is the intentional clear)
        if (stored.containsKey("owner")) {
            Object v = stored.get("owner");
            if (SnapshotPlan.isNoOwner(v)) {
                if (current.owner() == null) {
                    track.accept(new FieldPlan("behavior", "owner", "none", "none",
                            Status.UNCHANGED, "already empty"));
                } else {
                    t.owner = null;
                    t.ownerSet = true;
                    track.accept(new FieldPlan("behavior", "owner", shortId(current.owner()),
                            "none", Status.CHANGE, "explicit clear"));
                }
            } else {
                try {
                    UUID id = UUID.fromString(String.valueOf(v));
                    if (id.equals(current.owner())) {
                        track.accept(new FieldPlan("behavior", "owner", shortId(id), shortId(id),
                                Status.UNCHANGED, "already set"));
                    } else {
                        t.owner = id;
                        t.ownerSet = true;
                        track.accept(SnapshotPlan.planField("behavior", "owner",
                                current.owner() == null ? "none" : shortId(current.owner()),
                                shortId(id), true, null));
                    }
                } catch (IllegalArgumentException e) {
                    track.accept(new FieldPlan("behavior", "owner",
                            current.owner() == null ? "none" : shortId(current.owner()),
                            String.valueOf(v), Status.INVALID, "not a UUID"));
                    warnings.add("Behavior owner '" + v + "' is not a UUID — skipped.");
                }
            }
        }
        // UUID lists (trusted/enemies): present (even empty) replaces; absent leaves.
        parseUuidList(stored, "trusted", current.trusted(), track, warnings, ids -> t.trusted = ids);
        parseUuidList(stored, "enemies", current.enemies(), track, warnings, ids -> t.enemies = ids);
        // Entity-type filters: every entry must name a real EntityType.
        parseTypeList(stored, "allowed-types", current.allowedTypes(), track, warnings,
                s -> t.allowedTypes = s);
        parseTypeList(stored, "excluded-types", current.excludedTypes(), track, warnings,
                s -> t.excludedTypes = s);
        // Boolean flags.
        for (String key : List.of("defend-owner", "defend-allies", "retaliate",
                "attack-owner-target", "pursue-outside-range", "clear-invalid-target",
                "friendly-fire")) {
            if (!stored.containsKey(key)) {
                continue;
            }
            Object v = stored.get(key);
            if (!(v instanceof Boolean)) {
                track.accept(new FieldPlan("behavior", key, String.valueOf(currentFlag(current, key)),
                        String.valueOf(v), Status.INVALID, "expected true/false"));
                warnings.add("Behavior flag '" + key + "' is not true/false — skipped.");
                continue;
            }
            if ((Boolean) v == currentFlag(current, key)) {
                track.accept(new FieldPlan("behavior", key, String.valueOf(v), String.valueOf(v),
                        Status.UNCHANGED, "already set"));
            } else {
                t.flags.put(key, (Boolean) v);
                track.accept(SnapshotPlan.planField("behavior", key,
                        String.valueOf(currentFlag(current, key)), String.valueOf(v), true, null));
            }
        }
        // Anchor: absent key leaves the anchor alone; anchored:false clears it.
        if (stored.containsKey("anchor")) {
            Object v = stored.get("anchor");
            if (!(v instanceof Map)) {
                track.accept(new FieldPlan("behavior", "anchor", current.anchored() ? "set" : "none",
                        String.valueOf(v), Status.INVALID, "not an anchor map"));
                warnings.add("Behavior anchor is malformed — skipped.");
            } else {
                Map<String, Object> a = (Map<String, Object>) v;
                Object anchoredRaw = a.getOrDefault("anchored", false);
                if (!(anchoredRaw instanceof Boolean)) {
                    track.accept(new FieldPlan("behavior", "anchor", "?", "?", Status.INVALID,
                            "anchored must be true/false"));
                    warnings.add("Behavior anchor flag is malformed — skipped.");
                } else if (!(Boolean) anchoredRaw) {
                    if (!current.anchored()) {
                        track.accept(new FieldPlan("behavior", "anchor", "none", "none",
                                Status.UNCHANGED, "already unanchored"));
                    } else {
                        t.anchorSet = true;
                        t.anchored = false;
                        track.accept(new FieldPlan("behavior", "anchor", "set", "none",
                                Status.CHANGE, "explicit clear"));
                    }
                } else {
                    String world = String.valueOf(a.getOrDefault("world", ""));
                    if (Bukkit.getWorld(world) == null) {
                        track.accept(new FieldPlan("behavior", "anchor", "?", world,
                                Status.INVALID, "world not loaded"));
                        warnings.add("Anchor world '" + world + "' is not loaded — skipped.");
                    } else {
                        try {
                            double x = Double.parseDouble(String.valueOf(a.get("x")));
                            double y = Double.parseDouble(String.valueOf(a.get("y")));
                            double z = Double.parseDouble(String.valueOf(a.get("z")));
                            float yaw = Float.parseFloat(
                                    String.valueOf(a.getOrDefault("yaw", "0")));
                            float pitch = Float.parseFloat(
                                    String.valueOf(a.getOrDefault("pitch", "0")));
                            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                                throw new NumberFormatException("non-finite");
                            }
                            t.anchorSet = true;
                            t.anchored = true;
                            t.hard = Boolean.parseBoolean(String.valueOf(a.getOrDefault("hard", "false")));
                            t.world = world;
                            t.x = x;
                            t.y = y;
                            t.z = z;
                            t.yaw = yaw;
                            t.pitch = pitch;
                            t.note = "restores a saved position — verify it is still safe";
                            track.accept(new FieldPlan("behavior", "anchor",
                                    current.anchored() ? "set" : "none",
                                    (t.hard ? "hard@" : "soft@") + world, Status.CHANGE,
                                    "restores a saved position — verify it is still safe"));
                        } catch (NumberFormatException | NullPointerException e) {
                            track.accept(new FieldPlan("behavior", "anchor", "?", "?",
                                    Status.INVALID, "coordinates must be numbers"));
                            warnings.add("Anchor coordinates are malformed — skipped.");
                        }
                    }
                }
            }
        }
        return t;
    }

    private static boolean currentFlag(ManagedRecord r, String key) {
        return switch (key) {
            case "defend-owner" -> r.defendOwner();
            case "defend-allies" -> r.defendAllies();
            case "retaliate" -> r.retaliate();
            case "attack-owner-target" -> r.attackOwnerTarget();
            case "pursue-outside-range" -> r.pursueOutsideRange();
            case "clear-invalid-target" -> r.clearInvalidTarget();
            case "friendly-fire" -> r.friendlyFireProtect();
            default -> false;
        };
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "none";
        }
        String s = id.toString();
        return s.length() > 8 ? s.substring(0, 8) : s;
    }

    @SuppressWarnings("unchecked")
    private void parseUuidList(Map<String, Object> stored, String key, Set<UUID> current,
            java.util.function.Consumer<FieldPlan> track, List<String> warnings,
            java.util.function.Consumer<Set<UUID>> set) {
        if (!stored.containsKey(key)) {
            return;
        }
        Object v = stored.get(key);
        if (!(v instanceof List<?>)) {
            track.accept(new FieldPlan("behavior", key, describeIds(current), String.valueOf(v),
                    Status.INVALID, "expected a list"));
            warnings.add("Behavior list '" + key + "' is malformed — skipped.");
            return;
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (Object e : (List<?>) v) {
            try {
                ids.add(UUID.fromString(String.valueOf(e)));
            } catch (IllegalArgumentException ex) {
                track.accept(new FieldPlan("behavior", key, describeIds(current), String.valueOf(e),
                        Status.INVALID, "not a UUID: " + e));
                warnings.add("Behavior list '" + key + "' contains a bad UUID — field skipped.");
                return;
            }
        }
        if (ids.equals(current)) {
            track.accept(new FieldPlan("behavior", key, describeIds(current), describeIds(ids),
                    Status.UNCHANGED, "already matches"));
        } else {
            set.accept(ids);
            track.accept(SnapshotPlan.planField("behavior", key, describeIds(current),
                    describeIds(ids), true, null));
        }
    }

    private static String describeIds(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return "empty";
        }
        List<String> shorts = new ArrayList<>();
        for (UUID id : ids) {
            shorts.add(shortId(id));
        }
        shorts.sort(String::compareTo);
        return String.join(",", shorts);
    }

    private void parseTypeList(Map<String, Object> stored, String key, Set<String> current,
            java.util.function.Consumer<FieldPlan> track, List<String> warnings,
            java.util.function.Consumer<Set<String>> set) {
        if (!stored.containsKey(key)) {
            return;
        }
        Object v = stored.get(key);
        if (!(v instanceof List<?>)) {
            track.accept(new FieldPlan("behavior", key, String.join(",", current),
                    String.valueOf(v), Status.INVALID, "expected a list"));
            warnings.add("Behavior list '" + key + "' is malformed — skipped.");
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object e : (List<?>) v) {
            String name = String.valueOf(e).toUpperCase();
            try {
                EntityType.valueOf(name);
            } catch (IllegalArgumentException ex) {
                track.accept(new FieldPlan("behavior", key, String.join(",", current), name,
                        Status.INVALID, "unknown entity type"));
                warnings.add("Behavior filter '" + key + "' names unknown type '" + e
                        + "' — field skipped.");
                return;
            }
            names.add(name);
        }
        if (names.equals(current)) {
            track.accept(new FieldPlan("behavior", key, String.join(",", current),
                    names.isEmpty() ? "any" : String.join(",", names), Status.UNCHANGED,
                    "already matches"));
        } else {
            set.accept(names);
            track.accept(SnapshotPlan.planField("behavior", key,
                    current.isEmpty() ? "any" : String.join(",", current),
                    names.isEmpty() ? "any" : String.join(",", names), true, null));
        }
    }

    /** Full undoable record for a behavior restore (all fields + brain state). */
    private void recordBehaviorRestore(Player actor, Entity entity, BehaviorSnapshot old,
            boolean oldBrainAi, boolean oldBrainAware) {
        if (actor == null) {
            return;
        }
        plugin.history().record(new EditRecord(actor.getUniqueId(), entity.getUniqueId(),
                entity.getType().name(), "Restore behavior profile",
                old.profile().name(), "preset profile", true, "NORMAL", () -> {
                    Entity e = Bukkit.getEntity(entity.getUniqueId());
                    if (e == null || !e.isValid() || e.isDead()) {
                        throw new IllegalStateException("Entity is gone.");
                    }
                    ManagedRecord r = plugin.allegiance().getOrCreate(e);
                    old.restore(r);
                    if (e instanceof LivingEntity l) {
                        l.setAI(oldBrainAi);
                    }
                    if (e instanceof Mob m) {
                        m.setAware(oldBrainAware);
                    }
                }));
    }
}
