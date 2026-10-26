package com.minetranslate.command;

import com.minetranslate.MineTranslatePlugin;
import com.minetranslate.translate.Languages;
import com.minetranslate.translate.TranslateResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /translate [&lt;source&gt;] &lt;target&gt; &lt;text...&gt;
 * Also accepts "src&gt;dst" / "src:dst" / "src-&gt;dst" as first arg.
 */
public class TranslateCommand implements CommandExecutor, TabCompleter {

    private final MineTranslatePlugin plugin;

    public TranslateCommand(MineTranslatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("minetranslate.use")) {
            plugin.send(sender, plugin.getConfig().getString("messages.no-permission", "No permission."));
            return true;
        }
        // No text given (or /trgui): open the translation GUI for players.
        if (cmd.getName().equalsIgnoreCase("trgui") || args.length == 0) {
            if (sender instanceof org.bukkit.entity.Player p) {
                plugin.gui().openMain(p);
                return true;
            }
            plugin.send(sender, plugin.getConfig().getString("messages.players-only", "Players only."));
            return true;
        }
        if (args.length < 2) {
            plugin.send(sender, plugin.getConfig().getString("messages.usage-translate", "Usage: /translate <target> <text>"));
            return true;
        }

        String source = "auto";
        String target;
        int textFrom;

        String first = args[0];
        String pairSrc = parsePairSource(first);
        if (pairSrc != null) {
            source = pairSrc;
            target = parsePairTarget(first);
            textFrom = 1;
        } else if (args.length >= 3 && Languages.isSupported(args[0]) && Languages.isSupported(args[1])) {
            source = Languages.normalize(args[0]);
            target = Languages.normalize(args[1]);
            textFrom = 2;
        } else {
            target = Languages.normalize(args[0]);
            textFrom = 1;
            if (target == null) {
                // maybe they typed "/translate hello world" -> use default lang
                String def = Languages.normalize(plugin.getConfig().getString("default-language", "en"));
                target = def == null ? "en" : def;
                textFrom = 0;
            }
        }

        if (textFrom >= args.length) {
            plugin.send(sender, plugin.getConfig().getString("messages.usage-translate", "Usage: /translate <target> <text>"));
            return true;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, textFrom, args.length));
        if (text.isBlank()) {
            plugin.send(sender, plugin.getConfig().getString("messages.usage-translate", "Usage: /translate <target> <text>"));
            return true;
        }
        int max = plugin.getConfig().getInt("max-text-length", 1500) * 4;
        if (text.length() > max) {
            plugin.send(sender, "<red>Text too long (max " + max + " chars).");
            return true;
        }

        final String fSource = source;
        final String fTarget = target;
        plugin.send(sender, plugin.getConfig().getString("messages.translating", "Translating…"));
        plugin.service().translate(text, fSource, fTarget).whenComplete((res, err) -> {
            if (err != null || res == null) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.send(sender, "<red>Translation failed. Check console / internet. ("
                                + (err != null ? shortErr(err) : "?") + ")"));
                plugin.getLogger().warning("Translate failed: " + (err == null ? "null" : err.getMessage()));
                return;
            }
            TranslateResult r = res;
            plugin.getServer().getScheduler().runTask(plugin, () -> sendResult(sender, text, r));
        });
        return true;
    }

    private void sendResult(CommandSender sender, String original, TranslateResult r) {
        String header = plugin.getConfig().getString("messages.translated-header",
                "<gray>Translated <source> -> <target>:</gray>")
                .replace("<source>", escape(r.sourceLang()))
                .replace("<target>", escape(r.targetLang()));
        plugin.send(sender, header);
        Component body = plugin.mm().deserialize("<white>" + escape(r.text()) + "</white>")
                .hoverEvent(HoverEvent.showText(Component.text("Original: " + original)))
                .clickEvent(ClickEvent.copyToClipboard(r.text()));
        sender.sendMessage(body);
        sender.sendMessage(plugin.mm().deserialize("<dark_gray>Detected: "
                + escape(Languages.nameOf(r.sourceLang())) + " (" + escape(r.sourceLang())
                + ") ➜ " + escape(Languages.nameOf(r.targetLang())) + "</dark_gray>"));
    }

    private static String shortErr(Throwable t) {
        Throwable c = t.getCause() != null ? t.getCause() : t;
        String m = c.getMessage();
        return m == null ? c.getClass().getSimpleName() : m;
    }

    private static String escape(String s) {
        return s.replace("<", "&lt;");
    }

    private static String parsePairSource(String arg) {
        for (String sep : new String[] { "->", ">", ":" }) {
            int i = arg.indexOf(sep);
            if (i > 0) {
                String a = arg.substring(0, i);
                String b = arg.substring(i + sep.length());
                if (Languages.isSupported(a) && Languages.isSupported(b)) {
                    return Languages.normalize(a);
                }
            }
        }
        return null;
    }

    private static String parsePairTarget(String arg) {
        for (String sep : new String[] { "->", ">", ":" }) {
            int i = arg.indexOf(sep);
            if (i > 0) {
                String a = arg.substring(0, i);
                String b = arg.substring(i + sep.length());
                if (Languages.isSupported(a) && Languages.isSupported(b)) {
                    return Languages.normalize(b);
                }
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Languages.search(args[0]).stream().limit(20).collect(Collectors.toList());
        }
        if (args.length == 2 && Languages.isSupported(args[0])) {
            return Languages.search(args[1]).stream().limit(20).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
