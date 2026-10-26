package com.entitystudio.property;

import com.entitystudio.history.EditRecord;
import com.entitystudio.history.HistoryService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Central registry of typed properties + the guarded write path. */
public class PropertyRegistry {

    private final Map<String, EntityProperty<?>> properties = new LinkedHashMap<>();

    public PropertyRegistry() {
        CommonProperties.register(this);
        SpecificProperties.register(this);
    }

    public void register(EntityProperty<?> property) {
        properties.put(property.id(), property);
    }

    public EntityProperty<?> byId(String id) {
        return properties.get(id);
    }

    /** All properties with a real (non-UNSUPPORTED) status for this entity. */
    public List<EntityProperty<?>> listFor(Entity entity) {
        List<EntityProperty<?>> out = new ArrayList<>();
        for (EntityProperty<?> p : properties.values()) {
            if (p.support(entity) != EntityProperty.Support.UNSUPPORTED) {
                out.add(p);
            }
        }
        return out;
    }

    public List<EntityProperty<?>> searchFor(Entity entity, String query) {
        String q = query.toLowerCase();
        List<EntityProperty<?>> out = new ArrayList<>();
        for (EntityProperty<?> p : listFor(entity)) {
            if (p.id().contains(q) || p.displayName().toLowerCase().contains(q)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * Guarded write: permission → validation → snapshot → write → history.
     * Must run on the server thread. Returns an error message or null on success.
     */
    @SuppressWarnings("unchecked")
    public static <T> String apply(Player actor, Entity entity, EntityProperty<T> prop,
            T value, String permissionNode, HistoryService history, boolean recordHistory) {
        if (!actor.hasPermission(permissionNode)) {
            return "Missing permission " + permissionNode + ".";
        }
        if (prop.support(entity) == EntityProperty.Support.UNSUPPORTED) {
            return "Property '" + prop.id() + "' is not supported by "
                    + entity.getType().name().toLowerCase() + ".";
        }
        ValidationResult vr = prop.validate(entity, value);
        if (!vr.ok()) {
            return vr.message();
        }
        String oldValue;
        try {
            oldValue = String.valueOf(prop.read(entity));
        } catch (Exception e) {
            oldValue = "?";
        }
        try {
            prop.write(entity, value);
        } catch (Exception e) {
            return "Write failed: " + e.getMessage();
        }
        if (recordHistory && history != null) {
            String newValue = String.valueOf(value);
            String old = oldValue;
            history.record(new EditRecord(actor.getUniqueId(), entity.getUniqueId(),
                    entity.getType().name(), prop.displayName() + " (" + prop.id() + ")",
                    old, newValue, prop.reversible(), prop.risk().name(),
                    prop.reversible() ? () -> unsafeRewrite(prop, entity, value, old) : null));
        }
        return null;
    }

    /** Re-reads the entity fresh at undo time; reports failure instead of guessing. */
    private static <T> void unsafeRewrite(EntityProperty<T> prop, Entity entity,
            T applied, String old) throws Exception {
        // Best-effort: only boolean/number/text/enum properties recorded here are
        // restored by re-parsing the old string. If parsing fails, undo refuses.
        EntityProperty.ParseResult<T> parsed = prop.parse(old);
        if (!parsed.ok()) {
            throw new IllegalStateException("Previous value '" + old + "' is no longer valid.");
        }
        ValidationResult vr = prop.validate(entity, parsed.value());
        if (!vr.ok()) {
            throw new IllegalStateException(vr.message());
        }
        prop.write(entity, parsed.value());
    }
}
