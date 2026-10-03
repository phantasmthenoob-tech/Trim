package com.unbound.inspect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Admin inspection: sneak + right-click a player to open a **mirror** of
 * their inventory as a 45-slot container:
 *
 * <pre>
 *  0 helmet | 1 chestplate | 2 leggings | 3 boots | 4 off-hand | 5 main-hand
 *  6-14  hotbar (slots 0-8 of the player inventory)
 *  15-44 storage (slots 9-35 of the player inventory)
 * </pre>
 *
 * <p>Vanilla {@code openInventory(PlayerInventory)} does not expose editable
 * armor/hand slots, so edits are mirrored back to the target's real slots on
 * every click, drag and on close (1 tick later, after the click settled).
 * Taking an item removes it from the target; putting one equips them.</p>
 *
 * <p>Sneak is required so normal right-clicks are never hijacked;
 * permission-gated by {@code unbound.inspect}.</p>
 */
public final class InspectHandler implements Listener {

    private static final long COOLDOWN_MS = 500L;
    private static final int HANDS_AND_ARMOR = 6;
    private static final int HOTBAR = 9;
    private static final int STORAGE = 27;

    /** Tags a mirror view with the inspected target. */
    private static final class InspectView implements InventoryHolder {
        final UUID targetId;
        final Inventory inventory;

        InspectView(UUID targetId, Inventory inventory) {
            this.targetId = targetId;
            this.inventory = inventory;
        }

        @Override
        public @Nullable Inventory getInventory() {
            return inventory;
        }
    }

    private final Plugin plugin;
    private final Map<UUID, InspectView> openViews = new HashMap<>();
    private final Map<UUID, Long> lastOpen = new HashMap<>();

    public InspectHandler(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInspect(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return; // one open per click (ignore the off-hand packet)
        }
        Player viewer = event.getPlayer();
        if (!viewer.isSneaking() || !viewer.hasPermission("unbound.inspect")) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target) || target == viewer) {
            return;
        }
        if (!InspectAccess.isAllowed(viewer)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(viewer.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            return;
        }
        lastOpen.put(viewer.getUniqueId(), now);
        openMirror(viewer, target);
        event.setCancelled(true);
    }

    /**
     * Opens the inspection mirror for an online target via command. Returns
     * null on success, "offline" when Paper's API cannot reach the target.
     */
    public @Nullable String openFor(Player viewer, Player target) {
        if (target == null || !target.isOnline()) {
            return "offline";
        }
        openMirror(viewer, target);
        return null;
    }

    private void openMirror(Player viewer, Player target) {
        PlayerInventory targetInv = target.getInventory();
        InspectView holder = new InspectView(target.getUniqueId(),
                Bukkit.createInventory(null, 45, target.getName() + "'s inventory"));
        Inventory view = holder.inventory;

        ItemStack[] armor = targetInv.getArmorContents(); // boots, legs, chest, helmet
        view.setItem(3, armor.length > 3 ? armor[3] : null); // helmet
        view.setItem(2, armor.length > 2 ? armor[2] : null); // chestplate
        view.setItem(1, armor.length > 1 ? armor[1] : null); // leggings
        view.setItem(0, armor.length > 0 ? armor[0] : null); // boots
        view.setItem(4, targetInv.getItemInOffHand());
        view.setItem(5, targetInv.getItemInMainHand());
        for (int i = 0; i < HOTBAR; i++) {
            view.setItem(HANDS_AND_ARMOR + i, targetInv.getItem(i));
        }
        for (int i = 0; i < STORAGE; i++) {
            view.setItem(HANDS_AND_ARMOR + HOTBAR + i, targetInv.getItem(HOTBAR + i));
        }

        openViews.put(viewer.getUniqueId(), holder);
        viewer.openInventory(view);
    }

    // ---- edit mirroring --------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InspectView view)) {
            return;
        }
        // Let the vanilla container behavior settle (pickup/place/shift/drag),
        // then mirror the whole view back to the target's real inventory.
        Bukkit.getScheduler().runTask(plugin, () -> syncToTarget(view));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InspectView view) {
            Bukkit.getScheduler().runTask(plugin, () -> syncToTarget(view));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof InspectView view) {
            openViews.remove(event.getPlayer().getUniqueId());
            syncToTarget(view);
        }
    }

    /** Writes the mirror's contents back to the target's live inventory. */
    private void syncToTarget(InspectView view) {
        Player target = Bukkit.getPlayer(view.targetId);
        if (target == null || !target.isOnline()) {
            return;
        }
        PlayerInventory targetInv = target.getInventory();
        // Armor raw slots: 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 off-hand.
        targetInv.setItem(39, view.inventory.getItem(3));
        targetInv.setItem(38, view.inventory.getItem(2));
        targetInv.setItem(37, view.inventory.getItem(1));
        targetInv.setItem(36, view.inventory.getItem(0));
        targetInv.setItem(40, view.inventory.getItem(4)); // off-hand
        targetInv.setItemInMainHand(view.inventory.getItem(5));
        for (int i = 0; i < HOTBAR; i++) {
            targetInv.setItem(i, view.inventory.getItem(HANDS_AND_ARMOR + i));
        }
        for (int i = 0; i < STORAGE; i++) {
            targetInv.setItem(HOTBAR + i, view.inventory.getItem(HANDS_AND_ARMOR + HOTBAR + i));
        }
        target.updateInventory();
    }
}
