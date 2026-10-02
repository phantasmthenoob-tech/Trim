package com.unbound.core;

import java.util.function.Supplier;
import java.util.logging.Logger;

import com.unbound.core.capability.CapabilityScanner;
import com.unbound.core.config.ConfigManager;
import com.unbound.core.config.UnboundConfig;
import com.unbound.core.debug.BukkitDebugService;
import com.unbound.core.enchant.ApplicabilityResolver;
import com.unbound.core.enchant.DebugSink;
import com.unbound.core.enchant.EffectDispatcher;
import com.unbound.core.enchant.UniversalEnchantmentEngine;
import com.unbound.core.enchant.VanillaDefinitions;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Holds the plugin's collaborating services. Handlers and effects keep a
 * single reference to this object and read through it at event time, so a
 * config reload can rebuild the engine + dispatcher without re-registering
 * listeners.
 */
public final class UnboundServices {

    private final Plugin plugin;
    private final Logger logger;
    private final ConfigManager configManager;
    private final CapabilityScanner scanner = new CapabilityScanner();
    private final ApplicabilityResolver resolver = new ApplicabilityResolver();
    private final BukkitDebugService debug;
    private volatile UnboundConfig config;
    private volatile UniversalEnchantmentEngine engine;
    private volatile EffectDispatcher dispatcher;

    public UnboundServices(Plugin plugin, Logger logger, ConfigManager configManager) {
        this.plugin = plugin;
        this.logger = logger;
        this.configManager = configManager;
        this.debug = new BukkitDebugService(this, logger);
        reload();
    }

    /** Rebuilds engine + dispatcher from the current config. */
    public synchronized void reload() {
        UnboundConfig loaded = configManager.load();
        this.config = loaded;
        UniversalEnchantmentEngine engine = new UniversalEnchantmentEngine(logger);
        VanillaDefinitions.registerAll(engine, this::config, plugin,
                new NamespacedKey(plugin, "efficiency_attack_speed"), logger);
        this.engine = engine;
        this.dispatcher = new EffectDispatcher(engine, resolver, loaded.limits(), debug, logger);
        logger.info(() -> "[Unbound] Loaded " + engine.size() + " enchantment definitions ("
                + loaded.enchantToggles().size() + " configured).");
    }

    public Plugin plugin() {
        return plugin;
    }

    public Logger logger() {
        return logger;
    }

    public UnboundConfig config() {
        return config;
    }

    public Supplier<UnboundConfig> configSupplier() {
        return this::config;
    }

    public UniversalEnchantmentEngine engine() {
        return engine;
    }

    public EffectDispatcher dispatcher() {
        return dispatcher;
    }

    public CapabilityScanner scanner() {
        return scanner;
    }

    public DebugSink debugSink() {
        return debug;
    }

    public BukkitDebugService debug() {
        return debug;
    }
}
