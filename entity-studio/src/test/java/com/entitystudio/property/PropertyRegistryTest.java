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
    void genericCombatAndPhysicsDiscovery() {
        Entity living = fake(LivingEntity.class);
        assertEquals(EntityProperty.Support.SUPPORTED, byId("ticks_lived").support(fake()));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("portal_cooldown").support(fake()));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("fall_distance").support(fake()));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("no_damage_ticks").support(living));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("max_no_damage_ticks").support(living));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("last_damage").support(living));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("can_pickup_items").support(living));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("collidable").support(living));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("gliding").support(living));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("swimming").support(living));
        assertEquals(EntityProperty.Support.UNSUPPORTED, byId("no_damage_ticks").support(fake()));
    }

    @Test
    void newNumericBoundsRejectOutOfRange() {
        assertFalse(byId("ticks_lived").parse("0").ok());
        assertTrue(byId("ticks_lived").parse("100").ok());
        assertFalse(byId("portal_cooldown").parse("-1").ok());
        assertFalse(byId("fall_distance").parse("-1").ok());
        assertFalse(byId("fall_distance").parse("NaN").ok());
        assertFalse(byId("no_damage_ticks").parse("-1").ok());
        assertFalse(byId("no_damage_ticks").parse("99999").ok());
        assertFalse(byId("last_damage").parse("-1").ok());
        assertFalse(byId("creeper.explosion_radius").parse("128").ok());
        assertTrue(byId("creeper.explosion_radius").parse("3").ok());
        assertFalse(byId("creeper.max_fuse").parse("0").ok());
        assertFalse(byId("phantom.size").parse("65").ok());
        assertTrue(byId("phantom.size").parse("0").ok());
        assertFalse(byId("tnt.fuse").parse("-1").ok());
        assertTrue(byId("tnt.fuse").parse("80").ok());
        assertFalse(byId("explosive.yield").parse("128").ok());
        assertTrue(byId("explosive.yield").parse("4").ok());
        assertFalse(byId("item.pickup_delay").parse("32768").ok());
        assertFalse(byId("bee.anger").parse("-1").ok());
        assertFalse(byId("boat.max_speed").parse("11").ok());
        assertTrue(byId("boat.max_speed").parse("0.4").ok());
        assertFalse(byId("falling.max_damage").parse("-1").ok());
    }

    @Test
    void plainEnumParsesWorkHeadless() {
        // Plain enums need no server: parse is covered here, read/write live.
        assertTrue(byId("fox.type").parse("red").ok());
        assertTrue(byId("fox.type").parse("SNOW").ok());
        assertFalse(byId("fox.type").parse("gray").ok());
        assertTrue(byId("parrot.variant").parse("blue").ok());
        assertFalse(byId("parrot.variant").parse("purple").ok());
        assertTrue(byId("axolotl.variant").parse("blue").ok());
        assertFalse(byId("axolotl.variant").parse("pink").ok());
        assertTrue(byId("mushroomcow.variant").parse("brown").ok());
        assertFalse(byId("mushroomcow.variant").parse("green").ok());
        assertTrue(byId("boat.type").parse("bamboo").ok());
        assertFalse(byId("boat.type").parse("steel").ok());
        assertTrue(byId("wolf.collar").parse("red").ok());
        assertTrue(byId("cat.collar").parse("blue").ok());
        assertFalse(byId("wolf.collar").parse("blurple").ok());
    }

    @Test
    void entityTypeMismatchIsUnsupported() {
        Entity fox = fake(org.bukkit.entity.Fox.class);
        Entity creeper = fake(org.bukkit.entity.Creeper.class);
        Entity armorStand = fake(org.bukkit.entity.ArmorStand.class);
        Entity item = fake(org.bukkit.entity.Item.class);
        Entity boat = fake(org.bukkit.entity.Boat.class);
        Entity tnt = fake(org.bukkit.entity.TNTPrimed.class);
        Entity falling = fake(org.bukkit.entity.FallingBlock.class);
        Entity projectile = fake(org.bukkit.entity.Projectile.class);
        Entity tameable = fake(org.bukkit.entity.Tameable.class);
        assertEquals(EntityProperty.Support.SUPPORTED, byId("fox.type").support(fox));
        assertEquals(EntityProperty.Support.UNSUPPORTED, byId("fox.type").support(creeper));
        assertEquals(EntityProperty.Support.SUPPORTED,
                byId("creeper.explosion_radius").support(creeper));
        assertEquals(EntityProperty.Support.UNSUPPORTED,
                byId("creeper.explosion_radius").support(fox));
        assertEquals(EntityProperty.Support.SUPPORTED,
                byId("armorstand.visible").support(armorStand));
        assertEquals(EntityProperty.Support.SUPPORTED,
                byId("armorstand.marker").support(armorStand));
        assertEquals(EntityProperty.Support.UNSUPPORTED,
                byId("armorstand.visible").support(fox));
        assertEquals(EntityProperty.Support.SUPPORTED,
                byId("item.pickup_delay").support(item));
        assertEquals(EntityProperty.Support.UNSUPPORTED,
                byId("item.pickup_delay").support(boat));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("boat.type").support(boat));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("tnt.fuse").support(tnt));
        assertEquals(EntityProperty.Support.SUPPORTED,
                byId("falling.drop_item").support(falling));
        assertEquals(EntityProperty.Support.SUPPORTED,
                byId("projectile.bounce").support(projectile));
        assertEquals(EntityProperty.Support.READ_ONLY, byId("tame.owner_uuid").support(tameable));
        assertEquals(EntityProperty.Support.UNSUPPORTED, byId("tame.owner_uuid").support(fox));
        // Creeper is not an Explosive in Bukkit: no double coverage.
        assertEquals(EntityProperty.Support.UNSUPPORTED, byId("explosive.yield").support(creeper));
        assertEquals(EntityProperty.Support.SUPPORTED, byId("explosive.yield").support(tnt));
        // Love mode is honestly irreversible.
        assertFalse(byId("breed").reversible());
        assertEquals(EntityProperty.RiskLevel.DESTRUCTIVE,
                byId("creeper.explosion_radius").risk());
        assertEquals(EntityProperty.RiskLevel.DESTRUCTIVE, byId("tnt.fuse").risk());
        assertEquals(EntityProperty.RiskLevel.DESTRUCTIVE, byId("explosive.yield").risk());
    }

    @Test
    void ownerUuidReadOnlyRefusesWrites() {
        @SuppressWarnings("unchecked")
        EntityProperty<String> owner = (EntityProperty<String>) byId("tame.owner_uuid");
        assertThrows(UnsupportedOperationException.class, () -> owner.write(fake(), "x"));
        String err = PropertyRegistry.apply(null, fake(org.bukkit.entity.Tameable.class), owner,
                "x", "entitystudio.data.edit", node -> true, null, false);
        assertNotNull(err);
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
