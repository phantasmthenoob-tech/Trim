package com.entitystudio.behavior;

import com.entitystudio.EntityStudioPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
            r.friendlyFireProtect(plugin.getConfig()
                    .getBoolean("settings.behavior.friendly-fire-protect", true));
            return r;
        });
    }

    /** Resolves the living attacker behind a damage source, following
     * projectiles (arrows, trident, fireballs with shooters) back to whoever
     * fired them. Returns null for environmental damage. */
    public static LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile p && p.getShooter() instanceof LivingEntity s) {
            return s;
        }
        return null;
    }

    /** True when both sides belong to the same allegiance family: same
     * non-null owner with protection on, mutual owner/trust claims, or a
     * one-sided owner/trust claim from a lone managed record. An explicit
     * enemy marking on either side always wins over family. */
    public static boolean sharesAllegiance(ManagedRecord ra, UUID aId,
            ManagedRecord rb, UUID bId) {
        if (aId == null || aId.equals(bId)) {
            return false;
        }
        if (ra != null && ra.enemies().contains(bId)) {
            return false;
        }
        if (rb != null && rb.enemies().contains(aId)) {
            return false;
        }
        if (ra != null && rb != null) {
            if (!ra.friendlyFireProtect() || !rb.friendlyFireProtect()) {
                return false;
            }
            if (ra.owner() != null && ra.owner().equals(rb.owner())) {
                return true;
            }
            return ra.isAlly(bId) && rb.isAlly(aId);
        }
        if (ra != null) {
            return ra.friendlyFireProtect() && ra.isAlly(bId);
        }
        if (rb != null) {
            return rb.friendlyFireProtect() && rb.isAlly(aId);
        }
        return false;
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
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            reconcileAnchors();
            reconcileManaged();
        }, period, period);
    }

    /** Re-asserts managed AI state (AI on/off, passive target suppression) so
     * profile switches survive reloads, chunk unloads, and other plugins
     * fighting over the same mob. Touches managed mobs only. */
    private void reconcileManaged() {
        for (ManagedRecord record : managed.values()) {
            Entity entity = Bukkit.getEntity(record.entityUuid());
            if (!(entity instanceof Mob mob) || !entity.isValid() || entity.isDead()) {
                continue;
            }
            if (record.profile() == ManagedRecord.Profile.PASSIVE) {
                if (mob.getTarget() != null) {
                    mob.setTarget(null);
                }
                if (mob.isAware()) {
                    mob.setAware(false);
                }
                continue;
            }
            LivingEntity living = mob;
            if (record.aiEnabled() && !living.hasAI()) {
                living.setAI(true);
            } else if (!record.aiEnabled() && living.hasAI()) {
                living.setAI(false);
            }
        }
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
        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return; // environmental damage: no allegiance relevance
        }

        for (ManagedRecord record : managed.values()) {
            if (record.profile() == ManagedRecord.Profile.PASSIVE) {
                continue; // passive mobs never go on offense
            }
            Entity guard = Bukkit.getEntity(record.entityUuid());
            if (!(guard instanceof Mob mob) || !guard.isValid() || guard.isDead()) {
                continue;
            }
            // Case 1: the managed mob itself was attacked → retaliate.
            if (record.retaliate() && victim.getUniqueId().equals(record.entityUuid())) {
                if (engageable(mob, attacker, record)) {
                    mob.setTarget(attacker);
                }
                continue;
            }
            // Case 2: owner was attacked → defend owner.
            if (record.defendOwner() && record.owner() != null
                    && victim.getUniqueId().equals(record.owner())
                    && AllegianceRules.shouldDefend(attacker.getUniqueId(), true,
                            record.trusted(), java.util.Set.of(record.owner()))
                    && engageable(mob, attacker, record)) {
                mob.setTarget(attacker);
                continue;
            }
            // Case 3: a trusted ally was attacked → defend allies.
            if (record.defendAllies() && record.trusted().contains(victim.getUniqueId())
                    && AllegianceRules.shouldDefend(attacker.getUniqueId(), true,
                            record.trusted(), java.util.Set.of(record.owner()))
                    && engageable(mob, attacker, record)) {
                mob.setTarget(attacker);
            }
            // Case 4: owner attacks something → guard joins in.
            if (record.attackOwnerTarget() && record.owner() != null
                    && attacker.getUniqueId().equals(record.owner())
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

    // -------------------------------------------------------- friendly fire

    /** Cancels damage between members of the same allegiance family. Runs at
     * HIGH so the MONITOR retaliation handler below never sees friendly hits. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("settings.behavior.enable-custom-allegiance", true)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        ManagedRecord ra = managed.get(attacker.getUniqueId());
        ManagedRecord rb = managed.get(victim.getUniqueId());
        if (!sharesAllegiance(ra, attacker.getUniqueId(), rb, victim.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        if (attacker instanceof Mob mob && attacker.equals(mob.getTarget())) {
            mob.setTarget(null);
        }
        if (victim instanceof Mob vm && attacker.equals(vm.getTarget())) {
            vm.setTarget(null);
        }
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
        if (record.profile() == ManagedRecord.Profile.PASSIVE) {
            event.setCancelled(true);
            mob.setTarget(null);
            return;
        }
        boolean family = target instanceof LivingEntity lt
                && sharesAllegiance(record, mob.getUniqueId(),
                        managed.get(lt.getUniqueId()), lt.getUniqueId());
        boolean isOwner = record.owner() != null && record.owner().equals(tid);
        if (isOwner || record.trusted().contains(tid) || family) {
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

    /** Applies a behavior profile template (best-effort, honestly scoped).
     * Profiles that need a brain always re-enable AI + awareness, so switching
     * from PASSIVE/STATIONARY back to GUARD (or any active profile) cannot
     * leave the mob brain-dead. */
    public static String applyProfile(Mob mob, ManagedRecord.Profile profile, ManagedRecord record) {
        LivingEntity living = mob;
        return switch (profile) {
            case PASSIVE -> {
                mob.setTarget(null);
                mob.setAware(false);
                living.setAI(false);
                record.aiEnabled(false);
                record.defendOwner(false);
                record.defendAllies(false);
                record.retaliate(false);
                record.attackOwnerTarget(false);
                record.profile(profile);
                yield "Passive: AI off, never initiates or joins attacks.";
            }
            case NEUTRAL -> {
                living.setAI(true);
                mob.setAware(true);
                record.aiEnabled(true);
                record.defendOwner(false);
                record.defendAllies(false);
                record.retaliate(false);
                record.attackOwnerTarget(false);
                record.profile(profile);
                yield "Neutral: vanilla retaliation only (configure enemies for more).";
            }
            case GUARD -> {
                living.setAI(true);
                mob.setAware(true);
                record.aiEnabled(true);
                record.defendOwner(true);
                record.retaliate(true);
                record.profile(profile);
                yield "Guard: defends owner, retaliates. Set owner + trusted players.";
            }
            case AGGRESSIVE -> {
                living.setAI(true);
                mob.setAware(true);
                record.aiEnabled(true);
                record.profile(profile);
                yield "Aggressive: vanilla targeting rules apply (use allowed/excluded types).";
            }
            case STATIONARY -> {
                mob.setTarget(null);
                living.setAI(false);
                record.aiEnabled(false);
                record.defendOwner(false);
                record.defendAllies(false);
                record.retaliate(false);
                record.attackOwnerTarget(false);
                record.profile(profile);
                yield "Stationary: AI off (gravity/knockback still apply). Re-enable AI to restore.";
            }
            case CUSTOM -> {
                living.setAI(true);
                mob.setAware(true);
                record.aiEnabled(true);
                record.profile(profile);
                yield "Custom: brain restored, configure each rule individually.";
            }
        };
    }
}
