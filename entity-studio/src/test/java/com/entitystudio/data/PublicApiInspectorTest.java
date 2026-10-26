package com.entitystudio.data;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The inspector must never throw for failing getters and must never invent
 * values: failures become UNAVAILABLE rows, player data becomes REDACTED.
 */
class PublicApiInspectorTest {

    /** Proxy with all getters returning null/defaults (read failures). */
    static Entity failingEntity() {
        return (Entity) Proxy.newProxyInstance(PublicApiInspectorTest.class.getClassLoader(),
                new Class<?>[]{Entity.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    static Player failingPlayer() {
        return (Player) Proxy.newProxyInstance(PublicApiInspectorTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (type == Set.class) {
                return null; // simulate unreadable collections
            }
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return ' ';
        }
        return 0;
    }

    @Test
    void failingGettersBecomeUnavailableRowsNeverThrows() {
        List<DataField> rows = PublicApiInspector.INSTANCE.inspect(failingEntity(), true);
        assertFalse(rows.isEmpty());
        for (DataField f : rows) {
            assertNotNull(f.path());
            assertNotNull(f.type());
            assertNotNull(f.value());
            assertNotNull(f.status());
            assertEquals(DataField.PUBLIC_API, f.source());
        }
        assertTrue(rows.stream().anyMatch(f -> f.section().equals("identity")));
        // Null UUID / type / world must not be shown as readable facts.
        assertTrue(rows.stream()
                .filter(f -> f.path().equals("uuid") || f.path().equals("type"))
                .allMatch(f -> f.status() == DataStatus.UNAVAILABLE));
        assertTrue(rows.stream().anyMatch(f -> f.section().equals("pdc")
                && f.status() == DataStatus.UNAVAILABLE));
        assertTrue(rows.stream().anyMatch(f -> f.section().equals("snbt")
                && f.status() == DataStatus.UNAVAILABLE));
    }

    @Test
    void otherPlayersDataIsRedactedWithoutPermission() {
        List<DataField> rows = PublicApiInspector.INSTANCE.inspect(failingPlayer(), false);
        assertTrue(rows.stream().anyMatch(f -> f.section().equals("identity")));
        assertTrue(rows.stream()
                .filter(f -> f.section().equals("snbt") || f.section().equals("pdc")
                        || f.section().equals("tags"))
                .allMatch(f -> f.status() == DataStatus.REDACTED));
        // Redacted rows carry no secret payload.
        assertTrue(rows.stream()
                .filter(f -> f.status() == DataStatus.REDACTED)
                .allMatch(f -> f.value().equals("<redacted>")));
    }

    @Test
    void playerDataVisibleWithPermissionStillFailsClosed() {
        List<DataField> rows = PublicApiInspector.INSTANCE.inspect(failingPlayer(), true);
        assertTrue(rows.stream().noneMatch(f -> f.status() == DataStatus.REDACTED));
        assertTrue(rows.stream().anyMatch(f -> f.status() == DataStatus.UNAVAILABLE));
    }

    @Test
    void pdcReaderHandlesTypesAndUnknowns() {
        PersistentDataContainer strings = (PersistentDataContainer) Proxy.newProxyInstance(
                PublicApiInspectorTest.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("has")) {
                        return args[1] == PersistentDataType.STRING;
                    }
                    if (method.getName().equals("get")) {
                        return "hello";
                    }
                    return null;
                });
        var key = new org.bukkit.NamespacedKey("test", "k");
        assertEquals("hello", PublicApiInspector.readPdcValue(strings, key, 0));

        PersistentDataContainer empty = (PersistentDataContainer) Proxy.newProxyInstance(
                PublicApiInspectorTest.class.getClassLoader(),
                new Class<?>[]{PersistentDataContainer.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        Object unknown = PublicApiInspector.readPdcValue(empty, key, 0);
        assertInstanceOf(SnbtParser.Scalar.class, unknown);
        assertEquals("unknown", ((SnbtParser.Scalar) unknown).type());
    }
}
