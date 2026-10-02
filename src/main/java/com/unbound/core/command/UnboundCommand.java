package com.unbound.core.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.unbound.core.UnboundServices;
import com.unbound.core.config.DefaultConfig;
import com.unbound.core.debug.BukkitDebugService;
import com.unbound.core.enchant.EnchantmentDefinition;
import com.unbound.core.enchant.UniversalEnchantmentEngine;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * /unbound [reload|info|debug|enchant &lt;player&gt; &lt;enchantment&gt; &lt;level&gt; [force]]
 *
 * <p>Permissions: {@code unbound.admin} (base), {@code unbound.reload},
 * {@code unbound.debug}, {@code unbound.enchant}. The enchant command applies
 * enchantments to any item regardless of vanilla compatibility, but never
 * exceeds the configured maximum level unless {@code force} is given.</p>
 */
public final class UnboundCommand implements TabExecutor {

    private final UnboundServices services;

    public UnboundCommand(UnboundServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            case "debug" -> handleDebug(sender);
            case "enchant" -> handleEnchant(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("unbound.reload")) {
            send(sender, "&cYou lack permission (unbound.reload).");
            return;
        }
        services.reload();
        send(sender, "&aUnbound reloaded: &f" + services.engine().size() + "&a definitions, "
                + "&f" + services.config().enchantToggles().size() + "&a configured toggles.");
    }

    private void handleInfo(CommandSender sender) {
        if (!sender.hasPermission("unbound.admin")) {
            send(sender, "&cYou lack permission (unbound.admin).");
            return;
        }
        UniversalEnchantmentEngine engine = services.engine();
        send(sender, "&6=== Unbound Enchantments ===");
        int implemented = 0;
        int placeholders = 0;
        for (EnchantmentDefinition definition : engine.definitions()) {
            if (definition.effect() != null && !definition.supportedActions().isEmpty()) {
                implemented++;
                send(sender, "&b" + definition.key() + " &7(max " + definition.maxLevel()
                        + (definition.enabled() ? "" : ", &cdisabled&7") + ") &8- &f" + definition.description());
            } else {
                placeholders++;
            }
        }
        send(sender, "&7" + implemented + " implemented, " + placeholders
                + " pass-through (vanilla behavior where possible).");
        send(sender, "&7Use &f/unbound enchant <player> <key> <level>&7 to apply anywhere.");
    }

    private void handleDebug(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "&cDebug output can only be toggled in-game.");
            return;
        }
        boolean enabled = services.debug().toggle(player);
        send(sender, enabled ? "&aUnbound debug output: &fon" : "&aUnbound debug output: &foff");
    }

    private void handleEnchant(CommandSender sender, String[] args) {
        if (!sender.hasPermission("unbound.enchant")) {
            send(sender, "&cYou lack permission (unbound.enchant).");
            return;
        }
        if (args.length < 4) {
            send(sender, "&cUsage: /unbound enchant <player> <enchantment> <level> [force]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            send(sender, "&cPlayer not found: &f" + args[1]);
            return;
        }
        EnchantmentDefinition definition = findDefinition(args[2]);
        if (definition == null) {
            send(sender, "&cUnknown Unbound enchantment: &f" + args[2]
                    + "&7 (see /unbound info)");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            send(sender, "&cLevel must be a number: &f" + args[3]);
            return;
        }
        boolean force = args.length >= 5 && args[4].equalsIgnoreCase("force");
        if (!force && level > definition.maxLevel()) {
            send(sender, "&cLevel " + level + " exceeds the configured maximum (&f"
                    + definition.maxLevel() + "&c). Append &fforce&c to override.");
            return;
        }
        ItemStack item = target.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            send(sender, "&c" + target.getName() + " must hold an item.");
            return;
        }
        int applied = services.engine().applyToItem(item, definition, level, force);
        if (applied <= 0) {
            send(sender, "&cCould not apply &f" + definition.key() + "&c to that item.");
            return;
        }
        send(sender, "&aApplied &b" + definition.key() + " " + applied
                + "&a to " + target.getName() + "'s " + item.getType().name().toLowerCase(Locale.ROOT) + ".");
        if (sender != target) {
            send(target, "&aYou received &b" + definition.key() + " " + applied + "&a on your held item.");
        }
    }

    private EnchantmentDefinition findDefinition(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT).replace('_', '-');
        return services.engine().get(normalized)
                .or(() -> services.engine().get(raw.toLowerCase(Locale.ROOT)))
                .orElse(null);
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "&6Unbound &7- universal enchantments");
        send(sender, "&f/unbound info &8- &7list enchantments");
        send(sender, "&f/unbound reload &8- &7reload the configuration");
        send(sender, "&f/unbound debug &8- &7toggle personal debug output");
        send(sender, "&f/unbound enchant <player> <enchantment> <level> [force]");
    }

    private void send(CommandSender sender, String legacyText) {
        Component component = BukkitDebugService.legacy(legacyText);
        sender.sendMessage(component);
    }

    // ---- tab completion -------------------------------------------------

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("info");
            if (sender.hasPermission("unbound.reload")) {
                options.add("reload");
            }
            if (sender.hasPermission("unbound.debug")) {
                options.add("debug");
            }
            if (sender.hasPermission("unbound.enchant")) {
                options.add("enchant");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("enchant") && sender.hasPermission("unbound.enchant")) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("enchant") && sender.hasPermission("unbound.enchant")) {
            List<String> keys = new ArrayList<>();
            for (EnchantmentDefinition definition : services.engine().definitions()) {
                if (definition.enabled()) {
                    keys.add(definition.key());
                }
            }
            return filter(keys, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("enchant") && sender.hasPermission("unbound.enchant")) {
            EnchantmentDefinition definition = findDefinition(args[2]);
            int max = definition == null ? DefaultConfig.LIMITS.maxDepth() : definition.maxLevel();
            List<String> levels = new ArrayList<>();
            for (int i = 1; i <= Math.min(max, 10); i++) {
                levels.add(String.valueOf(i));
            }
            if (sender.hasPermission("unbound.enchant") && definition != null) {
                levels.add(String.valueOf(definition.maxLevel()));
            }
            return filter(levels, args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("enchant") && sender.hasPermission("unbound.enchant")) {
            return filter(List.of("force"), args[4]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
