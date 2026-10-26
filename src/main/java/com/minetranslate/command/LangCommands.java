package com.minetranslate.command;

import com.minetranslate.MineTranslatePlugin;
import com.minetranslate.translate.Languages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Handles /mylang, /trchat, /languages and /translate-reload. */
public class LangCommands implements CommandExecutor, TabCompleter {

    private final MineTranslatePlugin plugin;

    public LangCommands(MineTranslatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return switch (cmd.getName().toLowerCase()) {
            case "mylang" -> myLang(sender, args);
            case "trchat" -> trChat(sender, args);
            case "languages" -> languages(sender, args);
            case "translate-reload" -> reload(sender);
            default -> false;
        };
    }

    private boolean myLang(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.send(sender, plugin.getConfig().getString("messages.players-only", "Players only."));
            return true;
        }
        if (args.length == 0) {
            String cur = plugin.store().language(p.getUniqueId());
            if (cur == null) {
                cur = plugin.getConfig().getString("default-language", "en");
            }
            plugin.send(sender, "<gray>Your language: <aqua>" + cur + "</aqua> ("
                    + Languages.nameOf(cur) + "). Change it: <yellow>/mylang <language>");
            return true;
        }
        String norm = Languages.normalize(args[0]);
        if (norm == null) {
            plugin.send(sender, plugin.getConfig().getString("messages.unknown-language", "Unknown language.")
                    .replace("<input>", args[0]));
            return true;
        }
        plugin.store().setLanguage(p.getUniqueId(), norm);
        plugin.send(sender, "<green>Your language set to <aqua>" + norm + "</aqua> (" + Languages.nameOf(norm) + ").");
        return true;
    }

    private boolean trChat(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.send(sender, plugin.getConfig().getString("messages.players-only", "Players only."));
            return true;
        }
        if (args.length == 0) {
            return status(sender, p);
        }
        String a = args[0].toLowerCase();
        switch (a) {
            case "off", "disable", "disabled", "no" -> {
                plugin.store().setAuto(p.getUniqueId(), false);
                plugin.send(sender, "<yellow>Chat auto-translation <red>OFF</red><yellow>.");
                return true;
            }
            case "status", "info" -> {
                return status(sender, p);
            }
            case "on", "enable", "enabled", "yes" -> {
                String lang = plugin.store().language(p.getUniqueId());
                if (lang == null) {
                    lang = plugin.getConfig().getString("default-language", "en");
                    plugin.store().setLanguage(p.getUniqueId(), lang);
                }
                plugin.store().setAuto(p.getUniqueId(), true);
                plugin.send(sender, "<green>Chat auto-translation <green>ON</green><gray> → "
                        + lang + " (" + Languages.nameOf(lang) + "). Change with <yellow>/trchat <language>");
                return true;
            }
            default -> {
                String norm = Languages.normalize(args[0]);
                if (norm == null) {
                    plugin.send(sender, plugin.getConfig().getString("messages.usage-trchat", "/trchat <language|off|status>"));
                    return true;
                }
                plugin.store().setLanguage(p.getUniqueId(), norm);
                plugin.store().setAuto(p.getUniqueId(), true);
                plugin.send(sender, "<green>Chat auto-translation <green>ON</green><gray> → <aqua>"
                        + norm + "</aqua> (" + Languages.nameOf(norm) + ").");
                return true;
            }
        }
    }

    private boolean status(CommandSender sender, Player p) {
        boolean auto = plugin.store().autoTranslate(p.getUniqueId());
        String lang = plugin.store().language(p.getUniqueId());
        if (lang == null) {
            lang = plugin.getConfig().getString("default-language", "en");
        }
        plugin.send(sender, "<gray>Auto-translate: " + (auto ? "<green>ON" : "<red>OFF")
                + "<gray>, language: <aqua>" + lang + "</aqua> (" + Languages.nameOf(lang) + ").");
        return true;
    }

    private boolean languages(CommandSender sender, String[] args) {
        String q = args.length == 0 ? "" : String.join(" ", args);
        List<String> found = Languages.search(q);
        if (found.isEmpty()) {
            plugin.send(sender, "<red>No languages match '" + q + "'.");
            return true;
        }
        plugin.send(sender, "<gray>Supported languages (" + found.size() + "):");
        // Print in compact columns, 5 per line.
        StringBuilder line = new StringBuilder("<white>");
        int n = 0;
        for (String code : found) {
            if (n > 0 && n % 5 == 0) {
                sender.sendMessage(plugin.mm().deserialize(line.toString()));
                line = new StringBuilder("<white>");
            }
            if (n % 5 != 0) {
                line.append("<dark_gray>, </dark_gray>");
            }
            line.append("<aqua>").append(code).append("</aqua><gray>(")
                    .append(Languages.nameOf(code).replace("<", "&lt;")).append(")</gray>");
            n++;
        }
        if (line.length() > 7) {
            sender.sendMessage(plugin.mm().deserialize(line.toString()));
        }
        sender.sendMessage(plugin.mm().deserialize(
                plugin.getConfig().getString("messages.prefix", "") + "<dark_gray>Use e.g. /translate es Hello</dark_gray>"));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("minetranslate.admin")) {
            plugin.send(sender, plugin.getConfig().getString("messages.no-permission", "No permission."));
            return true;
        }
        plugin.reloadServices();
        plugin.store().reload();
        plugin.send(sender, plugin.getConfig().getString("messages.reloaded", "Reloaded."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String name = cmd.getName().toLowerCase();
        if ((name.equals("mylang") || name.equals("trchat")) && args.length == 1) {
            List<String> out = new ArrayList<>();
            if (name.equals("trchat")) {
                out.add("on");
                out.add("off");
                out.add("status");
            }
            out.addAll(Languages.search(args[0]).stream().limit(15).toList());
            final String q = args[0].toLowerCase();
            return out.stream().filter(s -> s.toLowerCase().startsWith(q)).limit(20).collect(Collectors.toList());
        }
        if (name.equals("languages") && args.length == 1) {
            return Languages.search(args[0]).stream().limit(20).collect(Collectors.toList());
        }
        return List.of();
    }
}
