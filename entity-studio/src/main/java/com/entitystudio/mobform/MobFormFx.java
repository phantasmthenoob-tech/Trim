package com.entitystudio.mobform;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Ambient particles so morphed forms feel alive between abilities. */
final class MobFormFx {

    private MobFormFx() {
    }

    static void tick(Player player, EntityType type) {
        Location at = player.getLocation().clone().add(0, 1, 0);
        switch (type) {
            case BLAZE -> player.getWorld().spawnParticle(Particle.FLAME, at, 4,
                    0.4, 0.5, 0.4, 0.02);
            case GHAST, WITHER -> player.getWorld().spawnParticle(
                    Particle.LARGE_SMOKE, at, 3, 0.5, 0.5, 0.5, 0.01);
            case ENDERMAN -> player.getWorld().spawnParticle(Particle.PORTAL, at, 6,
                    0.4, 0.8, 0.4, 0.3);
            case SLIME -> player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 3,
                    0.4, 0.3, 0.4, 0.02);
            case WITCH -> player.getWorld().spawnParticle(Particle.WITCH, at, 2,
                    0.4, 0.5, 0.4, 0.02);
            case ENDER_DRAGON -> player.getWorld().spawnParticle(
                    Particle.DRAGON_BREATH, at, 4, 0.6, 0.6, 0.6, 0.02);
            case CREEPER -> player.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                    player.getLocation().clone().add(0, 0.5, 0), 1,
                    0.2, 0.2, 0.2, 0.01);
            default -> {
            }
        }
    }
}
