package com.unbound.core.enchant;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.Nullable;

/**
 * Declarative description of one enchantment inside Unbound:
 * its vanilla handle, whether Unbound has it enabled, the configured maximum
 * level, the actions it participates in, its effect implementation and the
 * raw effect-specific config node.
 *
 * <p>Definitions are data; the engine never special-cases individual keys.
 * Enchantments without an {@link EnchantmentEffect} are registered as
 * pass-through placeholders (vanilla behavior applies where it exists, and a
 * future Unbound interpretation can be added without touching the core).</p>
 */
public final class EnchantmentDefinition {

    private final String key;
    @Nullable
    private final Enchantment handle;
    private final boolean enabled;
    private final int maxLevel;
    private final Set<ActionType> supportedActions;
    @Nullable
    private final EnchantmentEffect effect;
    private final Set<ItemCapability> requiredCapabilities;
    private final Map<String, Object> effectConfig;
    private final String description;

    private EnchantmentDefinition(Builder builder) {
        this.key = builder.key;
        this.handle = builder.handle;
        this.enabled = builder.enabled;
        this.maxLevel = builder.maxLevel;
        this.supportedActions = Collections.unmodifiableSet(EnumSet.copyOf(builder.supportedActions));
        this.effect = builder.effect;
        this.requiredCapabilities = builder.requiredCapabilities.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.requiredCapabilities));
        this.effectConfig = builder.effectConfig == null ? Map.of() : Map.copyOf(builder.effectConfig);
        this.description = builder.description;
    }

    /** Stable config key, e.g. {@code quick-charge}. */
    public String key() {
        return key;
    }

    /** The vanilla enchantment this definition extends; null in unit tests. */
    @Nullable
    public Enchantment handle() {
        return handle;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Plugin-configured maximum level; levels are clamped to this. */
    public int maxLevel() {
        return maxLevel;
    }

    public Set<ActionType> supportedActions() {
        return supportedActions;
    }

    @Nullable
    public EnchantmentEffect effect() {
        return effect;
    }

    /** Capabilities the held item must have for the effect to run; empty = unrestricted. */
    public Set<ItemCapability> requiredCapabilities() {
        return requiredCapabilities;
    }

    public Map<String, Object> effectConfig() {
        return effectConfig;
    }

    /** Human-readable summary used by /unbound info. */
    public String description() {
        return description;
    }

    public Builder toBuilder() {
        return new Builder(key, handle)
                .enabled(enabled)
                .maxLevel(maxLevel)
                .actions(supportedActions)
                .effect(effect)
                .requiredCapabilities(requiredCapabilities)
                .effectConfig(effectConfig)
                .description(description);
    }

    public static Builder builder(String key, @Nullable Enchantment handle) {
        return new Builder(key, handle);
    }

    public static final class Builder {
        private final String key;
        private final Enchantment handle;
        private boolean enabled = true;
        private int maxLevel = 1;
        private EnumSet<ActionType> supportedActions = EnumSet.noneOf(ActionType.class);
        @Nullable
        private EnchantmentEffect effect;
        private EnumSet<ItemCapability> requiredCapabilities = EnumSet.noneOf(ItemCapability.class);
        private Map<String, Object> effectConfig;
        private String description = "";

        private Builder(String key, @Nullable Enchantment handle) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key must not be blank");
            }
            this.key = key;
            this.handle = handle;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder maxLevel(int maxLevel) {
            this.maxLevel = Math.max(1, maxLevel);
            return this;
        }

        public Builder actions(ActionType... actions) {
            this.supportedActions = actions.length == 0 ? EnumSet.noneOf(ActionType.class) : EnumSet.copyOf(java.util.Arrays.asList(actions));
            return this;
        }

        public Builder actions(Set<ActionType> actions) {
            this.supportedActions = EnumSet.copyOf(actions);
            return this;
        }

        public Builder effect(@Nullable EnchantmentEffect effect) {
            this.effect = effect;
            return this;
        }

        public Builder requiredCapabilities(Set<ItemCapability> capabilities) {
            this.requiredCapabilities = capabilities.isEmpty()
                    ? EnumSet.noneOf(ItemCapability.class)
                    : EnumSet.copyOf(capabilities);
            return this;
        }

        public Builder requiredCapabilities(ItemCapability... capabilities) {
            return requiredCapabilities(ItemCapability.of(capabilities));
        }

        public Builder effectConfig(Map<String, Object> effectConfig) {
            this.effectConfig = effectConfig;
            return this;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        public EnchantmentDefinition build() {
            return new EnchantmentDefinition(this);
        }
    }
}
