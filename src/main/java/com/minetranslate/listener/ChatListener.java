package com.minetranslate.listener;

import com.minetranslate.MineTranslatePlugin;
import com.minetranslate.translate.TranslateResult;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-translates chat for players who enabled /trchat.
 * Default mode (per-viewer): subscribed players ONLY see the translated
 * message (with the original on hover). Everyone else sees the original.
 * Copy mode: everyone sees the original, subscribers get an extra line.
 */
public class ChatListener implements Listener {

    private final MineTranslatePlugin plugin;

    public ChatListener(MineTranslatePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (plain.isBlank()) {
            return;
        }
        int maxLen = plugin.getConfig().getInt("chat.max-length", 500);
        if (plain.length() > maxLen) {
            return;
        }
        for (String prefix : plugin.getConfig().getStringList("chat.ignore-prefixes")) {
            if (!prefix.isEmpty() && plain.startsWith(prefix)) {
                return;
            }
        }

        // Collect subscribed player viewers (exclude the sender - they see their own words).
        Map<String, List<Player>> byLang = new HashMap<>();
        List<Player> subscribed = new ArrayList<>();
        for (Audience a : event.viewers()) {
            if (a instanceof Player p) {
                if (p.getUniqueId().equals(event.getPlayer().getUniqueId())) {
                    continue;
                }
                if (!plugin.store().autoTranslate(p.getUniqueId())) {
                    continue;
                }
                String lang = plugin.store().language(p.getUniqueId());
                if (lang == null) {
                    lang = plugin.getConfig().getString("default-language", "en");
                }
                subscribed.add(p);
                byLang.computeIfAbsent(lang, k -> new ArrayList<>()).add(p);
            }
        }
        if (subscribed.isEmpty()) {
            return;
        }

        String mode = plugin.getConfig().getString("chat.mode", "per-viewer");
        if (mode.equalsIgnoreCase("copy")) {
            // Everyone keeps the original; subscribers get an extra translated line.
            for (Map.Entry<String, List<Player>> e : byLang.entrySet()) {
                final String lang = e.getKey();
                final List<Player> targets = e.getValue();
                plugin.service().translate(plain, "auto", lang).whenComplete((res, err) -> {
                    if (err != null || res == null) {
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> sendCopy(targets, event.getPlayer().getName(), plain, res));
                });
            }
            return;
        }

        // per-viewer: hide the original from subscribers, send them translations.
        try {
            event.viewers().removeAll(subscribed);
        } catch (UnsupportedOperationException ex) {
            // Fall through to copy mode if the viewer set is immutable on this build.
            for (Map.Entry<String, List<Player>> e : byLang.entrySet()) {
                final String lang = e.getKey();
                final List<Player> targets = e.getValue();
                plugin.service().translate(plain, "auto", lang).whenComplete((res, err) -> {
                    if (err != null || res == null) {
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> sendCopy(targets, event.getPlayer().getName(), plain, res));
                });
            }
            return;
        }
        String senderName = event.getPlayer().getName();
        for (Map.Entry<String, List<Player>> e : byLang.entrySet()) {
            final String lang = e.getKey();
            final List<Player> targets = e.getValue();
            plugin.service().translate(plain, "auto", lang).whenComplete((res, err) -> {
                if (err != null || res == null) {
                    // Translation failed: deliver the original so nothing is lost.
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Component fallback = Component.text("<" + senderName + "> " + plain);
                        for (Player p : targets) {
                            if (p.isOnline()) {
                                p.sendMessage(fallback);
                            }
                        }
                    });
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> sendTranslated(targets, senderName, plain, res));
            });
        }
    }

    private void sendTranslated(List<Player> targets, String senderName, String original, TranslateResult res) {
        Component msg = buildMessage(senderName, res);
        if (plugin.getConfig().getBoolean("chat.hover-original", true)) {
            msg = msg.hoverEvent(HoverEvent.showText(Component.text("Original: " + original)));
        }
        for (Player p : targets) {
            if (p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private void sendCopy(List<Player> targets, String senderName, String original, TranslateResult res) {
        Component msg = buildMessage(senderName, res);
        if (plugin.getConfig().getBoolean("chat.hover-original", true)) {
            msg = msg.hoverEvent(HoverEvent.showText(Component.text("Original: " + original)));
        }
        for (Player p : targets) {
            if (p.isOnline()) {
                p.sendMessage(msg);
            }
        }
    }

    private Component buildMessage(String senderName, TranslateResult res) {
        String prefix = plugin.getConfig().getString("chat.prefix", "[<source>><target>] ")
                .replace("<source>", res.sourceLang())
                .replace("<target>", res.targetLang());
        Component head = plugin.mm().deserialize(prefix);
        Component body = Component.text("<" + senderName + "> " + res.text());
        return head.append(body);
    }
}
