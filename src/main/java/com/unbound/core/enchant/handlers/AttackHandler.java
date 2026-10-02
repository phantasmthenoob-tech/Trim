package com.unbound.core.enchant.handlers;

import com.unbound.core.UnboundServices;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.ProcessingGuard;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Translates combat events into dispatches:
 *
 * <ul>
 *   <li>{@code NORMAL} phase (damage modification): MELEE_ATTACK
 *       (Sharpness) and PROJECTILE_HIT (Power).</li>
 *   <li>{@code MONITOR} phase (actions, only when the attack was not
 *       cancelled): MELEE_IMPACT (Multishot, Punch) and PROJECTILE_IMPACT
 *       (Punch).</li>
 *   <li>Death: ENTITY_DEATH (Looting).</li>
 * </ul>
 *
 * <p>Events arriving while a {@link ProcessingGuard} is open (our own
 * nested {@code damage()} calls) are skipped entirely — this is the
 * recursion and duplicate-processing guard for every combat effect.</p>
 */
public final class AttackHandler implements Listener {

    private final UnboundServices services;

    public AttackHandler(UnboundServices services) {
        this.services = services;
    }

    // ---- damage modification phase --------------------------------------

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        dispatchCombat(event, false);
    }

    // ---- impact phase ----------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onImpact(EntityDamageByEntityEvent event) {
        dispatchCombat(event, true);
    }

    // ---- death ------------------------------------------------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) {
            return;
        }
        dispatch(ActionType.ENTITY_DEATH, killer, weapon, null, event, event.getEntity(), event.getEntity().getLocation(), 0.0);
    }

    // ---- plumbing -----------------------------------------------------------

    private void dispatchCombat(EntityDamageByEntityEvent event, boolean impactPhase) {
        if (ProcessingGuard.isActive()) {
            return;
        }
        Resolved resolved = resolve(event);
        if (resolved == null) {
            return;
        }
        ActionType action;
        if (impactPhase) {
            action = resolved.projectile != null ? ActionType.PROJECTILE_IMPACT : ActionType.MELEE_IMPACT;
        } else {
            action = resolved.projectile != null ? ActionType.PROJECTILE_HIT : ActionType.MELEE_ATTACK;
        }
        dispatch(action, resolved.player, resolved.item, resolved.projectile, event,
                event.getEntity(), resolved.player.getLocation(), event.getDamage());
    }

    private record Resolved(Player player, ItemStack item, @Nullable Projectile projectile) {
    }

    private @Nullable Resolved resolve(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (damager instanceof Player player
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            ItemStack item = player.getInventory().getItemInMainHand();
            return item.getType().isAir() ? null : new Resolved(player, item, null);
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            return item.getType().isAir() ? null : new Resolved(player, item, projectile);
        }
        return null;
    }

    private void dispatch(ActionType action, Player player, ItemStack item,
                          @Nullable Projectile projectile, org.bukkit.event.Event event,
                          Entity target, org.bukkit.Location location, double damage) {
        ProcessingGuard guard = ProcessingGuard.enter(services.config().limits());
        try {
            EnchantmentContext context = EnchantmentContext.builder(action, item, guard)
                    .player(player)
                    .itemCapabilities(services.scanner().scan(item))
                    .target(target)
                    .projectile(projectile)
                    .damageAmount(damage)
                    .location(location)
                    .event(event)
                    .build();
            services.dispatcher().dispatch(context);
        } finally {
            ProcessingGuard.exit();
        }
    }
}
