package com.minetranslate.gui;

import com.minetranslate.MineTranslatePlugin;
import com.minetranslate.translate.Languages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chest-menu GUI for MineTranslate.
 * Main menu: type text to translate, pick target language, toggle chat
 * auto-translation and pick its language — all without commands.
 */
public class TranslateGUI implements Listener {

    private final MineTranslatePlugin plugin;

    /** Player -> translate target chosen inside the GUI. */
    private final Map<UUID, String> sessionTarget = new ConcurrentHashMap<>();
    /** Player -> target waiting for chat text input. */
    private final Map<UUID, InputRequest> pendingInput = new ConcurrentHashMap<>();

    private record InputRequest(String target) {
    }

    /** What a language-browser click should set. */
    private enum Context {
        MY_LANG, TARGET, CHAT_LANG
    }

    private enum Menu {
        MAIN, BROWSER, RESULT
    }

    /** Holder attached to every GUI inventory so clicks can be routed. */
    private static final class GuiHolder implements InventoryHolder {
        final Menu menu;
        final Context context; // BROWSER only
        final int page; // BROWSER only
        final String target; // MAIN + RESULT
        final String original; // RESULT only
        final String translated; // RESULT only
        final String sourceLang; // RESULT only
        private Inventory inv;

        GuiHolder(Menu menu, Context context, int page, String target,
                String original, String translated, String sourceLang) {
            this.menu = menu;
            this.context = context;
            this.page = page;
            this.target = target;
            this.original = original;
            this.translated = translated;
            this.sourceLang = sourceLang;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public TranslateGUI(MineTranslatePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ open

    public void openMain(Player p) {
        String target = sessionTarget.computeIfAbsent(p.getUniqueId(), k -> defaultTarget(p));
        String myLang = langOf(p);
        boolean auto = plugin.store().autoTranslate(p.getUniqueId());

        GuiHolder holder = new GuiHolder(Menu.MAIN, null, 0, target, null, null, null);
        Inventory inv = Bukkit.createInventory(holder, 27,
                plugin.mm().deserialize("<dark_gray>MineTranslate"));
        holder.inv = inv;

        fill(inv, 27);
        inv.setItem(10, named(Material.WRITABLE_BOOK, "<green><bold>Translate text",
                "<gray>Translate into <aqua>" + target + "</aqua>",
                "<yellow>Click, then type the text in chat"));
        inv.setItem(11, named(Material.PAPER, "<aqua><bold>Target language",
                "<gray>Current: <aqua>" + target + "</aqua> (" + Languages.nameOf(target) + ")",
                "<yellow>Click to change"));
        inv.setItem(13, auto
                ? named(Material.LIME_DYE, "<green><bold>Auto-translate: ON",
                        "<gray>Chat is translated to <aqua>" + myLang + "</aqua>",
                        "<yellow>Click to turn OFF")
                : named(Material.GRAY_DYE, "<red><bold>Auto-translate: OFF",
                        "<gray>Turn on to translate chat",
                        "<gray>into <aqua>" + myLang + "</aqua>",
                        "<yellow>Click to turn ON"));
        inv.setItem(14, named(Material.COMPASS, "<gold><bold>My language",
                "<gray>Yours: <aqua>" + myLang + "</aqua> (" + Languages.nameOf(myLang) + ")",
                "<yellow>Click to change"));
        inv.setItem(15, named(Material.BOOK, "<light_purple><bold>Chat language",
                "<gray>Auto-translate target: <aqua>" + myLang + "</aqua>",
                "<yellow>Click to change"));
        inv.setItem(16, named(Material.ENDER_EYE, "<gray><bold>All languages",
                "<gray>Browse every supported language",
                "<yellow>Click to browse"));
        p.openInventory(inv);
    }

    public void openBrowser(Player p, Context context, int page) {
        List<String> all = browserList();
        int perPage = 45;
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
        page = Math.max(0, Math.min(page, pages - 1));

        String title = switch (context) {
            case MY_LANG -> "My language";
            case TARGET -> "Translate target";
            case CHAT_LANG -> "Chat language";
        };
        GuiHolder holder = new GuiHolder(Menu.BROWSER, context, page, null, null, null, null);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>" + title + " <gray>(<aqua>" + (page + 1) + "</aqua>/<aqua>"
                        + pages + "</aqua>)"));
        holder.inv = inv;

        int from = page * perPage;
        for (int i = 0; i < perPage && from + i < all.size(); i++) {
            String code = all.get(from + i);
            inv.setItem(i, named(Material.PAPER, "<aqua>" + Languages.nameOf(code) + " <gray>(" + code + ")",
                    "<yellow>Click to select"));
        }
        // Nav row.
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler());
        }
        inv.setItem(45, named(Material.ARROW, "<red>Back", "<gray>Back to menu"));
        if (page > 0) {
            inv.setItem(48, named(Material.ARROW, "<yellow>Previous page", "<gray>Page " + page));
        }
        if (page < pages - 1) {
            inv.setItem(50, named(Material.ARROW, "<yellow>Next page", "<gray>Page " + (page + 2)));
        }
        p.openInventory(inv);
    }

    public void openResult(Player p, String target, String original, String translated, String sourceLang) {
        GuiHolder holder = new GuiHolder(Menu.RESULT, null, 0, target, original, translated, sourceLang);
        Inventory inv = Bukkit.createInventory(holder, 27,
                plugin.mm().deserialize("<dark_gray>Translated"));
        holder.inv = inv;

        fill(inv, 27);
        inv.setItem(11, named(Material.BOOK, "<gray><bold>Original <gray>(" + sourceLang + ")",
                splitLore(original, 40)));
        inv.setItem(13, named(Material.WRITABLE_BOOK, "<green><bold>Translated <gray>(" + target + ")",
                splitLore(translated, 40)));
        inv.setItem(15, named(Material.PAPER, "<yellow><bold>Send to chat",
                "<gray>Post the translation in chat",
                "<yellow>Click to send"));
        inv.setItem(22, named(Material.ARROW, "<red>Back", "<gray>Back to menu"));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------- text input

    private void requestTextInput(Player p, String target) {
        p.closeInventory();
        pendingInput.put(p.getUniqueId(), new InputRequest(target));
        plugin.send(p, "<yellow>Type the text to translate into <aqua>" + target
                + "</aqua><yellow> in chat, or type <red>cancel</red><yellow>.");
        // Expire the request after 60 seconds.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingInput.remove(p.getUniqueId()) != null && p.isOnline()) {
                plugin.send(p, "<gray>Translation input expired.");
            }
        }, 20L * 60);
    }

    /** Intercepts chat from players typing translation text. Returns true if consumed. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(AsyncChatEvent event) {
        Player p = event.getPlayer();
        InputRequest req = pendingInput.remove(p.getUniqueId());
        if (req == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (text.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.send(p, "<gray>Cancelled.");
                openMain(p);
            });
            return;
        }
        if (text.isBlank()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.send(p, "<red>Empty text - try again or type cancel.");
                pendingInput.put(p.getUniqueId(), req);
            });
            return;
        }
        plugin.service().translate(text, "auto", req.target()).whenComplete((res, err) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                if (err != null || res == null) {
                    plugin.send(p, "<red>Translation failed. Check console / internet.");
                    openMain(p);
                    return;
                }
                openResult(p, req.target(), text, res.text(), res.sourceLang());
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pendingInput.remove(id);
    }

    // ----------------------------------------------------------------- clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        // Only handle clicks inside the GUI itself.
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        int slot = event.getSlot();
        switch (holder.menu) {
            case MAIN -> {
                String target = holder.target;
                switch (slot) {
                    case 10 -> requestTextInput(p, target);
                    case 11 -> openBrowser(p, Context.TARGET, pageOf(target));
                    case 13 -> {
                        boolean now = !plugin.store().autoTranslate(p.getUniqueId());
                        plugin.store().setAuto(p.getUniqueId(), now);
                        plugin.send(p, now ? "<green>Chat auto-translation <green>ON</green><gray>."
                                : "<yellow>Chat auto-translation <red>OFF</red><yellow>.");
                        openMain(p);
                    }
                    case 14 -> openBrowser(p, Context.MY_LANG, pageOf(langOf(p)));
                    case 15 -> openBrowser(p, Context.CHAT_LANG, pageOf(langOf(p)));
                    case 16 -> openBrowser(p, Context.MY_LANG, 0);
                    default -> {
                    }
                }
            }
            case BROWSER -> {
                if (slot == 45) {
                    openMain(p);
                } else if (slot == 48) {
                    openBrowser(p, holder.context, holder.page - 1);
                } else if (slot == 50) {
                    openBrowser(p, holder.context, holder.page + 1);
                } else if (slot < 45 && event.getCurrentItem() != null
                        && event.getCurrentItem().getType() == Material.PAPER) {
                    List<String> all = browserList();
                    int idx = holder.page * 45 + slot;
                    if (idx >= 0 && idx < all.size()) {
                        selectLanguage(p, holder.context, all.get(idx));
                    }
                }
            }
            case RESULT -> {
                if (slot == 15) {
                    p.closeInventory();
                    p.sendMessage(plugin.mm().deserialize("<gray>[" + holder.sourceLang + "➜"
                            + holder.target + "] <white>" + escape(holder.translated)));
                } else if (slot == 22) {
                    openMain(p);
                }
            }
        }
    }

    private void selectLanguage(Player p, Context context, String code) {
        switch (context) {
            case MY_LANG -> {
                plugin.store().setLanguage(p.getUniqueId(), code);
                plugin.send(p, "<green>Your language set to <aqua>" + code + "</aqua> ("
                        + Languages.nameOf(code) + ").");
                openMain(p);
            }
            case CHAT_LANG -> {
                plugin.store().setLanguage(p.getUniqueId(), code);
                plugin.store().setAuto(p.getUniqueId(), true);
                plugin.send(p, "<green>Chat auto-translation <green>ON</green><gray> → <aqua>"
                        + code + "</aqua> (" + Languages.nameOf(code) + ").");
                openMain(p);
            }
            case TARGET -> {
                sessionTarget.put(p.getUniqueId(), code);
                plugin.send(p, "<green>Translate target: <aqua>" + code + "</aqua> ("
                        + Languages.nameOf(code) + "). Now type your text in chat, or <red>cancel</red>.");
                // Jump straight into text input so selecting a target translates immediately.
                requestTextInput(p, code);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private String langOf(Player p) {
        String lang = plugin.store().language(p.getUniqueId());
        return lang != null ? lang : plugin.getConfig().getString("default-language", "en");
    }

    private String defaultTarget(Player p) {
        return langOf(p);
    }

    /** Canonical ordered language list for the browser (zh aliases hidden). */
    static List<String> browserList() {
        List<String> out = new ArrayList<>();
        for (String code : Languages.all().keySet()) {
            if (code.equals("zhcn") || code.equals("zhtw")) {
                continue;
            }
            if (code.equals("iw")) {
                continue; // superseded by "he"
            }
            out.add(Languages.canonical(code));
        }
        return out;
    }

    /** Page containing the given code, so the browser opens near it. */
    static int pageOf(String code) {
        int idx = browserList().indexOf(code);
        return idx < 0 ? 0 : idx / 45;
    }

    private ItemStack named(Material mat, String nameMini, String... loreMini) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.mm().deserialize(nameMini));
        List<Component> lore = new ArrayList<>();
        for (String line : loreMini) {
            for (String part : wrap(line, 40)) {
                lore.add(plugin.mm().deserialize("<gray>" + part));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack named(Material mat, String nameMini, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.mm().deserialize(nameMini));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(plugin.mm().deserialize("<white>" + line.replace("<", "&lt;")));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory inv, int size) {
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
    }

    private static String[] splitLore(String text, int width) {
        return wrap(text, width).toArray(new String[0]);
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add(" ");
            return out;
        }
        String clean = text.replace("<", "&lt;");
        if (clean.length() <= width) {
            out.add(clean);
            return out;
        }
        int start = 0;
        while (start < clean.length() && out.size() < 8) {
            int end = Math.min(start + width, clean.length());
            if (end < clean.length()) {
                int space = clean.lastIndexOf(' ', end);
                if (space > start + width / 2) {
                    end = space;
                }
            }
            out.add(clean.substring(start, end));
            start = end;
            while (start < clean.length() && clean.charAt(start) == ' ') {
                start++;
            }
        }
        if (start < clean.length()) {
            out.add("…");
        }
        return out;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("<", "&lt;");
    }
}
