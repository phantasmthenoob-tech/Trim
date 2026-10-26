package com.entitystudio.property;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/** Boss properties list for the right entities (incl. dragons) and validate. */
class BossPropertiesTest {

    static Entity fake(Class<?>... ifaces) {
        Class<?>[] all = new Class<?>[ifaces.length + 1];
        all[0] = Entity.class;
        System.arraycopy(ifaces, 0, all, 1, ifaces.length);
        return (Entity) Proxy.newProxyInstance(BossPropertiesTest.class.getClassLoader(), all,
                (proxy, method, args) -> {
                    Class<?> t = method.getReturnType();
                    if (!t.isPrimitive()) {
                        return null;
                    }
                    if (t == boolean.class) {
                        return false;
                    }
                    if (t == long.class) {
                        return 0L;
                    }
                    if (t == double.class) {
                        return 0.0d;
                    }
                    if (t == float.class) {
                        return 0.0f;
                    }
                    return 0;
                });
    }

    private final PropertyRegistry registry = new PropertyRegistry();

    @Test
    void dragonPhaseForDragonsOnly() {
        Entity dragon = fake(EnderDragon.class);
        Entity zombie = fake(Zombie.class);
        assertTrue(registry.byId("dragon.phase").support(dragon).writable());
        assertFalse(registry.byId("dragon.phase").support(zombie).writable());
        assertTrue(registry.listFor(dragon).stream().anyMatch(p -> p.id().equals("dragon.phase")));
        assertTrue(registry.listFor(zombie).stream().noneMatch(p -> p.id().equals("dragon.phase")));
        // Generic living-entity coverage still applies to dragons (never an empty page).
        assertTrue(registry.listFor(dragon).stream().anyMatch(p -> p.id().equals("health")));
        assertTrue(registry.listFor(dragon).stream().anyMatch(p -> p.id().equals("ai")));
    }

    @Test
    void dragonPhaseValidatesAgainstLiveEnum() {
        EntityProperty<?> phase = registry.byId("dragon.phase");
        assertTrue(phase.parse("circling").ok());
        assertFalse(phase.parse("bogus-phase").ok());
        assertTrue(phase.parse("HOVER").ok());
    }

    @Test
    void witherTicksForWithersOnly() {
        Entity wither = fake(Wither.class);
        Entity zombie = fake(Zombie.class);
        assertTrue(registry.byId("wither.invulnerable_ticks").support(wither).writable());
        assertFalse(registry.byId("wither.invulnerable_ticks").support(zombie).writable());
    }
}
