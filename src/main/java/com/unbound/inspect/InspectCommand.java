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
 * /inspect &lt;username&gt; — opens the target player's inventory as a live,
 * editable mirror (armor, hands, hotbar, storage): taking, removing and
 * replacing items edits the target's real inventory — no sneak-right-click
 * needed.
 *
 * <p>Restricted to the {@code ImNotAllocate} account (see
 * {@link InspectAccess}). The target must be online: Paper's public API
 * cannot open or edit offline player data.</p>
 */
public final class InspectCommand implements TabExecutor {

    private final InspectHandler handler;

    public InspectCommand(InspectHandler handler) {
        this.handler = handler;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("§cOnly players can open inventory views."));
            return true;
        }
        if (!InspectAccess.isAllowed(player)) {
            player.sendMessage(Component.text(InspectAccess.denialMessage()));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("§cUsage: /inspect <username>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text(InspectAccess.offlineMessage(args[1])));
            return true;
        }
        if (target == player) {
            player.openInventory(player.getInventory());
            return true;
        }
        String error = handler.openFor(player, target);
        if (error != null) {
            player.sendMessage(Component.text(InspectAccess.offlineMessage(args[1])));
            return true;
        }
        player.sendMessage(Component.text("§aOpened §b" + target.getName()
                + "§a's inventory (live, edits apply)."));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !InspectAccess.isAllowed(sender)) {
            return List.of();
        }
        // Online players only: Paper's API cannot open offline player data.
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(online -> names.add(online.getName()));
        return names.stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .sorted()
                .toList();
    }
}
