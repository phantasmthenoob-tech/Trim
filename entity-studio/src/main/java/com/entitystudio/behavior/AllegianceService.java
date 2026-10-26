package com.entitystudio.behavior;

import com.entitystudio.EntityStudioPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores plugin-managed ownership/trust/enemy rules and enforces them with
 * targeted event listeners (no per-tick full-entity scans) plus a small
 * repeating task that only touches anchored entities.
 */
public class AllegianceService implements Listener {

    private final EntityStudioPlugin plugin;
    private final Map<UUID, ManagedRecord> managed = new ConcurrentHashMap<>();

    public AllegianceService(EntityStudioPlugin plugin) {
        this.plugin = plugin;
    }

    public ManagedRecord getOrCreate(Entity entity) {
        return managed.computeIfAbsent(entity.getUniqueId(), id -> {
            ManagedRecord r = new ManagedRecord(id);
            r.entityType(entity.getType().name());
            return r;
        });
    }

    public ManagedRecord get(UUID entityUuid) {
        return managed.get(entityUuid);
    }

    public boolean isManaged(UUID entityUuid) {
        return managed.containsKey(entityUuid);
    }

    public void forget(UUID entityUuid) {
        managed.remove(entityUuid);
    }

    public Map<UUID, ManagedRecord> all() {
        return managed;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int period = Math.max(5, plugin.getConfig().getInt("settings.behavior.anchor-check-ticks", 10));
        Bukkit.getScheduler().runTaskTimer(plugin, this::reconcileAnchors, period, period);
    }

    // ------------------------------------------------------------ retaliation

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("settings.behavior.enable-custom-allegiance", true)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity damager = event.getDamager();

        for (ManagedRecord record : managed.values()) {
            Entity guard = Bukkit.getEntity(record.entityUuid());
            if (!(guard instanceof Mob mob) || !guard.isValid() || guard.isDead()) {
                continue;
            }
            // Case 1: the managed mob itself was attacked → retaliate.
            if (record.retaliate() && victim.getUniqueId().equals(record.entityUuid())) {
                if (damager instanceof LivingEntity target && engageable(mob, target, record)) {
                    mob.setTarget(target);
                }
                continue;
            }
            // Case 2: owner was attacked → defend owner.
            if (record.defendOwner() && record.owner() != null
                    && victim.getUniqueId().equals(record.owner())
                    && damager instanceof LivingEntity target
                    && AllegianceRules.shouldDefend(target.getUniqueId(), true,
                            record.trusted(), java.util.Set.of(record.owner()))
                    && engageable(mob, target, record)) {
                mob.setTarget(target);
                continue;
            }
            // Case 3: a trusted ally was attacked → defend allies.
            if (record.defendAllies() && record.trusted().contains(victim.getUniqueId())
                    && damager instanceof LivingEntity target
                    && AllegianceRules.shouldDefend(target.getUniqueId(), true,
                            record.trusted(), java.util.Set.of(record.owner()))
                    && engageable(mob, target, record)) {
                mob.setTarget(target);
            }
            // Case 4: owner attacks something → guard joins in.
            if (record.attackOwnerTarget() && record.owner() != null
                    && damager.getUniqueId().equals(record.owner())
                    && victim instanceof LivingEntity target
                    && engageable(mob, target, record)) {
                mob.setTarget(target);
            }
        }
    }

    /** Owner and trusted allies are never valid targets; anyone else must pass
     * the type filters. Retaliation counts as an explicit-enemy signal. */
    private boolean engageable(Mob mob, LivingEntity target, ManagedRecord record) {
        if (!target.isValid() || target.isDead() || target.equals(mob)) {
            return false;
        }
        UUID tid = target.getUniqueId();
        AllegianceRules.TargetContext ctx = new AllegianceRules.TargetContext(true,
                record.owner() != null && record.owner().equals(tid),
                record.trusted().contains(tid),
                true,
                false,
                target.getType().name(),
                record.allowedTypes(), record.excludedTypes());
        return AllegianceRules.evaluate(ctx) == AllegianceRules.Decision.ATTACK;
    }

    // -------------------------------------------------------------- protection

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!plugin.getConfig().getBoolean("settings.behavior.enable-custom-allegiance", true)) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob) || event.getTarget() == null) {
            return;
        }
        ManagedRecord record = managed.get(mob.getUniqueId());
        if (record == null) {
            return;
        }
        Entity target = event.getTarget();
        UUID tid = target.getUniqueId();
        boolean isOwner = record.owner() != null && record.owner().equals(tid);
        if (isOwner || record.trusted().contains(tid)) {
            event.setCancelled(true);
            if (record.clearInvalidTarget()) {
                mob.setTarget(null);
            }
        }
    }

    // ---------------------------------------------------------------- anchors

    private void reconcileAnchors() {
        double tolerance = plugin.getConfig().getDouble("settings.behavior.soft-anchor-tolerance", 3.0);
        for (ManagedRecord record : managed.values()) {
            if (!record.anchored() || record.anchorWorld() == null) {
                continue;
            }
            Entity entity = Bukkit.getEntity(record.entityUuid());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                continue;
            }
            Location at = entity.getLocation();
            if (!at.getWorld().getName().equals(record.anchorWorld())) {
                continue; // changed worlds: leave it, don't yoink across dimensions
            }
            double dx = at.getX() - record.anchorX();
            double dy = at.getY() - record.anchorY();
            double dz = at.getZ() - record.anchorZ();
            double distSq = dx * dx + dy * dy + dz * dz;
            if (record.hardAnchor()) {
                if (distSq > 0.04) { // ~0.2 blocks: correct drift, allow tiny physics jitter
                    Location back = new Location(at.getWorld(), record.anchorX(), record.anchorY(),
                            record.anchorZ(), record.anchorYaw(), record.anchorPitch());
                    entity.teleport(back);
                    entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            } else if (distSq > tolerance * tolerance) {
                // Soft anchor: stop pursuit and let it settle nearby instead of
                // teleporting — avoids jitter and fights with vanilla movement.
                if (entity instanceof Mob mob) {
                    mob.setTarget(null);
                }
                entity.setVelocity(new org.bukkit.util.Vector(0, entity.getVelocity().getY(), 0));
            }
        }
    }

    // ---------------------------------------------------------------- profiles

    /** Applies a behavior profile template (best-effort, honestly scoped). */
    public static String applyProfile(Mob mob, ManagedRecord.Profile profile, ManagedRecord record) {
        return switch (profile) {
            case PASSIVE -> {
                mob.setTarget(null);
                mob.setAware(false);
                record.aiEnabled(false);
                record.profile(profile);
                yield "Passive: target cleared, awareness off. Re-enable to restore.";
            }
            case NEUTRAL -> {
                mob.setAware(true);
                record.aiEnabled(true);
                record.profile(profile);
                yield "Neutral: vanilla retaliation only (configure enemies for more).";
            }
            case GUARD -> {
                mob.setAware(true);
                record.aiEnabled(true);
                record.defendOwner(true);
                record.retaliate(true);
                record.profile(profile);
                yield "Guard: defends owner, retaliates. Set owner + trusted players.";
            }
            case AGGRESSIVE -> {
                mob.setAware(true);
                record.aiEnabled(true);
                record.profile(profile);
                yield "Aggressive: vanilla targeting rules apply (use allowed/excluded types).";
            }
            case STATIONARY -> {
                mob.setTarget(null);
                if (mob instanceof LivingEntity living) {
                    living.setAI(false);
                }
                record.aiEnabled(false);
                record.profile(profile);
                yield "Stationary: AI off (gravity/knockback still apply). Re-enable AI to restore.";
            }
            case CUSTOM -> {
                record.profile(profile);
                yield "Custom: configure each rule individually.";
            }
        };
    }
}
