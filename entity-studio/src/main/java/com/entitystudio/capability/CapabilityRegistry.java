package com.entitystudio.capability;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.Merchant;

import java.util.EnumSet;
import java.util.Set;

/** Detects which capabilities an entity supports, dynamically per entity. */
public final class CapabilityRegistry {

    private CapabilityRegistry() {
    }

    public static Set<Capability> detect(Entity entity) {
        EnumSet<Capability> out = EnumSet.noneOf(Capability.class);
        if (entity instanceof LivingEntity living && living.getEquipment() != null) {
            out.add(Capability.EQUIPMENT);
        }
        if (entity instanceof Player) {
            out.add(Capability.PLAYER_INVENTORY);
        } else if (entity instanceof org.bukkit.entity.AbstractHorse) {
            out.add(Capability.MOUNT_INVENTORY);
        } else if (entity instanceof InventoryHolder) {
            out.add(Capability.CONTAINER_INVENTORY);
        }
        if (entity instanceof Merchant) {
            out.add(Capability.MERCHANT_OFFERS);
        }
        if (entity instanceof Tameable) {
            out.add(Capability.TAMEABLE_OWNER);
        }
        if (entity instanceof Mob) {
            out.add(Capability.MOB_TARGETING);
            out.add(Capability.PLUGIN_ALLEGIANCE);
        }
        if (entity instanceof org.bukkit.attribute.Attributable) {
            out.add(Capability.ATTRIBUTES);
        }
        if (entity instanceof Ageable) {
            out.add(Capability.AGEABLE_STATE);
        }
        if (entity instanceof LivingEntity) {
            out.add(Capability.EFFECTS);
        }
        out.add(Capability.ADVANCED_DATA);
        return out;
    }
}
