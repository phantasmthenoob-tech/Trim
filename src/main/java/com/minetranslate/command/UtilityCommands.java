package com.minetranslate.command;

import com.minetranslate.MineTranslatePlugin;
import com.minetranslate.translate.Languages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** /trsign and /trbook. */
public class UtilityCommands implements CommandExecutor, TabCompleter {

    private final MineTranslatePlugin plugin;

    public UtilityCommands(MineTranslatePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return switch (cmd.getName().toLowerCase()) {
            case "trsign" -> sign(sender, args);
            case "trbook" -> book(sender, args);
            default -> false;
        };
    }

    private boolean sign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.send(sender, plugin.getConfig().getString("messages.players-only", "Players only."));
            return true;
        }
        if (args.length < 1) {
            plugin.send(sender, plugin.getConfig().getString("messages.usage-trsign", "/trsign <target> [--apply]"));
            return true;
        }
        boolean apply = Arrays.asList(args).contains("--apply");
        String targetArg = args[0];
        String target = Languages.normalize(targetArg);
        if (target == null) {
            plugin.send(sender, plugin.getConfig().getString("messages.unknown-language", "Unknown language.")
                    .replace("<input>", targetArg));
            return true;
        }
        if (apply && !p.hasPermission("minetranslate.apply")) {
            plugin.send(sender, plugin.getConfig().getString("messages.no-permission", "No permission."));
            return true;
        }
        int maxDist = plugin.getConfig().getInt("signs.max-distance", 6);
        Block b = p.getTargetBlockExact(maxDist);
        if (b == null || !(b.getState() instanceof Sign sign)) {
            plugin.send(sender, "<red>Look at a sign within " + maxDist + " blocks first.");
            return true;
        }
        List<String> lines = new ArrayList<>(Arrays.asList(sign.getLines()));
        String text = String.join("\n", lines);
        if (text.isBlank()) {
            plugin.send(sender, "<red>That sign is blank - nothing to translate.");
            return true;
        }
        plugin.send(sender, plugin.getConfig().getString("messages.translating", "Translating…"));
        final String fTarget = target;
        plugin.service().translate(text.replace("\n", " / "), "auto", fTarget).whenComplete((res, err) -> {
            if (err != null || res == null) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> plugin.send(sender, "<red>Sign translation failed. Check console / internet."));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.send(sender, "<gray>Sign translated (<aqua>" + res.sourceLang() + "</aqua> ➜ <green>"
                        + res.targetLang() + "</green>):");
                sender.sendMessage(plugin.mm().deserialize("<white>" + escape(res.text()) + "</white>"));
                if (apply) {
                    String[] parts = res.text().split(" / ", -1);
                    for (int i = 0; i < 4; i++) {
                        String line = i < parts.length ? parts[i].trim() : "";
                        if (line.length() > 45) {
                            line = line.substring(0, 45);
                        }
                        sign.setLine(i, line);
                    }
                    sign.update();
                    plugin.send(sender, "<green>Sign updated.");
                } else if (p.hasPermission("minetranslate.apply")) {
                    plugin.send(sender, "<dark_gray>Tip: add <yellow>--apply</yellow><dark_gray> to write it onto the sign.");
                }
            });
        });
        return true;
    }

    private boolean book(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.send(sender, plugin.getConfig().getString("messages.players-only", "Players only."));
            return true;
        }
        if (args.length < 1) {
            plugin.send(sender, plugin.getConfig().getString("messages.usage-trbook", "/trbook <target> [--copy]"));
            return true;
        }
        boolean copy = Arrays.asList(args).contains("--copy");
        String target = Languages.normalize(args[0]);
        if (target == null) {
            plugin.send(sender, plugin.getConfig().getString("messages.unknown-language", "Unknown language.")
                    .replace("<input>", args[0]));
            return true;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if ((hand.getType() != Material.WRITTEN_BOOK && hand.getType() != Material.WRITABLE_BOOK)
                || !(hand.getItemMeta() instanceof BookMeta meta) || meta.getPageCount() == 0) {
            plugin.send(sender, "<red>Hold a written book in your main hand first.");
            return true;
        }
        int maxPages = plugin.getConfig().getInt("books.max-pages", 50);
        int pages = Math.min(meta.getPageCount(), maxPages);
        plugin.send(sender, "<gray>Translating " + pages + " page(s)…");
        final String fTarget = target;
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 1; i <= pages; i++) {
            final String pageText = PlainTextComponentSerializer.plainText().serialize(meta.page(i));
            if (pageText.isBlank()) {
                futures.add(CompletableFuture.completedFuture(""));
            } else {
                futures.add(plugin.service().translate(pageText, "auto", fTarget)
                        .thenApply(r -> r.text())
                        .exceptionally(e -> null));
            }
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((v, err) -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                List<String> translated = new ArrayList<>();
                for (CompletableFuture<String> f : futures) {
                    try {
                        String t = f.getNow(null);
                        translated.add(t == null ? "<failed>" : t);
                    } catch (Exception e) {
                        translated.add("<failed>");
                    }
                }
                plugin.send(sender, "<gray>Book translated to <green>" + fTarget + "</green>:</gray>");
                for (int i = 0; i < translated.size(); i++) {
                    sender.sendMessage(plugin.mm().deserialize(
                            "<dark_gray>--- Page " + (i + 1) + " ---</dark_gray>"));
                    sender.sendMessage(plugin.mm().deserialize("<white>" + escape(translated.get(i)) + "</white>"));
                }
                if (copy) {
                    if (!p.hasPermission("minetranslate.apply")) {
                        plugin.send(sender, plugin.getConfig().getString("messages.no-permission", "No permission."));
                        return;
                    }
                    ItemStack out = new ItemStack(Material.WRITTEN_BOOK);
                    BookMeta bm = (BookMeta) out.getItemMeta();
                    bm.setTitle((meta.hasTitle() ? meta.getTitle() + " " : "") + "[" + fTarget + "]");
                    bm.setAuthor(p.getName());
                    for (String t : translated) {
                        bm.addPage(t);
                    }
                    out.setItemMeta(bm);
                    p.getInventory().addItem(out);
                    plugin.send(sender, "<green>Translated copy added to your inventory.");
                } else if (p.hasPermission("minetranslate.apply")) {
                    plugin.send(sender, "<dark_gray>Tip: add <yellow>--copy</yellow><dark_gray> to get a translated copy.");
                }
            });
        });
        return true;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("<", "&lt;");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Languages.search(args[0]).stream().limit(20).collect(Collectors.toList());
        }
        if (args.length == 2) {
            String flag = cmd.getName().equalsIgnoreCase("trsign") ? "--apply" : "--copy";
            if (flag.startsWith(args[1])) {
                return List.of(flag);
            }
        }
        return List.of();
    }
}
