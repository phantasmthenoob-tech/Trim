package com.unbound.core.enchant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an effect may need to know about the current action. Only
 * relevant fields are populated per action; typed accessors return
 * {@link Optional} so effects can express intent explicitly instead of
 * null-checking blindly.
 *
 * <p>Handlers build the context once per event; effects never touch the raw
 * Bukkit event except through {@link #event()} when a Bukkit-only detail
 * (e.g. modifying damage) is genuinely required.</p>
 */
public final class EnchantmentContext {

    private final ActionType action;
    @Nullable
    private final LivingEntity actor;
    @Nullable
    private final Player player;
    private final ItemStack item;
    private final Set<ItemCapability> itemCapabilities;
    private final int level;
    @Nullable
    private final EnchantmentDefinition definition;
    @Nullable
    private final Block block;
    @Nullable
    private final Entity target;
    @Nullable
    private final Projectile projectile;
    private final double damageAmount;
    private final int xpAmount;
    private final List<ItemStack> drops;
    @Nullable
    private final Location location;
    @Nullable
    private final Event event;
    private final ProcessingGuard guard;
    private boolean cancelled;

    private EnchantmentContext(Builder builder) {
        this.action = builder.action;
        this.actor = builder.actor;
        this.player = builder.player;
        this.item = builder.item;
        this.itemCapabilities = builder.itemCapabilities.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.itemCapabilities));
        this.level = builder.level;
        this.definition = builder.definition;
        this.block = builder.block;
        this.target = builder.target;
        this.projectile = builder.projectile;
        this.damageAmount = builder.damageAmount;
        this.xpAmount = builder.xpAmount;
        this.drops = builder.drops == null ? Collections.emptyList() : Collections.unmodifiableList(builder.drops);
        this.location = builder.location;
        this.event = builder.event;
        this.guard = builder.guard;
    }

    public ActionType action() {
        return action;
    }

    /** The entity performing the action (player for player actions). */
    public Optional<LivingEntity> actor() {
        return Optional.ofNullable(actor);
    }

    /** The player, when the action is player-driven. */
    public Optional<Player> player() {
        return Optional.ofNullable(player);
    }

    /** The item the enchantment was found on (held tool, consumed item...). */
    public ItemStack item() {
        return item;
    }

    public Set<ItemCapability> itemCapabilities() {
        return itemCapabilities;
    }

    /** Enchantment level already clamped to the definition's configured max. */
    public int level() {
        return level;
    }

    /** The definition being dispatched (set by the dispatcher per effect). */
    public Optional<EnchantmentDefinition> definition() {
        return Optional.ofNullable(definition);
    }

    public Optional<Block> block() {
        return Optional.ofNullable(block);
    }

    /** The entity acted upon: the victim of an attack, dying mob, hit target... */
    public Optional<Entity> target() {
        return Optional.ofNullable(target);
    }

    public Optional<Projectile> projectile() {
        return Optional.ofNullable(projectile);
    }

    /** Damage relevant to the action (event damage for attacks). */
    public double damageAmount() {
        return damageAmount;
    }

    /** XP involved in the action. */
    public int xpAmount() {
        return xpAmount;
    }

    /** Mutable-relevant drops view (never mutated directly by the engine). */
    public List<ItemStack> drops() {
        return drops;
    }

    public Optional<Location> location() {
        return Optional.ofNullable(location);
    }

    /** The originating Bukkit event, for effects that must adjust it directly. */
    public Optional<Event> event() {
        return Optional.ofNullable(event);
    }

    public ProcessingGuard guard() {
        return guard;
    }

    /**
     * Requests cancellation of the underlying event when it is cancellable
     * and marks the context cancelled so later effects can back off.
     *
     * @return true if the underlying event was cancellable and is now cancelled
     */
    public boolean cancelAction() {
        cancelled = true;
        if (event instanceof Cancellable cancellable) {
            cancellable.setCancelled(true);
            return true;
        }
        return false;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** Returns a copy of this context bound to another definition/level. */
    public EnchantmentContext withDefinition(EnchantmentDefinition newDefinition, int newLevel) {
        return toBuilder().definition(newDefinition).level(newLevel).build();
    }

    private Builder toBuilder() {
        return new Builder(action)
                .actor(actor)
                .player(player)
                .item(item)
                .itemCapabilities(itemCapabilities)
                .level(level)
                .definition(definition)
                .block(block)
                .target(target)
                .projectile(projectile)
                .damageAmount(damageAmount)
                .xpAmount(xpAmount)
                .drops(drops == null ? null : new ArrayList<>(drops))
                .location(location)
                .event(event)
                .guard(guard);
    }

    public static Builder builder(ActionType action, ItemStack item, ProcessingGuard guard) {
        return new Builder(action).item(item).guard(guard);
    }

    public static final class Builder {
        private final ActionType action;
        private LivingEntity actor;
        private Player player;
        private ItemStack item;
        private EnumSet<ItemCapability> itemCapabilities = EnumSet.noneOf(ItemCapability.class);
        private int level;
        private EnchantmentDefinition definition;
        private Block block;
        private Entity target;
        private Projectile projectile;
        private double damageAmount;
        private int xpAmount;
        private List<ItemStack> drops;
        private Location location;
        private Event event;
        private ProcessingGuard guard;

        private Builder(ActionType action) {
            this.action = action;
        }

        public Builder actor(@Nullable LivingEntity actor) {
            this.actor = actor;
            return this;
        }

        public Builder player(@Nullable Player player) {
            this.player = player;
            if (actor == null && player != null) {
                this.actor = player;
            }
            return this;
        }

        public Builder item(ItemStack item) {
            this.item = item;
            return this;
        }

        public Builder itemCapabilities(Set<ItemCapability> capabilities) {
            this.itemCapabilities = capabilities.isEmpty()
                    ? EnumSet.noneOf(ItemCapability.class)
                    : EnumSet.copyOf(capabilities);
            return this;
        }

        public Builder level(int level) {
            this.level = level;
            return this;
        }

        public Builder definition(@Nullable EnchantmentDefinition definition) {
            this.definition = definition;
            return this;
        }

        public Builder block(@Nullable Block block) {
            this.block = block;
            return this;
        }

        public Builder target(@Nullable Entity target) {
            this.target = target;
            return this;
        }

        public Builder projectile(@Nullable Projectile projectile) {
            this.projectile = projectile;
            return this;
        }

        public Builder damageAmount(double damageAmount) {
            this.damageAmount = damageAmount;
            return this;
        }

        public Builder xpAmount(int xpAmount) {
            this.xpAmount = xpAmount;
            return this;
        }

        public Builder drops(@Nullable List<ItemStack> drops) {
            this.drops = drops;
            return this;
        }

        public Builder location(@Nullable Location location) {
            this.location = location;
            return this;
        }

        public Builder event(@Nullable Event event) {
            this.event = event;
            return this;
        }

        public Builder guard(ProcessingGuard guard) {
            this.guard = guard;
            return this;
        }

        public EnchantmentContext build() {
            return new EnchantmentContext(this);
        }
    }
}
