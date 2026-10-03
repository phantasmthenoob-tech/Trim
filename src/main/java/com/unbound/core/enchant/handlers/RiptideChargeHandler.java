package com.unbound.core.enchant.handlers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.EnchantmentDefinition;
import com.unbound.core.math.EffectMath;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Riptide self-launch, Unbound-style — a real charged mechanic:
 *
 * <ol>
 *   <li><b>Right-click repeatedly</b> on a melee item (or any plain item)
 *       carrying Riptide: each click adds a charge step (~5 ticks) with
 *       escalating riptide sound and a particle swirl. Plain items only
 *       send one use packet per click (unlike a drawn bow), so charge
 *       builds per click — about 3 clicks reach full charge by default.
 *       Holding right-click on items that DO repeat use packets (bows,
 *       shields, food) also charges continuously.</li>
 *   <li><b>Aim freely while charging</b> — the launch direction is captured
 *       at release, so the player fully controls where they go.</li>
 *   <li><b>Release</b> (stop clicking / stop sending use packets): a 2-tick
 *       maintenance task detects the input gap and launches along the
 *       current look direction, with strength scaled by charge progress.
 *       Quick single clicks under the minimum charge do nothing (no
 *       accidental launches).</li>
 * </ol>
 *
 * <p>Tridents are excluded (vanilla riptide owns them, water requirement and
 * all); off-trident riptide works without water.</p>
 */
public final class RiptideChargeHandler implements Listener {

    /** Charge ticks credited per right-click (clients repeat packets only
     *  while an item is "in use", so plain items charge one step per click). */
    private static final int INPUT_PACKET_INTERVAL_TICKS = 5;

    private final UnboundServices services;
    private final Map<UUID, Charge> charging = new HashMap<>();
    private final int taskId;

    private record Charge(int level, int chargeTicks, long lastInputTick, Material itemMaterial) {
    }

    public RiptideChargeHandler(UnboundServices services) {
        this.services = services;
        // Maintenance: finalize charges whose input stopped (release / switch).
        this.taskId = Bukkit.getScheduler().runTaskTimer(services.plugin(),
                this::finalizeExpiredCharges, 2L, 2L).getTaskId();
    }

    /** Stops the maintenance task (plugin disable/reload safety). */
    public void stop() {
        Bukkit.getScheduler().cancelTask(taskId);
        charging.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCharge(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return; // one charge step per click, not two (main + off hand packet)
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (item.getType() == Material.TRIDENT) {
            return; // vanilla riptide owns tridents
        }
        int level = riptideLevel(player, item);
        if (level <= 0) {
            return;
        }
        long nowTick = player.getWorld().getFullTime();
        Charge existing = charging.get(player.getUniqueId());
        int chargeTicks = existing == null
                ? INPUT_PACKET_INTERVAL_TICKS
                : existing.chargeTicks() + INPUT_PACKET_INTERVAL_TICKS;
        charging.put(player.getUniqueId(),
                new Charge(level, chargeTicks, nowTick, item.getType()));
        playChargeStep(player, level, chargeProgress(chargeTicks, level));
    }

    private void finalizeExpiredCharges() {
        long gapTicks = Math.max(INPUT_PACKET_INTERVAL_TICKS + 1,
                services.config().riptide().inputGapTicks());
        Iterator<Map.Entry<UUID, Charge>> iterator = charging.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Charge> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Charge charge = entry.getValue();
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            long nowTick = player.getWorld().getFullTime();
            boolean released = nowTick - charge.lastInputTick() >= gapTicks;
            boolean switchedItem = player.getInventory().getItemInMainHand().getType() != charge.itemMaterial();
            if (!released && !switchedItem) {
                continue;
            }
            iterator.remove();
            if (switchedItem) {
                continue; // swapping items cancels the charge silently
            }
            if (charge.chargeTicks() < 1) {
                continue;
            }
            launch(player, charge.level(), charge.chargeTicks());
        }
    }

    private void launch(Player player, int level, int chargeTicks) {
        double progress = chargeProgress(chargeTicks, level);
        int minCharge = Math.max(1, services.config().riptide().minChargeTicks());
        if (chargeTicks < minCharge) {
            return; // too quick: no accidental launches
        }
        double strength = EffectMath.riptideLaunchStrength(level) * (0.6 + 0.4 * progress);
        if (strength <= 0) {
            return;
        }
        // Direction is captured at release: aiming while charging edits it.
        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.clone().multiply(strength);
        velocity.setY(Math.max(velocity.getY(), strength * 0.35));
        player.setVelocity(velocity);
        player.setFallDistance(0.0f);

        Location location = player.getLocation();
        var world = location.getWorld();
        if (world != null) {
            world.playSound(location, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f,
                    (float) (0.9f + 0.1f * progress));
            world.spawnParticle(Particle.CLOUD, location, 24, 0.3, 0.3, 0.3, 0.12);
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP, location, 32, 0.4, 0.6, 0.4, 0.15);
        }
    }

    private void playChargeStep(Player player, int level, double progress) {
        Location location = player.getLocation();
        var world = location.getWorld();
        if (world == null) {
            return;
        }
        Sound sound = level >= 3 ? Sound.ITEM_TRIDENT_RIPTIDE_3
                : level == 2 ? Sound.ITEM_TRIDENT_RIPTIDE_2
                : Sound.ITEM_TRIDENT_RIPTIDE_1;
        world.playSound(location, sound, 0.6f, (float) (0.6f + 0.6f * progress));
        // Escalating swirl: particles tighten and speed up as charge grows.
        double radius = 1.2 - 0.6 * progress;
        double angle = chargeStepSeed(player, progress);
        world.spawnParticle(Particle.CLOUD,
                location.clone().add(Math.cos(angle) * radius, 0.8, Math.sin(angle) * radius),
                2, 0.02, 0.05, 0.02, 0.0);
        if (progress >= 1.0) {
            world.spawnParticle(Particle.BUBBLE_COLUMN_UP,
                    location.clone().add(0, 1.0, 0), 6, 0.25, 0.2, 0.25, 0.05);
        }
    }

    private static double chargeStepSeed(Player player, double progress) {
        return (player.getEntityId() * 31L + System.nanoTime()) % 628L / 100.0
                + progress * Math.PI;
    }

    private double chargeProgress(int chargeTicks, int level) {
        int full = Math.max(1, services.config().riptide().chargeTicks());
        return Math.min(1.0, chargeTicks / (double) full);
    }

    private int riptideLevel(Player player, ItemStack item) {
        EnchantmentDefinition riptide = services.engine().get("riptide").orElse(null);
        if (riptide == null || !riptide.enabled()) {
            return 0;
        }
        return services.engine().levelOf(item, riptide);
    }

    /** Number of players currently charging (tests/debug). */
    public int chargingCount() {
        return charging.size();
    }
}
