package com.entitystudio.behavior;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** AGGRESSIVE is plugin-driven hunting, not vanilla: flags, assist rules,
 * and scan-range fallback are all pinned headless. */
class AggressionTest {

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
        return 0;
    }

    /** Mob proxy with configurable attack-damage / follow-range attributes
     * (null = attribute absent, like a peaceful animal). */
    static Mob fakeMob(Double attackDamage, Double followRange) {
        return (Mob) Proxy.newProxyInstance(AggressionTest.class.getClassLoader(),
                new Class<?>[]{Mob.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getAttribute") && args.length == 1) {
                        Attribute attr = (Attribute) args[0];
                        Double v = attr.name().equals("ATTACK_DAMAGE") ? attackDamage
                                : attr.name().equals("FOLLOW_RANGE") ? followRange : null;
                        if (v == null) {
                            return null;
                        }
                        return Proxy.newProxyInstance(
                                AggressionTest.class.getClassLoader(),
                                new Class<?>[]{AttributeInstance.class},
                                (p2, m2, a2) -> m2.getName().equals("getValue") ? v
                                        : defaultValue(m2.getReturnType()));
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    @Test
    void aggressiveProfileArmsHunting() {
        ManagedRecord r = new ManagedRecord(UUID.randomUUID());
        AllegianceService.applyProfile(fakeMob(null, null),
                ManagedRecord.Profile.AGGRESSIVE, r);
        assertTrue(r.aggressive());
        assertTrue(r.retaliate());
        assertTrue(r.aiEnabled());
    }

    @Test
    void otherProfilesDisarmHunting() {
        for (var p : new ManagedRecord.Profile[]{
                ManagedRecord.Profile.PASSIVE, ManagedRecord.Profile.NEUTRAL,
                ManagedRecord.Profile.GUARD, ManagedRecord.Profile.STATIONARY,
                ManagedRecord.Profile.CUSTOM}) {
            ManagedRecord r = new ManagedRecord(UUID.randomUUID());
            r.aggressive(true);
            AllegianceService.applyProfile(fakeMob(null, null), p, r);
            assertFalse(r.aggressive(), p.name());
        }
    }

    @Test
    void assistOnlyForTheHarmless() {
        // Null = attribute absent (peaceful animal); non-positive = no real attack.
        assertTrue(AllegianceService.needsAssistValue(null));
        assertTrue(AllegianceService.needsAssistValue(0.0));
        assertFalse(AllegianceService.needsAssistValue(5.0));
        // Mob resolution degrades to assist when attributes are unavailable.
        assertTrue(AllegianceService.needsAssist(fakeMob(null, null)));
    }

    @Test
    void scanRangeUsesFollowRangeWithBounds() {
        assertEquals(16.0, AllegianceService.scanRangeValue(null));
        assertEquals(32.0, AllegianceService.scanRangeValue(32.0));
        assertEquals(64.0, AllegianceService.scanRangeValue(500.0));
        assertEquals(4.0, AllegianceService.scanRangeValue(0.5));
        // Mob resolution falls back to 16 when attributes are unavailable.
        assertEquals(16.0, AllegianceService.scanRange(fakeMob(null, null)));
    }
}
