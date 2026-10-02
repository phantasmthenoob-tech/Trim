package com.unbound.inspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /enderchest edit &lt;username&gt; — opens the target player's ender chest
 * as a live container: taking, removing and replacing items edits the real
 * ender chest, wherever the target is. /enderchest without arguments opens
 * your own (vanilla parity).
 *
 * <p>Permission: {@code unbound.enderchest}.</p>
 */
public final class EnderChestCommand implements TabExecutor {

    public EnderChestCommand() {
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cOnly players can open ender chests."));
            return true;
        }
        // Own ender chest needs no extra permission (vanilla behavior).
        if (args.length == 0) {
            player.openInventory(player.getEnderChest());
            return true;
        }
        if (!args[0].equalsIgnoreCase("edit")) {
            player.sendMessage(Component.text("§cUsage: /enderchest edit <username>"));
            return true;
        }
        if (!player.hasPermission("unbound.enderchest")) {
            player.sendMessage(Component.text("§cYou lack permission (unbound.enderchest)."));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("§cUsage: /enderchest edit <username>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text("§cPlayer not found: §f" + args[1]));
            return true;
        }
        player.openInventory(target.getEnderChest());
        if (target != player) {
            player.sendMessage(Component.text("§aOpened §b" + target.getName()
                    + "§a's ender chest (live)."));
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("unbound.enderchest")) {
                options.add("edit");
            }
            return options.stream()
                    .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")
                && sender.hasPermission("unbound.enderchest")) {
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(online -> names.add(online.getName()));
            return names.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
