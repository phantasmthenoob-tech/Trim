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
    /** Single repeating reconcile task handle. One task total (never one per
     * entity): stored so disable/reload cancels it explicitly instead of
     * relying on platform cleanup alone. */
    private org.bukkit.scheduler.BukkitTask task;

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
        stop(); // never stack a second timer across reload cycles
        int period = Math.max(5, plugin.getConfig().getInt("settings.behavior.anchor-check-ticks", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            reconcileAnchors();
            reconcileManaged();
        }, period, period);
    }

    /** Cancels the reconcile timer. Called from {@code onDisable}; Bukkit
     * would also drop it, but an explicit handle means no uncontrolled
     * repeating work can outlive the plugin. World unloads need no handling:
     * unloaded entities resolve to null and are skipped, never teleported. */
    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
            task = null;
        }
    }

    /** Re-asserts managed AI state (AI on/off, passive target suppression) so
     * profile switches survive reloads, chunk unloads, and other plugins
     * fighting over the same mob. Touches managed mobs only — never scans
     * every entity in any world. */
    private void reconcileManaged() {
        for (ManagedRecord record : managed.values()) {
            Entity entity = Bukkit.getEntity(record.entityUuid());
            if (!(entity instanceof Mob mob) || !entity.isValid() || entity.isDead()) {
                continue;
            }
            if (record.profile() == ManagedRecord.Profile.PASSIVE) {
                // PASSIVE is absolute: no target, no awareness, no AI. The AI
                // line matters — without it another plugin (or a reload) can
                // silently re-enable the brain while the record claims passive.
                if (mob.getTarget() != null) {
                    mob.setTarget(null);
                }
                if (mob.isAware()) {
                    mob.setAware(false);
                }
                if (mob.hasAI()) {
                    mob.setAI(false);
                }
                continue;
            }
            LivingEntity living = mob;
            if (record.aiEnabled() && !living.hasAI()) {
                living.setAI(true);
            } else if (!record.aiEnabled() && living.hasAI()) {
                living.setAI(false);
            }
            reassertCommand(mob, record);
        }
    }

    /** Assigns a target through allegiance rules and remembers the order, so
     * the reconcile timer can restore it when vanilla AI drops it (lost
     * sight, path timeout, chunk flicker). Vanilla keeps a differently
     * acquired target — only a *lost* order is restored, never fought over. */
    private static void assign(Mob mob, ManagedRecord record, LivingEntity target) {
        mob.setTarget(target);
        record.command(target.getUniqueId(), System.currentTimeMillis());
    }

    /** Maximum distance (blocks) at which a lost commanded target is
     * re-asserted. Beyond this the mob could never path back; re-asserting
     * would just spin vanilla targeting forever. */
    static final double REASSERT_RANGE = 64.0;

    private static void reassertCommand(Mob mob, ManagedRecord record) {
        UUID cmd = record.commandedTarget();
        if (cmd == null) {
            return;
        }
        Entity target = Bukkit.getEntity(cmd);
        if (!(target instanceof LivingEntity living) || !target.isValid() || target.isDead()) {
            record.clearCommand();
            return;
        }
        if (target.getWorld() == null || mob.getWorld() == null
                || !target.getWorld().equals(mob.getWorld())) {
            record.clearCommand(); // cross-world orders are meaningless aggro
            return;
        }
        if (!engageableStatic(mob, living, record)) {
            // Became illegal (allied now, type-filtered, ...): drop the order
            // instead of re-asserting a forbidden target forever.
            record.clearCommand();
            return;
        }
        LivingEntity current = mob.getTarget();
        if (current != null) {
            return; // still on it, or vanilla acquired something else: respect it
        }
        double distSq = mob.getLocation().distanceSquared(target.getLocation());
        if (!shouldReassert(true, distSq)) {
            return; // too far to path: keep the order, retry when closer
        }
        mob.setTarget(living);
    }

    /** Pure range gate for commanded-target re-assertion (headless tested). */
    public static boolean shouldReassert(boolean sameWorld, double distSq) {
        return sameWorld && distSq >= 0 && distSq <= REASSERT_RANGE * REASSERT_RANGE;
    }

    /** engageable() without an instance (reconcile path is static context). */
    private static boolean engageableStatic(Mob mob, LivingEntity target, ManagedRecord record) {
        if (!target.isValid() || target.isDead() || target.equals(mob)) {
            return false;
        }
        if (target.getWorld() == null || mob.getWorld() == null
                || !target.getWorld().equals(mob.getWorld())) {
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
                    assign(mob, record, attacker);
                }
                continue;
            }
            // Case 2: owner was attacked → defend owner.
            if (record.defendOwner() && record.owner() != null
                    && victim.getUniqueId().equals(record.owner())
                    && AllegianceRules.shouldDefend(attacker.getUniqueId(), true,
                            record.trusted(), java.util.Set.of(record.owner()))
                    && engageable(mob, attacker, record)) {
                assign(mob, record, attacker);
                continue;
            }
            // Case 3: a trusted ally was attacked → defend allies.
            if (record.defendAllies() && record.trusted().contains(victim.getUniqueId())
                    && AllegianceRules.shouldDefend(attacker.getUniqueId(), true,
                            record.trusted(), java.util.Set.of(record.owner()))
                    && engageable(mob, attacker, record)) {
                assign(mob, record, attacker);
            }
            // Case 4: owner attacks something → guard joins in.
            if (record.attackOwnerTarget() && record.owner() != null
                    && attacker.getUniqueId().equals(record.owner())
                    && victim instanceof LivingEntity target
                    && engageable(mob, target, record)) {
                assign(mob, record, target);
            }
        }
    }

    /** Owner and trusted allies are never valid targets; anyone else must pass
     * the type filters. Retaliation counts as an explicit-enemy signal.
     * Cross-world targets are refused: the mob could never path to them,
     * and a cross-dimension target assignment is meaningless aggro. */
    private boolean engageable(Mob mob, LivingEntity target, ManagedRecord record) {
        return engageableStatic(mob, target, record);
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

    /** Cleanup on death. A dead mob never returns under the same UUID, so its
     * managed record is permanently useless: drop it instead of reconciling
     * a ghost forever. Chunk unloads do NOT fire this event, so guards that
     * unload keep their configuration and reconcile when available again.
     * Players respawn under the SAME UUID, so player records are kept —
     * only genuinely one-way entity UUIDs are pruned, including from every
     * other record's trusted/enemy lists and owner slots that referenced
     * the dead entity. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        UUID dead = event.getEntity().getUniqueId();
        if (event.getEntity() instanceof Player) {
            return; // same UUID on respawn: keep the player's record
        }
        managed.remove(dead);
        for (ManagedRecord r : managed.values()) {
            r.trusted().remove(dead);
            r.enemies().remove(dead);
            if (dead.equals(r.owner())) {
                r.owner(null);
            }
            if (dead.equals(r.commandedTarget())) {
                r.clearCommand();
            }
        }
    }

    /** True when the record holds a live, same-world commanded target.
     * Used so soft anchors (and only they) yield to owner orders. */
    static boolean hasActiveCommand(Entity entity, ManagedRecord record) {
        UUID cmd = record.commandedTarget();
        if (cmd == null) {
            return false;
        }
        Entity target = Bukkit.getEntity(cmd);
        return target != null && target.isValid() && !target.isDead()
                && target.getWorld() != null && target.getWorld().equals(entity.getWorld());
    }

    /** Pure anchor-distance decisions, extracted for headless tests.
     * Hard anchors correct drift beyond ~0.2 blocks (squared: 0.04) while
     * tolerating tiny physics jitter; soft anchors only settle pursuit past
     * the configured tolerance, never teleport. */
    public static boolean shouldCorrectHard(double distSq) {
        return distSq > 0.04;
    }

    public static boolean shouldSettleSoft(double distSq, double tolerance) {
        return distSq > tolerance * tolerance;
    }

    /** Parses a comma-separated entity-type filter (e.g. "zombie, skeleton")
     * into uppercase type names. Unknown tokens fail the whole input with
     * the offending token named — a filter never half-applies. Plain
     * {@code EntityType} enum: safe without a server. */
    public static TypeFilterResult parseTypeFilter(String input) {
        if (input == null || input.isBlank()) {
            return new TypeFilterResult(java.util.Set.of(), null);
        }
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (String token : input.split(",")) {
            String t = token.trim().toUpperCase(java.util.Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(org.bukkit.entity.EntityType.valueOf(t).name());
            } catch (IllegalArgumentException e) {
                return new TypeFilterResult(null,
                        "Unknown entity type '" + token.trim() + "'.");
            }
        }
        return new TypeFilterResult(java.util.Set.copyOf(out), null);
    }

    public record TypeFilterResult(java.util.Set<String> types, String error) {
        public boolean ok() {
            return error == null;
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
                if (shouldCorrectHard(distSq)) {
                    Location back = new Location(at.getWorld(), record.anchorX(), record.anchorY(),
                            record.anchorZ(), record.anchorYaw(), record.anchorPitch());
                    entity.teleport(back);
                    entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                }
            } else if (shouldSettleSoft(distSq, tolerance)) {
                // Soft anchor: settle drift instead of teleporting — but never
                // steal an active commanded target. Owner orders win over
                // anchoring; the anchor still holds once the fight is over.
                if (entity instanceof Mob mob && !hasActiveCommand(entity, record)) {
                    mob.setTarget(null);
                }
                if (!hasActiveCommand(entity, record)) {
                    entity.setVelocity(new org.bukkit.util.Vector(0, entity.getVelocity().getY(), 0));
                }
            }
        }
    }

    // ---------------------------------------------------------------- profiles

    /** Applies a behavior profile template. All writes are absolute, never
     * toggles, so applying the same profile twice is a no-op relative to the
     * first application (idempotent) — verified by unit tests.
     *
     * <p>Native vs custom, honestly separated:
     * <ul>
     *   <li>Native controls: AI on/off, awareness, live target. These are
     *   plain Bukkit toggles and do NOT by themselves constitute a behavior
     *   mode — vanilla, other plugins, and special mob brains (Warden
     *   vibration anger, boss phases) can still drive the entity.</li>
     *   <li>Custom logic: the record's defend/retaliate/join/friendly-fire
     *   flags plus allowed/excluded type filters, enforced by
     *   event listeners ({@code onDamage}, {@code onTarget},
     *   {@code onFriendlyFire}) and re-asserted by the reconcile timer.</li>
     * </ul>
     * Profiles that need a brain always re-enable AI + awareness, so switching
     * from PASSIVE/STATIONARY back to GUARD (or any active profile) cannot
     * leave the mob brain-dead. Requires {@code Mob}: entities without
     * native targeting (armor stands, projectiles, vehicles) cannot take
     * profiles — callers must gate on {@code instanceof Mob}. */
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
                // STATIONARY is AI-off, not an anchor: the entity still drifts
                // with knockback, water, and vehicles. Combine with a soft or
                // hard anchor to hold a position (documented in behavior.md).
                mob.setTarget(null);
                living.setAI(false);
                record.aiEnabled(false);
                record.defendOwner(false);
                record.defendAllies(false);
                record.retaliate(false);
                record.attackOwnerTarget(false);
                record.profile(profile);
                yield "Stationary: AI off (gravity/knockback still apply). Anchor to hold position.";
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
