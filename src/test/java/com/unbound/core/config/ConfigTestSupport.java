package com.unbound.core.config;

import org.jetbrains.annotations.Nullable;

/**
 * Test-only factory for {@link UnboundConfig} instances. Lives in the config
 * package so it can use the package-private constructor to build variants
 * (e.g. a config with a specific InfinitySettings) without a Bukkit server.
 */
public final class ConfigTestSupport {

    private ConfigTestSupport() {
    }

    /** The shipped default configuration, as the plugin would parse it. */
    public static UnboundConfig defaults() {
        return ConfigParser.parse(DefaultConfig.asMap());
    }

    /**
     * The default configuration with Infinity replaced. {@code null} keeps
     * the defaults untouched.
     */
    public static UnboundConfig configWithInfinity(@Nullable UnboundConfig.InfinitySettings infinity) {
        UnboundConfig base = defaults();
        if (infinity == null) {
            return base;
        }
        return withInfinity(base, infinity);
    }

    static UnboundConfig withInfinity(UnboundConfig base, UnboundConfig.InfinitySettings infinity) {
        return new UnboundConfig(
                base.enchantingTableEnabled(),
                base.enchantingDenyMessage(),
                base.debugEnabled(),
                base.debugAllowPlayerToggle(),
                base.debugShowTiming(),
                base.limits(),
                base.enchantToggles(),
                infinity,
                base.unbreaking(),
                base.mending(),
                base.efficiency(),
                base.quickCharge(),
                base.multishot(),
                base.sharpness(),
                base.power(),
                base.punch(),
                base.fortune(),
                base.looting(),
                base.veil(),
                base.ignite(),
                base.sweep(),
                base.thorns(),
                base.silkTouch(),
                base.luck(),
                base.channeling(),
                base.riptide(),
                base.impaling(),
                base.breach(),
                base.piercing(),
                base.windBurst());
    }
}
