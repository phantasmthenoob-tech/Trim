package com.entitystudio.data;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Reads everything the public Bukkit/Paper API exposes about an entity:
 *
 * <ul>
 *   <li>identity — class, type, ids, world/position, vitals facts;</li>
 *   <li>tags — scoreboard tags;</li>
 *   <li>pdc — the {@link PersistentDataContainer} (plugin/custom data);</li>
 *   <li>snbt — the full entity data via {@link Entity#getAsString()}
 *       (present in the pinned Paper 1.21.11 API), parsed for display.</li>
 * </ul>
 *
 * <p>Every getter is isolated: one failing section never blanks the others,
 * and failures become UNAVAILABLE rows — never invented values. No NMS, no
 * reflection, no writes of any kind.
 */
public final class PublicApiInspector implements DataInspector {

    public static final PublicApiInspector INSTANCE = new PublicApiInspector();

    /** Per-section row budgets; the GUI paginates whatever is returned. */
    static final int TAG_LIMIT = 120;
    static final int PDC_LIMIT = 250;
    static final int SNBT_LIMIT = 500;

    private PublicApiInspector() {
    }

    @Override
    public List<DataField> inspect(Entity entity, boolean canSeePrivateData) {
        List<DataField> out = new ArrayList<>();
        identity(out, entity);
        boolean playerHidden = entity instanceof Player && !canSeePrivateData;
        tags(out, entity, playerHidden);
        pdc(out, entity, playerHidden);
        snbt(out, entity, playerHidden);
        return out;
    }

    // ------------------------------------------------------------ identity

    private static void identity(List<DataField> out, Entity entity) {
        id(out, "class", "string", () -> entity.getClass().getName());
        id(out, "type", "string", () -> {
            var t = entity.getType();
            return t == null ? null : t.name().toLowerCase();
        });
        id(out, "entity-id", "int", () -> String.valueOf(entity.getEntityId()));
        id(out, "uuid", "string", () -> {
            var u = entity.getUniqueId();
            return u == null ? null : u.toString();
        });
        id(out, "world", "string", () -> {
            var w = entity.getWorld();
            return w == null ? null : w.getName();
        });
        id(out, "location", "string", () -> {
            var l = entity.getLocation();
            if (l == null) {
                return null;
            }
            return String.format("%.1f, %.1f, %.1f", l.getX(), l.getY(), l.getZ());
        });
        id(out, "ticks-lived", "int", () -> String.valueOf(entity.getTicksLived()));
        id(out, "velocity", "string", () -> {
            var v = entity.getVelocity();
            return v == null ? null
                    : String.format("%.2f, %.2f, %.2f", v.getX(), v.getY(), v.getZ());
        });
        id(out, "passengers", "int", () -> {
            var p = entity.getPassengers();
            return p == null ? null : String.valueOf(p.size());
        });
        id(out, "vehicle", "string", () -> {
            var v = entity.getVehicle();
            if (v == null) {
                return "none";
            }
            var t = v.getType();
            return t == null ? "unknown" : t.name().toLowerCase();
        });
        id(out, "portal-cooldown", "int", () -> String.valueOf(entity.getPortalCooldown()));
    }

    private static void id(List<DataField> out, String path, String type, Supplier<String> read) {
        String value;
        try {
            value = read.get();
        } catch (Exception e) {
            value = null;
        }
        if (value == null) {
            out.add(new DataField("identity", path, type, "<unavailable>",
                    DataStatus.UNAVAILABLE, DataField.PUBLIC_API));
        } else {
            out.add(new DataField("identity", path, type,
                    DataBrowser.truncate(value, DataBrowser.DISPLAY_CHARS),
                    DataStatus.READABLE, DataField.PUBLIC_API));
        }
    }

    // ---------------------------------------------------------------- tags

    private static void tags(List<DataField> out, Entity entity, boolean hidden) {
        if (hidden) {
            out.add(new DataField("tags", "tags", "list", "<redacted>",
                    DataStatus.REDACTED, DataField.PUBLIC_API));
            return;
        }
        Set<String> tags;
        try {
            tags = entity.getScoreboardTags();
        } catch (Exception e) {
            tags = null;
        }
        if (tags == null) {
            out.add(new DataField("tags", "tags", "list", "<unavailable>",
                    DataStatus.UNAVAILABLE, DataField.PUBLIC_API));
            return;
        }
        if (tags.isEmpty()) {
            out.add(new DataField("tags", "tags", "list", "(none)",
                    DataStatus.READABLE, DataField.PUBLIC_API));
            return;
        }
        int i = 0;
        for (String tag : tags) {
            if (i >= TAG_LIMIT) {
                out.add(new DataField("tags", "tags[…]", "list",
                        "+" + (tags.size() - i) + " more", DataStatus.READABLE,
                        DataField.PUBLIC_API));
                return;
            }
            out.add(new DataField("tags", "tags[" + i + "]", "string",
                    DataBrowser.truncate(tag == null ? "null" : tag,
                            DataBrowser.DISPLAY_CHARS),
                    DataStatus.READABLE, DataField.PUBLIC_API));
            i++;
        }
    }

    // ----------------------------------------------------------------- pdc

    private static void pdc(List<DataField> out, Entity entity, boolean hidden) {
        if (hidden) {
            out.add(new DataField("pdc", "pdc", "compound", "<redacted>",
                    DataStatus.REDACTED, DataField.PUBLIC_API));
            return;
        }
        PersistentDataContainer container;
        try {
            container = entity.getPersistentDataContainer();
        } catch (Exception e) {
            container = null;
        }
        if (container == null) {
            out.add(new DataField("pdc", "pdc", "compound", "<unavailable>",
                    DataStatus.UNAVAILABLE, DataField.PUBLIC_API));
            return;
        }
        Set<NamespacedKey> keys;
        try {
            keys = container.getKeys();
        } catch (Exception e) {
            keys = null;
        }
        if (keys == null) {
            out.add(new DataField("pdc", "pdc", "compound", "<unavailable>",
                    DataStatus.UNAVAILABLE, DataField.PUBLIC_API));
            return;
        }
        if (keys.isEmpty()) {
            out.add(new DataField("pdc", "pdc", "compound", "(empty)",
                    DataStatus.READABLE, DataField.PUBLIC_API));
            return;
        }
        Map<String, Object> tree = new LinkedHashMap<>();
        for (NamespacedKey key : keys) {
            if (key == null) {
                continue;
            }
            tree.put("pdc." + key.getNamespace() + ":" + key.getKey(),
                    readPdcValue(container, key, 0));
            if (tree.size() >= PDC_LIMIT) {
                break;
            }
        }
        for (DataField f : DataBrowser.flatten(tree, "pdc", PDC_LIMIT)) {
            out.add(DataBrowser.redactIfSensitive(f));
        }
    }

    /** Reads one PDC key with the richest public type that matches. Never throws. */
    static Object readPdcValue(PersistentDataContainer c, NamespacedKey key, int depth) {
        try {
            if (c.has(key, PersistentDataType.STRING)) {
                return c.get(key, PersistentDataType.STRING);
            }
            if (c.has(key, PersistentDataType.INTEGER)) {
                return c.get(key, PersistentDataType.INTEGER);
            }
            if (c.has(key, PersistentDataType.LONG)) {
                return c.get(key, PersistentDataType.LONG);
            }
            if (c.has(key, PersistentDataType.DOUBLE)) {
                return c.get(key, PersistentDataType.DOUBLE);
            }
            if (c.has(key, PersistentDataType.FLOAT)) {
                return c.get(key, PersistentDataType.FLOAT);
            }
            if (c.has(key, PersistentDataType.SHORT)) {
                return c.get(key, PersistentDataType.SHORT);
            }
            if (c.has(key, PersistentDataType.BYTE)) {
                return c.get(key, PersistentDataType.BYTE);
            }
            if (c.has(key, PersistentDataType.BOOLEAN)) {
                return c.get(key, PersistentDataType.BOOLEAN);
            }
            if (c.has(key, PersistentDataType.INTEGER_ARRAY)) {
                return c.get(key, PersistentDataType.INTEGER_ARRAY);
            }
            if (c.has(key, PersistentDataType.LONG_ARRAY)) {
                return c.get(key, PersistentDataType.LONG_ARRAY);
            }
            if (c.has(key, PersistentDataType.BYTE_ARRAY)) {
                return c.get(key, PersistentDataType.BYTE_ARRAY);
            }
            if (c.has(key, PersistentDataType.LIST.strings())) {
                return c.get(key, PersistentDataType.LIST.strings());
            }
            if (c.has(key, PersistentDataType.LIST.integers())) {
                return c.get(key, PersistentDataType.LIST.integers());
            }
            if (c.has(key, PersistentDataType.LIST.longs())) {
                return c.get(key, PersistentDataType.LIST.longs());
            }
            if (c.has(key, PersistentDataType.LIST.doubles())) {
                return c.get(key, PersistentDataType.LIST.doubles());
            }
            if (c.has(key, PersistentDataType.LIST.floats())) {
                return c.get(key, PersistentDataType.LIST.floats());
            }
            if (c.has(key, PersistentDataType.LIST.shorts())) {
                return c.get(key, PersistentDataType.LIST.shorts());
            }
            if (c.has(key, PersistentDataType.LIST.bytes())) {
                return c.get(key, PersistentDataType.LIST.bytes());
            }
            if (c.has(key, PersistentDataType.LIST.booleans())) {
                return c.get(key, PersistentDataType.LIST.booleans());
            }
            if (depth < 2 && c.has(key, PersistentDataType.TAG_CONTAINER)) {
                PersistentDataContainer nested =
                        c.get(key, PersistentDataType.TAG_CONTAINER);
                if (nested != null) {
                    Map<String, Object> sub = new LinkedHashMap<>();
                    for (NamespacedKey k : nested.getKeys()) {
                        if (k != null) {
                            sub.put(k.getNamespace() + ":" + k.getKey(),
                                    readPdcValue(nested, k, depth + 1));
                        }
                    }
                    return sub;
                }
            }
        } catch (Exception ignored) {
            // fall through to the unreadable marker below
        }
        return new SnbtParser.Scalar("unknown", "<unreadable type>");
    }

    // ---------------------------------------------------------------- snbt

    private static void snbt(List<DataField> out, Entity entity, boolean hidden) {
        if (hidden) {
            out.add(new DataField("snbt", "snbt", "compound", "<redacted>",
                    DataStatus.REDACTED, DataField.PUBLIC_API));
            return;
        }
        String raw = null;
        try {
            raw = entity.getAsString();
        } catch (Exception ignored) {
            // Some entities (e.g. players on some implementations) refuse this.
        }
        if ((raw == null || raw.isBlank()) && !(entity instanceof Player)) {
            try {
                raw = entity.createSnapshot().getAsString();
            } catch (Exception ignored) {
                // Leave raw null: reported as UNAVAILABLE below.
            }
        }
        if (raw == null || raw.isBlank()) {
            out.add(new DataField("snbt", "snbt", "compound",
                    "<unavailable: this entity exposes no readable snapshot>",
                    DataStatus.UNAVAILABLE, DataField.PUBLIC_API));
            return;
        }
        SnbtParser.Result parsed = SnbtParser.parse(raw);
        if (!parsed.ok()) {
            out.add(new DataField("snbt", "snbt", "compound",
                    "<unavailable: " + parsed.error() + ">",
                    DataStatus.UNAVAILABLE, DataField.PUBLIC_API));
            return;
        }
        List<DataField> rows = DataBrowser.flatten(parsed.root(), "snbt", SNBT_LIMIT);
        if (rows.isEmpty()) {
            out.add(new DataField("snbt", "snbt", "compound", "(empty compound)",
                    DataStatus.READABLE, DataField.PUBLIC_API));
            return;
        }
        out.addAll(rows);
    }
}
