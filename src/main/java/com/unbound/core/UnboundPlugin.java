package com.unbound.core;

import com.unbound.core.command.UnboundCommand;
import com.unbound.core.config.ConfigManager;
import com.unbound.inspect.EnderChestCommand;
import com.unbound.inspect.InspectHandler;
import com.unbound.core.enchant.handlers.AttackHandler;
import com.unbound.core.enchant.handlers.BlockBreakHandler;
import com.unbound.core.enchant.handlers.BlockPlaceHandler;
import com.unbound.core.enchant.handlers.EnchantingTableHandler;
import com.unbound.core.enchant.handlers.ExperienceHandler;
import com.unbound.core.enchant.handlers.ItemUseHandler;
import com.unbound.core.enchant.handlers.PlayerLifecycleHandler;
import com.unbound.core.enchant.handlers.ProjectileHandler;
import com.unbound.core.enchant.handlers.RiptideChargeHandler;
import com.unbound.core.enchant.handlers.ThornsHandler;
import com.unbound.core.enchant.handlers.VeilHandler;
import com.unbound.core.enchant.handlers.DurabilityHandler;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Unbound — universal enchantment mechanics for the Unbound SMP.
 *
 * <p>Startup wires together config → engine (definitions + effects) →
 * dispatcher → handlers. Nothing else happens here: behaviors live in
 * {@code com.unbound.core.enchant.effects}, event translation in
 * {@code com.unbound.core.enchant.handlers}, and all important numbers in
 * config.yml.</p>
 */
public final class UnboundPlugin extends JavaPlugin {

    private UnboundServices services;
    private RiptideChargeHandler riptideChargeHandler;

    @Override
    public void onEnable() {
        ConfigManager configManager = new ConfigManager(this, getLogger());
        this.services = new UnboundServices(this, getLogger(), configManager);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new AttackHandler(services), this);
        pluginManager.registerEvents(new BlockBreakHandler(services), this);
        pluginManager.registerEvents(new BlockPlaceHandler(services), this);
        pluginManager.registerEvents(new ItemUseHandler(services), this);
        pluginManager.registerEvents(new ProjectileHandler(services), this);
        pluginManager.registerEvents(new DurabilityHandler(services), this);
        pluginManager.registerEvents(new ExperienceHandler(services), this);
        pluginManager.registerEvents(new PlayerLifecycleHandler(services), this);
        pluginManager.registerEvents(new EnchantingTableHandler(services), this);
        pluginManager.registerEvents(new VeilHandler(services, java.util.Map.of(
                com.unbound.core.enchant.VeilType.ALL, services.veilState(),
                com.unbound.core.enchant.VeilType.FIRE, services.veilState(),
                com.unbound.core.enchant.VeilType.BLAST, services.veilState(),
                com.unbound.core.enchant.VeilType.PROJECTILE, services.veilState(),
                com.unbound.core.enchant.VeilType.FALL, services.veilState())), this);
        pluginManager.registerEvents(new ThornsHandler(services), this);
        this.riptideChargeHandler = new RiptideChargeHandler(services);
        pluginManager.registerEvents(this.riptideChargeHandler, this);
        pluginManager.registerEvents(new InspectHandler(), this);

        PluginCommand enderChest = getServer().getPluginCommand("enderchest");
        if (enderChest != null) {
            EnderChestCommand enderChestExecutor = new EnderChestCommand();
            enderChest.setExecutor(enderChestExecutor);
            enderChest.setTabCompleter(enderChestExecutor);
        }

        PluginCommand command = getCommand("unbound");
        if (command != null) {
            UnboundCommand executor = new UnboundCommand(services);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("Unbound enabled — enchantment restrictions are off.");
    }

    @Override
    public void onDisable() {
        if (riptideChargeHandler != null) {
            riptideChargeHandler.stop();
        }
        getLogger().info("Unbound disabled.");
    }
}
