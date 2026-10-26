package com.entitystudio.capability;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Merchant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Capability discovery without a server. Proxies implement the Bukkit
 * interfaces, so instanceof narrowing matches the real type hierarchy\n * (verified against the 1.21.11 API jar). */
class CapabilityRegistryTest {

    static Entity fake(Class<?>... ifaces) {
        Class<?>[] all = new Class<?>[ifaces.length + 1];
        all[0] = Entity.class;
        System.arraycopy(ifaces, 0, all, 1, ifaces.length);
        return (Entity) Proxy.newProxyInstance(CapabilityRegistryTest.class.getClassLoader(),
                all, (proxy, method, args) -> null);
    }

    @Test
    void plainEntityGetsStashAndAdvancedOnly() {
        assertEquals(Set.of(Capability.VIRTUAL_STASH, Capability.ADVANCED_DATA),
                CapabilityRegistry.detect(fake()));
    }

    @Test
    void playerCapabilities() {
        Set<Capability> caps = CapabilityRegistry.detect(fake(Player.class));
        assertTrue(caps.contains(Capability.PLAYER_INVENTORY));
        assertTrue(caps.contains(Capability.ATTRIBUTES));
        assertTrue(caps.contains(Capability.EFFECTS));
        assertTrue(caps.contains(Capability.ADVANCED_DATA));
        assertFalse(caps.contains(Capability.MOB_TARGETING));
        assertFalse(caps.contains(Capability.MERCHANT_OFFERS));
        assertFalse(caps.contains(Capability.CONTAINER_INVENTORY));
        assertFalse(caps.contains(Capability.VIRTUAL_STASH));
    }

    @Test
    void horseGetsMountNotContainerOrStash() {
        Set<Capability> caps = CapabilityRegistry.detect(fake(AbstractHorse.class));
        assertTrue(caps.contains(Capability.MOUNT_INVENTORY));
        assertTrue(caps.contains(Capability.TAMEABLE_OWNER));
        assertFalse(caps.contains(Capability.CONTAINER_INVENTORY));
        assertFalse(caps.contains(Capability.VIRTUAL_STASH));
        assertFalse(caps.contains(Capability.PLAYER_INVENTORY));
    }

    @Test
    void merchantGetsOffers() {
        Set<Capability> caps = CapabilityRegistry.detect(fake(Merchant.class));
        assertTrue(caps.contains(Capability.MERCHANT_OFFERS));
    }

    @Test
    void mobGetsTargetingAndAllegiance() {
        Set<Capability> caps = CapabilityRegistry.detect(fake(Mob.class));
        assertTrue(caps.contains(Capability.MOB_TARGETING));
        assertTrue(caps.contains(Capability.PLUGIN_ALLEGIANCE));
        assertTrue(caps.contains(Capability.ATTRIBUTES));
        assertFalse(caps.contains(Capability.PLAYER_INVENTORY));
    }
}
