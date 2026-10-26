package com.entitystudio.property;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Capability discovery + metadata + invalid-value tests without a server.
 * Entities are dynamic proxies: instanceof checks behave like the real
 * type hierarchy, Bukkit getters return safe defaults.
 */
class PropertyRegistryTest {

    /** Proxy implementing Entity plus the given Bukkit interfaces. */
    static Entity fake(Class<?>... ifaces) {
        Class<?>[] all = new Class<?>[ifaces.length + 1];
        all[0] = Entity.class;
        System.arraycopy(ifaces, 0, all, 1, ifaces.length);
        return (Entity) Proxy.newProxyInstance(PropertyRegistryTest.class.getClassLoader(), all,
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
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

    private final PropertyRegistry registry = new PropertyRegistry();

    private EntityProperty<?> byId(String id) {
        EntityProperty<?> p = registry.byId(id);
        assertNotNull(p, "missing property " + id);
        return p;
    }

    @Test
    void metadataCompleteAndUnique() {
        Set<String> ids = new HashSet<>();
        int count = 0;
        for (EntityProperty<?> p : registry.all()) {
            count++;
            assertTrue(ids.add(p.id()), "duplicate id " + p.id());
            assertFalse(p.id().isBlank());
            assertFalse(p.displayName().isBlank(), p.id());
            assertFalse(p.description().isBlank(), p.id());
            assertFalse(p.scope().isBlank(), p.id());
            assertNotNull(p.type(), p.id());
            assertNotNull(p.risk(), p.id());
            assertFalse(p.failureBehavior().isBlank(), p.id());
        }
        assertTrue(count >= 30, "expected full registry, got " + count);
    }

    @Test
    void plainEntityOnlySeesUniversalAndReadOnly() {
        Entity plain = fake();
        assertEquals(EntityProperty.Support.SUPPORTED, byId("fire_ticks").support(plain));
        assertEquals(EntityProperty.Support.READ_ONLY, byId("entity.uuid").support(plain));
        assertEquals(EntityProperty.Support.READ_ONLY, byId("entity.type").support(plain));
        assertEquals(EntityProperty.Support.UNSUPPORTED, byId("health").support(plain));
        assertEquals(EntityProperty.Support.UNSUPPORTED, byId("age").support(plain));
        assertEquals(EntityProperty.Support.UNSUPPORTED,
                byId("villager.profession").support(plain));
        assertEquals(EntityProperty.Support.UNSUPPORTED, byId("horse.chest").support(plain));
    }

    @Test
    void livingEntityMismatchIsUnsupportedNotEditable() {
        Entity living = fake(LivingEntity.class);
        assertEquals(EntityProperty.Support.SUPPORTED, byId("health").support(living));
        assertEquals(EntityProperty.Support.UNAVAILABLE, byId("max_health").support(living));
        assertEquals(EntityProperty.Support.UNSUPPORTED, byId("age").support(living));
        assertEquals(EntityProperty.Support.UNSUPPORTED,
                byId("creeper.fuse").support(living));
    }

    @Test
    void ageableAndSpecialStates() {
        // Ageable -> Creature -> Mob -> LivingEntity (verified against the API
        // jar): ageable mobs are living, so vitals apply to them too.
        Entity ageable = fake(Ageable.class);
        assertEquals(EntityProperty.Support.SUPPORTED, byId("age").support(ageable));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("health").support(ageable));
        assertEquals(EntityProperty.Support.UNSUPPORTED,
                byId("creeper.fuse").support(ageable));
        Entity chested = fake(ChestedHorse.class);
        assertEquals(EntityProperty.Support.SPECIAL, byId("horse.chest").support(chested));
    }

    @Test
    void invalidValuesRejectedByParse() {
        assertFalse(byId("health").parse("abc").ok());
        assertFalse(byId("health").parse("9999").ok());
        assertFalse(byId("age").parse("99999").ok());
        // Villager.Profession enum init needs a server, so profession parsing
        // is verified live; the pure range check below covers the same path.
        assertFalse(byId("villager.level").parse("99").ok());
        assertTrue(byId("villager.level").parse("3").ok());
        assertFalse(byId("color").parse("blurple").ok());
        assertFalse(byId("slime.size").parse("0").ok());
        assertTrue(byId("health").parse("20").ok());
    }

    @Test
    void readOnlyPropertiesRefuseWrites() {
        @SuppressWarnings("unchecked")
        EntityProperty<String> uuid = (EntityProperty<String>) byId("entity.uuid");
        assertThrows(UnsupportedOperationException.class, () -> uuid.write(fake(), "x"));
        String err = PropertyRegistry.apply(null, fake(), uuid, "x", "entitystudio.data.edit",
                node -> true, null, false);
        assertNotNull(err);
        assertTrue(err.contains("read_only"));
    }

    @Test
    void writableMatrix() {
        assertTrue(EntityProperty.Support.SUPPORTED.writable());
        assertTrue(EntityProperty.Support.PARTIAL.writable());
        assertFalse(EntityProperty.Support.UNAVAILABLE.writable());
        assertFalse(EntityProperty.Support.READ_ONLY.writable());
        assertFalse(EntityProperty.Support.SPECIAL.writable());
        assertFalse(EntityProperty.Support.UNSUPPORTED.writable());
    }
}
