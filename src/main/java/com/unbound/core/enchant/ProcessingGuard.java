package com.unbound.core.enchant;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scoped safety guard for one top-level action (one Bukkit event processed by
 * this plugin). Tracks:
 *
 * <ul>
 *   <li>recursion depth — effects that trigger further events (e.g.
 *       Multishot calling {@code damage()} on extra targets) produce nested
 *       events; handlers must not re-run effects on nested events, and
 *       effects must never chain beyond {@link Limits#maxDepth};</li>
 *   <li>action budgets — how many entities, blocks and projectiles a single
 *       top-level action may affect, shared by every effect running in that
 *       action.</li>
 * </ul>
 *
 * <p>Guards are thread-local because Bukkit events are synchronous. Handlers
 * call {@link #enter(Limits)} before dispatching and {@link #exit()} in a
 * finally block. A guard obtained while another is active shares the parent's
 * budgets and increments the depth, which also marks the processing as
 * {@link #isSynthetic()} (triggered by our own effects).</p>
 *
 * <p>This class is intentionally free of any Bukkit imports so the recursion
 * and limit logic can be unit tested directly.</p>
 */
public final class ProcessingGuard {

    /** Hard safety limits for a single top-level action. */
    public record Limits(int maxEntities, int maxBlocks, int maxProjectiles, int maxDepth) {
        public Limits {
            if (maxEntities < 0 || maxBlocks < 0 || maxProjectiles < 0 || maxDepth < 0) {
                throw new IllegalArgumentException("limits must be non-negative");
            }
        }
    }

    private static final ThreadLocal<ProcessingGuard> CURRENT = new ThreadLocal<>();

    private final ProcessingGuard parent;
    private final Limits limits;
    private final int depth;
    /** Shared budget counters live on the root guard. */
    private final AtomicInteger entitiesAffected;
    private final AtomicInteger blocksAffected;
    private final AtomicInteger projectilesCreated;

    private ProcessingGuard(ProcessingGuard parent, Limits limits, int depth,
                            AtomicInteger entities, AtomicInteger blocks, AtomicInteger projectiles) {
        this.parent = parent;
        this.limits = limits;
        this.depth = depth;
        this.entitiesAffected = entities;
        this.blocksAffected = blocks;
        this.projectilesCreated = projectiles;
    }

    /** Opens a fresh root scope for a top-level action. */
    public static ProcessingGuard enter(Limits limits) {
        ProcessingGuard previous = CURRENT.get();
        ProcessingGuard guard = new ProcessingGuard(previous, limits, previous == null ? 0 : previous.depth + 1,
                previous == null ? new AtomicInteger() : previous.entitiesAffected,
                previous == null ? new AtomicInteger() : previous.blocksAffected,
                previous == null ? new AtomicInteger() : previous.projectilesCreated);
        CURRENT.set(guard);
        return guard;
    }

    /** Closes the innermost scope. Must be called in a finally block. */
    public static void exit() {
        ProcessingGuard current = CURRENT.get();
        if (current == null) {
            return;
        }
        if (current.parent == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(current.parent);
        }
    }

    /** The guard for the current thread, or null when outside processing. */
    public static ProcessingGuard current() {
        return CURRENT.get();
    }

    /**
     * True while this thread is already inside a processing scope. Handlers
     * must skip events in this state: any event that fires while a guard is
     * open was triggered by one of our own effects (nested event) and must
     * never be re-processed — this is the recursion protection.
     */
    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    /** How deep below the top-level action this scope is. */
    public int depth() {
        return depth;
    }

    /** True when this scope was opened by one of our own effects (nested event). */
    public boolean isSynthetic() {
        return depth > 0;
    }

    /** True when further effect chaining must stop. */
    public boolean isDepthExceeded() {
        return depth >= limits.maxDepth();
    }

    public int entitiesAffected() {
        return entitiesAffected.get();
    }

    public int blocksAffected() {
        return blocksAffected.get();
    }

    public int projectilesCreated() {
        return projectilesCreated.get();
    }

    /**
     * Reserves up to {@code wanted} entity slots for this action.
     *
     * @return the number of slots actually granted (may be 0)
     */
    public int consumeEntitySlots(int wanted) {
        return consume(entitiesAffected, wanted, limits.maxEntities());
    }

    /** Reserves up to {@code wanted} block slots. */
    public int consumeBlockSlots(int wanted) {
        return consume(blocksAffected, wanted, limits.maxBlocks());
    }

    /** Reserves up to {@code wanted} projectile slots. */
    public int consumeProjectileSlots(int wanted) {
        return consume(projectilesCreated, wanted, limits.maxProjectiles());
    }

    /** Cheap pre-check without reserving. */
    public boolean hasEntityBudget() {
        return entitiesAffected.get() < limits.maxEntities();
    }

    public boolean hasProjectileBudget() {
        return projectilesCreated.get() < limits.maxProjectiles();
    }

    public boolean hasBlockBudget() {
        return blocksAffected.get() < limits.maxBlocks();
    }

    private static int consume(AtomicInteger counter, int wanted, int max) {
        if (wanted <= 0) {
            return 0;
        }
        while (true) {
            int current = counter.get();
            int granted = Math.min(wanted, Math.max(0, max - current));
            if (granted == 0) {
                return 0;
            }
            if (counter.compareAndSet(current, current + granted)) {
                return granted;
            }
        }
    }
}
