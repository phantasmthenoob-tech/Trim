package com.entitystudio.mobform;

import com.entitystudio.EntityStudioPlugin;
import com.entitystudio.permission.StudioPermissions;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * "Become the mob" picker: every hand-written form as its vanilla spawn egg
 * (wither and dragon get iconic fallbacks — they have no eggs), plus an
 * unmorph button. Clicking re-checks the morph permission and morphs
 * directly; no separate entity selection needed.
 */
public class MorphMenu implements Listener {

    static final class MorphHolder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final int UNMORPH_SLOT = 47;
    private static final int CLOSE_SLOT = 49;

    private final EntityStudioPlugin plugin;
    private final MobFormService service;
    private final NamespacedKey tag;

    public MorphMenu(EntityStudioPlugin plugin, MobFormService service) {
        this.plugin = plugin;
        this.service = service;
        this.tag = new NamespacedKey(plugin, "morph_menu_type");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        if (!plugin.canUse(player) || !plugin.permitted(player, StudioPermissions.MORPH)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        MorphHolder holder = new MorphHolder();
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<gold><bold>Become the mob"));
        holder.inv = inv;
        List<String> types = FormStats.detailedTypes();
        int slot = 0;
        for (String type : types) {
            if (slot >= 45) {
                break;
            }
            inv.setItem(slot++, button(type));
        }
        inv.setItem(UNMORPH_SLOT, named(Material.BARRIER, "<red>Return to yourself",
                "<gray>End your current form", "<yellow>Click to unmorph"));
        inv.setItem(CLOSE_SLOT, named(Material.OAK_DOOR, "<gray>Close",
                "<yellow>Click to close"));
        for (int i = 45; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
        player.openInventory(inv);
    }

    /** Spawn-egg icon for a form name, with iconic fallbacks. */
    static Material iconFor(String typeName) {
        return switch (typeName) {
            case "WITHER" -> Material.NETHER_STAR;
            case "ENDER_DRAGON" -> Material.DRAGON_EGG;
            default -> {
                Material m = Material.matchMaterial(typeName + "_SPAWN_EGG");
                yield m != null ? m : Material.EGG;
            }
        };
    }

    private ItemStack button(String typeName) {
        FormStats.Stats s = FormStats.forType(typeName);
        String pretty = typeName.toLowerCase().replace('_', ' ');
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + s.primaryDesc());
        if (s.secondaryDesc() != null) {
            lore.add("<gray>" + s.secondaryDesc());
        }
        lore.add("<dark_gray>shift-right-click a mob, or");
        lore.add("<yellow>Click to become " + pretty + "</yellow>");
        ItemStack stack = named(iconFor(typeName),
                "<aqua><bold>" + pretty, lore.toArray(new String[0]));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(tag, PersistentDataType.STRING, typeName);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack named(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.mm().deserialize(name));
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(plugin.mm().deserialize(line));
            }
            meta.lore(lines);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack filler() {
        ItemStack stack = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.mm().deserialize("<dark_gray>"));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MorphHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || event.getClickedInventory().getHolder() instanceof MorphHolder == false) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == UNMORPH_SLOT) {
            if (!plugin.permitted(player, StudioPermissions.MORPH)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            player.closeInventory();
            if (!service.unmorph(player)) {
                plugin.send(player, "<gray>You are not morphed.");
            }
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        String typeName = meta == null ? null
                : meta.getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        if (typeName == null) {
            return;
        }
        if (!plugin.canUse(player) || !plugin.permitted(player, StudioPermissions.MORPH)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }
        player.closeInventory();
        service.morph(player, type);
    }
}
