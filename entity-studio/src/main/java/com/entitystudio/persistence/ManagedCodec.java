package com.entitystudio.persistence;

import com.entitystudio.behavior.ManagedRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure encode/decode for plugin-managed allegiance records. No Bukkit, no
 * files — fully headless-testable. Corrupt fields are quarantined
 * individually (named in the result) while valid fields still load, so one
 * bad entry can never silently wipe or poison a record.
 *
 * <p>Defaults mirror the original loader exactly: defend-owner true,
 * friendly-fire true, ai-enabled true, everything else off/empty, unknown
 * profile names fall back to CUSTOM.
 */
public final class ManagedCodec {

    public record DecodeResult(ManagedRecord record, List<String> problems) {
        public boolean clean() {
            return problems.isEmpty();
        }
    }

    private ManagedCodec() {
    }

    /** Plain-map form of a record (nested maps, string lists, primitives). */
    public static Map<String, Object> encode(ManagedRecord r) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", r.entityType());
        Map<String, Object> allegiance = new LinkedHashMap<>();
        allegiance.put("owner", r.owner() == null ? null : r.owner().toString());
        allegiance.put("trusted", sortedStrings(r.trusted()));
        allegiance.put("enemies", sortedStrings(r.enemies()));
        allegiance.put("allowed-types", sortedStrings(r.allowedTypes()));
        allegiance.put("excluded-types", sortedStrings(r.excludedTypes()));
        allegiance.put("defend-owner", r.defendOwner());
        allegiance.put("defend-allies", r.defendAllies());
        allegiance.put("retaliate", r.retaliate());
        allegiance.put("attack-owner-target", r.attackOwnerTarget());
        allegiance.put("pursue-outside-range", r.pursueOutsideRange());
        allegiance.put("clear-invalid-target", r.clearInvalidTarget());
        allegiance.put("friendly-fire", r.friendlyFireProtect());
        out.put("allegiance", allegiance);
        Map<String, Object> behavior = new LinkedHashMap<>();
        behavior.put("profile", r.profile().name());
        behavior.put("ai-enabled", r.aiEnabled());
        behavior.put("anchored", r.anchored());
        behavior.put("hard-anchor", r.hardAnchor());
        behavior.put("world", r.anchorWorld());
        behavior.put("x", r.anchorX());
        behavior.put("y", r.anchorY());
        behavior.put("z", r.anchorZ());
        behavior.put("yaw", r.anchorYaw());
        behavior.put("pitch", r.anchorPitch());
        out.put("behavior", behavior);
        return out;
    }

    private static List<String> sortedStrings(Set<?> ids) {
        List<String> out = new ArrayList<>();
        for (Object o : ids) {
            out.add(String.valueOf(o));
        }
        out.sort(String::compareTo);
        return out;
    }

    /** Tolerant decode: bad fields are named and skipped, good fields load. */
    @SuppressWarnings("unchecked")
    public static DecodeResult decode(UUID id, Map<String, Object> data) {
        List<String> problems = new ArrayList<>();
        ManagedRecord r = new ManagedRecord(id);
        if (data == null) {
            problems.add("empty entry");
            return new DecodeResult(r, problems);
        }
        r.entityType(string(data, "type", "UNKNOWN"));
        Map<String, Object> allegiance = section(data, "allegiance", problems);
        Map<String, Object> behavior = section(data, "behavior", problems);

        String owner = stringOrNull(allegiance, "owner");
        if (owner != null && !owner.isBlank()) {
            UUID oid = uuid(owner);
            if (oid == null) {
                problems.add("allegiance.owner not a UUID: " + owner);
            } else {
                r.owner(oid);
            }
        }
        r.trusted().addAll(uuidList(allegiance, "trusted", problems));
        r.enemies().addAll(uuidList(allegiance, "enemies", problems));
        r.allowedTypes().addAll(typeSet(allegiance, "allowed-types", problems));
        r.excludedTypes().addAll(typeSet(allegiance, "excluded-types", problems));
        r.defendOwner(bool(allegiance, "defend-owner", true, problems));
        r.defendAllies(bool(allegiance, "defend-allies", false, problems));
        r.retaliate(bool(allegiance, "retaliate", false, problems));
        r.attackOwnerTarget(bool(allegiance, "attack-owner-target", false, problems));
        r.pursueOutsideRange(bool(allegiance, "pursue-outside-range", true, problems));
        r.clearInvalidTarget(bool(allegiance, "clear-invalid-target", true, problems));
        r.friendlyFireProtect(bool(allegiance, "friendly-fire", true, problems));

        String profile = string(behavior, "profile", "CUSTOM");
        try {
            r.profile(ManagedRecord.Profile.valueOf(profile.toUpperCase()));
        } catch (IllegalArgumentException e) {
            problems.add("behavior.profile unknown: " + profile);
            r.profile(ManagedRecord.Profile.CUSTOM);
        }
        r.aiEnabled(bool(behavior, "ai-enabled", true, problems));
        if (bool(behavior, "anchored", false, problems)) {
            String world = stringOrNull(behavior, "world");
            if (world == null || world.isBlank()) {
                problems.add("behavior.anchored without a world — anchor dropped");
            } else {
                r.setAnchor(true, bool(behavior, "hard-anchor", false, problems), world,
                        num(behavior, "x", 0, problems), num(behavior, "y", 0, problems),
                        num(behavior, "z", 0, problems),
                        (float) num(behavior, "yaw", 0, problems),
                        (float) num(behavior, "pitch", 0, problems));
            }
        }
        return new DecodeResult(r, problems);
    }

    // ------------------------------------------------------------- readers

    private static Map<String, Object> section(Map<String, Object> data, String key,
            List<String> problems) {
        Object o = data.get(key);
        if (o == null) {
            return Map.of();
        }
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        problems.add(key + " section is not a map — skipped");
        return Map.of();
    }

    private static String string(Map<String, Object> map, String key, String def) {
        Object o = map.get(key);
        return o == null ? def : String.valueOf(o);
    }

    private static String stringOrNull(Map<String, Object> map, String key) {
        Object o = map.get(key);
        return o == null ? null : String.valueOf(o);
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Set<UUID> uuidList(Map<String, Object> map, String key,
            List<String> problems) {
        Set<UUID> out = new LinkedHashSet<>();
        Object o = map.get(key);
        if (o == null) {
            return out;
        }
        if (!(o instanceof List<?> list)) {
            problems.add(key + " is not a list — skipped");
            return out;
        }
        for (Object e : list) {
            UUID id = uuid(String.valueOf(e));
            if (id == null) {
                problems.add(key + " has bad UUID: " + e);
            } else {
                out.add(id);
            }
        }
        return out;
    }

    /** Uppercase entity-type names; unknown tokens are quarantined, never stored. */
    private static Set<String> typeSet(Map<String, Object> map, String key,
            List<String> problems) {
        Set<String> out = new LinkedHashSet<>();
        Object o = map.get(key);
        if (o == null) {
            return out;
        }
        if (!(o instanceof List<?> list)) {
            problems.add(key + " is not a list — skipped");
            return out;
        }
        for (Object e : list) {
            String t = String.valueOf(e).trim().toUpperCase(java.util.Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(org.bukkit.entity.EntityType.valueOf(t).name());
            } catch (IllegalArgumentException ex) {
                problems.add(key + " has unknown type: " + e);
            }
        }
        return out;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def,
            List<String> problems) {
        Object o = map.get(key);
        if (o == null) {
            return def;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s
                && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false"))) {
            return Boolean.parseBoolean(s);
        }
        problems.add(key + " is not a boolean — default " + def);
        return def;
    }

    private static double num(Map<String, Object> map, String key, double def,
            List<String> problems) {
        Object o = map.get(key);
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            problems.add(key + " is not a number — default " + def);
            return def;
        }
    }
}
