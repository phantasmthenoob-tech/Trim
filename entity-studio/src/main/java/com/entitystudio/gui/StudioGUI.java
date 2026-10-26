package com.entitystudio.gui;

import com.entitystudio.EntityStudioPlugin;
import com.entitystudio.behavior.ManagedRecord;
import com.entitystudio.capability.Capability;
import com.entitystudio.capability.CapabilityRegistry;
import com.entitystudio.history.EditRecord;
import com.entitystudio.history.HistoryService;
import com.entitystudio.permission.StudioPermissions;
import com.entitystudio.property.EntityProperty;
import com.entitystudio.property.PropertyRegistry;
import com.entitystudio.session.EditorSession;
import com.entitystudio.session.SessionManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All editor GUIs. Display lives here; every mutation goes through the
 * property/history/permission pipeline and re-validates the session first.
 */
public class StudioGUI implements Listener {

    public enum Page {
        DASHBOARD, PROPERTIES, EQUIPMENT, ATTRIBUTES, EFFECTS, BEHAVIOR,
        TRADES, ITEM, HISTORY, SNAPSHOTS, PRESETS, CONFIRM, PLAYER_INV, CONTAINER,
        ADVANCED,
    }

    /** Chat-input kinds for typed value entry. */
    private enum InputKind {
        PROP_VALUE, SEARCH, ITEM_NAME, ITEM_LORE, ITEM_AMOUNT, ITEM_DAMAGE,
        ITEM_ENCHANT, ALLY_OWNER, TRUST_ADD, TRUST_DEL, ENEMY_ADD, ENEMY_DEL,
        PRESET_SAVE, SNAP_SAVE, EFFECT_ADD, ATTR_VALUE, TELEPORT, NAME_SET, DATA_SEARCH,
    }

    private record PendingInput(InputKind kind, UUID entityUuid, String data) {
    }

    /** Generic destructive-confirmation callback. */
    public interface ConfirmAction {
        void run(Player player, Entity entity);
    }

    /** Destructive confirmations carry their required node so execution time
     * re-checks it: a token created under one permission set must not fire
     * after revocation, target protection, or session changes. */
    private record ConfirmEntry(String title, String question, String permissionNode,
            ConfirmAction action) {
    }

    static final class GuiHolder implements InventoryHolder {
        final Page page;
        final UUID entityUuid;
        final String data; // property id / slot index / preset name / confirm token / filter
        final int pageNum;
        private Inventory inv;

        GuiHolder(Page page, UUID entityUuid, String data, int pageNum) {
            this.page = page;
            this.entityUuid = entityUuid;
            this.data = data;
            this.pageNum = pageNum;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final EntityStudioPlugin plugin;
    private final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();
    private final Map<String, ConfirmEntry> confirms = new ConcurrentHashMap<>();

    private static final int[] EQUIP_SLOTS = { 10, 11, 12, 13, 14, 15 };
    private static final String[] EQUIP_NAMES = { "Main hand", "Off hand", "Helmet",
            "Chestplate", "Leggings", "Boots" };

    public StudioGUI(EntityStudioPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------- entry data

    public void openInspectSummary(Player player, Entity target) {
        plugin.send(player, "<gray>Selected <aqua>" + target.getType().name().toLowerCase()
                + "</aqua> (<yellow>" + shortUuid(target.getUniqueId()) + "</yellow>) at "
                + loc(target) + ".");
    }

    /** Re-validates the session and returns the live entity, or null + feedback. */
    private Entity resolve(Player player, GuiHolder holder) {
        Entity entity = holder.entityUuid == null ? null : Bukkit.getEntity(holder.entityUuid);
        if (entity == null || !entity.isValid() || entity.isDead()) {
            plugin.sessions().close(player.getUniqueId());
            plugin.send(player, plugin.msg("session-invalid"));
            player.closeInventory();
            return null;
        }
        EditorSession session = plugin.sessions().get(player.getUniqueId());
        if (session == null || !session.entityUuid().equals(entity.getUniqueId())) {
            plugin.send(player, plugin.msg("session-invalid"));
            player.closeInventory();
            return null;
        }
        SessionManager.Validation v = plugin.sessions().validate(plugin, session);
        if (v != SessionManager.Validation.OK) {
            plugin.send(player, describeValidation(v, entity));
            player.closeInventory();
            return null;
        }
        return entity;
    }

    private String describeValidation(SessionManager.Validation v, Entity entity) {
        return switch (v) {
            case ENTITY_GONE -> plugin.msg("session-invalid");
            case NO_ACCESS -> plugin.msg("no-permission");
            case CONCURRENT_EDITOR -> plugin.msg("concurrent-editor", "editor", "?");
            case PROTECTED -> plugin.msg("entity-protected");
            default -> plugin.msg("session-invalid");
        };
    }

    // --------------------------------------------------------------- dashboard

    public void openDashboard(Player player, Entity entity) {
        if (!plugin.checkTargetAccess(player, entity)) {
            return;
        }
        plugin.sessions().open(player, entity);
        plugin.sessions().setPage(player.getUniqueId(), "dashboard");
        Set<Capability> caps = CapabilityRegistry.detect(entity);
        GuiHolder holder = new GuiHolder(Page.DASHBOARD, entity.getUniqueId(), null, 0);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Entity Studio"));
        holder.inv = inv;

        inv.setItem(4, summaryItem(entity, caps));
        int col = 10;
        col = dashButton(inv, col, caps.contains(Capability.EQUIPMENT), "EQUIPMENT",
                Material.IRON_CHESTPLATE, "<aqua><bold>Equipment",
                "<gray>Armor, hands, drop chances");
        // Every entity has editable storage now: native inventory for holders,
        // persistent plugin stash for the rest — so this is always available.
        col = dashButton(inv, col, true, "PLAYER_INV",
                Material.CHEST, "<aqua><bold>Inventory",
                playerInvHint(entity));
        col = dashButton(inv, col, true, "PROPERTIES", Material.BOOK,
                "<green><bold>Properties", "<gray>Typed data editor + search");
        col = dashButton(inv, col, caps.contains(Capability.ATTRIBUTES), "ATTRIBUTES",
                Material.GOLDEN_APPLE, "<green><bold>Attributes",
                "<gray>Base values, modifiers");
        col = dashButton(inv, col, caps.contains(Capability.EFFECTS), "EFFECTS",
                Material.POTION, "<green><bold>Effects", "<gray>Potion effects");
        col = dashButton(inv, col, entity instanceof Mob || entity instanceof LivingEntity,
                "BEHAVIOR", Material.LEAD, "<gold><bold>Behavior",
                "<gray>AI, profiles, anchors, allegiance");
        col = dashButton(inv, col, caps.contains(Capability.MERCHANT_OFFERS), "TRADES",
                Material.EMERALD, "<green><bold>Trades", "<gray>Offers, profession, level");
        col = dashButton(inv, col, caps.contains(Capability.ADVANCED_DATA), "ADVANCED",
                Material.COMMAND_BLOCK, "<aqua><bold>Advanced data",
                "<gray>Read-only PDC + data view");
        for (String warn : warnings(entity, caps)) {
            if (col > 25) {
                break;
            }
            inv.setItem(col++, named(Material.REDSTONE_TORCH, "<red><bold>Warning",
                    "<yellow>" + warn));
        }
        inv.setItem(45, named(Material.COMPASS, "<gray>Search properties",
                "<gray>Filter the data editor by name", "<yellow>Click, then type"));
        inv.setItem(46, toggleItem(entity.isCustomNameVisible(), "Nameplate visible",
                "Show the custom name above the entity"));
        String dashName = entity.getCustomName();
        inv.setItem(47, named(Material.NAME_TAG,
                "<white>Rename: <aqua>" + escape(trunc(
                        dashName == null || dashName.isBlank() ? "—" : dashName, 20)),
                "<yellow>Click to type a new name", "<gray>Right-click to clear"));
        inv.setItem(48, named(Material.CLOCK, "<aqua>History & undo",
                "<gray>" + plugin.history().size(player.getUniqueId()) + " recorded edits",
                "<gray>Current session: " + shortUuid(entity.getUniqueId()),
                "<yellow>Click to review"));
        inv.setItem(49, named(Material.ENDER_CHEST, "<light_purple>Snapshots",
                "<yellow>Click to manage"));
        inv.setItem(50, named(Material.ENDER_EYE, "<light_purple>Presets",
                "<yellow>Click to manage"));
        inv.setItem(51, helpItem("Shift-right-click selects. Every edit re-checks",
                "permissions + the live entity first."));
        inv.setItem(52, navRefresh());
        inv.setItem(44, navClose());
        inv.setItem(53, named(Material.BARRIER, "<red>Remove entity",
                "<gray>Deletes this entity (needs confirm).",
                "<red>Cannot be undone!", "<yellow>Shift-click to confirm"));
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
        player.openInventory(inv);
    }

    private int dashButton(Inventory inv, int slot, boolean enabled, String target,
            Material mat, String name, String... lore) {
        if (slot > 16) {
            return slot;
        }
        if (enabled) {
            List<String> lines = new ArrayList<>(List.of(lore));
            lines.add("<dark_gray>→ " + target);
            inv.setItem(slot, named(mat, name, lines.toArray(new String[0])));
        } else {
            inv.setItem(slot, named(Material.GRAY_DYE, "<dark_gray>" + stripTags(name),
                    "<dark_gray>Not supported by this entity"));
        }
        return slot + 1;
    }

    private String playerInvHint(Entity entity) {
        if (entity instanceof Player) {
            return "Inventory + ender chest (live, online only)";
        }
        if (entity instanceof org.bukkit.entity.AbstractHorse) {
            return "Saddle, armor, chest storage (live)";
        }
        if (entity instanceof InventoryHolder) {
            return "Container storage (live)";
        }
        return "Entity stash: 27 persistent slots";
    }

    private ItemStack summaryItem(Entity entity, Set<Capability> caps) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Type: <aqua>" + entity.getType().name().toLowerCase());
        lore.add("<gray>UUID: <yellow>" + shortUuid(entity.getUniqueId()));
        lore.add("<gray>EID: <white>#" + entity.getEntityId());
        String cn = entity.getCustomName();
        lore.add("<gray>Name: <white>"
                + escape(trunc(cn == null || cn.isBlank() ? "—" : cn, 28))
                + (entity.isCustomNameVisible() ? " <green>(shown)" : " <gray>(hidden)"));
        lore.add("<gray>At: <white>" + loc(entity));
        if (entity instanceof LivingEntity l) {
            lore.add("<gray>Health: <red>" + round1(l.getHealth())
                    + "</red> / <red>" + round1(l.getMaxHealth()));
            lore.add("<gray>AI: <white>" + (l.hasAI() ? "on" : "OFF"));
        }
        if (entity instanceof Mob m) {
            lore.add("<gray>Target: <white>" + (m.getTarget() == null ? "none"
                    : m.getTarget().getType().name().toLowerCase()));
        }
        lore.add("<gray>Caps: <white>" + caps.size() + " supported");
        return named(Material.NAME_TAG, "<bold>" + displayName(entity), lore.toArray(new String[0]));
    }

    private List<String> warnings(Entity entity, Set<Capability> caps) {
        List<String> out = new ArrayList<>();
        if (entity instanceof LivingEntity l && l.getHealth() <= 0) {
            out.add("Entity is at 0 health.");
        }
        if (entity instanceof LivingEntity l2 && !l2.hasAI()) {
            out.add("AI is disabled — behavior controls are limited.");
        }
        if (entity instanceof Player) {
            out.add("Player editing: offline changes are NOT supported.");
        }
        return out;
    }

    // -------------------------------------------------------------- properties

    public void openProperties(Player player, Entity entity, String filter) {
        openProperties(player, entity, filter, 0);
    }

    public void openProperties(Player player, Entity entity, String filter, int page) {
        if (!plugin.permitted(player, StudioPermissions.DATA_VIEW)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "properties");
        List<EntityProperty<?>> all = filter == null || filter.isBlank()
                ? plugin.properties().listFor(entity)
                : plugin.properties().searchFor(entity, filter);
        int pages = Math.max(1, (int) Math.ceil(Math.max(1, all.size()) / 45.0));
        page = ContainerPages.clampPage(page, Math.max(1, all.size()));
        GuiHolder holder = new GuiHolder(Page.PROPERTIES, entity.getUniqueId(), filter, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Properties"
                        + (filter == null || filter.isBlank() ? "" : " <gray>" + escape(trunc(filter, 12)))
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        int start = ContainerPages.startIndex(page, Math.max(1, all.size()));
        for (int i = 0; i < 45 && start + i < all.size(); i++) {
            inv.setItem(i, propertyItem(entity, all.get(start + i)));
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler());
        }
        inv.setItem(GuiNav.BACK_54, navBack());
        inv.setItem(GuiNav.HELP_54, helpItem("Gold = editable now.",
                "Gray = read-only, red = unsupported.", "Click a gold row to type a value."));
        if (ContainerPages.hasPrev(page, Math.max(1, all.size()))) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        inv.setItem(GuiNav.ACTION_54, named(Material.COMPASS,
                filter == null || filter.isBlank() ? "<gray>Search" : "<aqua>Filter: " + escape(trunc(filter, 16)),
                "<yellow>Click to type a filter", filter == null || filter.isBlank() ? "" : "<gray>Right-click to clear"));
        if (ContainerPages.hasNext(page, Math.max(1, all.size()))) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        player.openInventory(inv);
    }

    private ItemStack propertyItem(Entity entity, EntityProperty<?> prop) {
        Object current;
        try {
            current = prop.read(entity);
        } catch (Exception e) {
            current = "<unreadable>";
        }
        Material icon = switch (prop.type()) {
            case BOOLEAN -> Boolean.TRUE.equals(current) ? Material.LIME_DYE : Material.GRAY_DYE;
            case NUMBER -> Material.GOLD_NUGGET;
            case TEXT -> Material.PAPER;
            case ENUM -> Material.BOOK;
            case ENTITY_REF -> Material.LEAD;
        };
        EntityProperty.Support support = prop.support(entity);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + prop.description());
        lore.add("<gray>Value: <aqua>" + escape(String.valueOf(current)));
        lore.add("<gray>Type: <white>" + prop.type().name().toLowerCase()
                + "  <gray>Risk: " + riskColor(prop.risk()) + prop.risk().name().toLowerCase());
        lore.add(prop.reversible() ? "<green>Reversible" : "<red>Not reversible");
        lore.add("<dark_gray>Scope: " + escape(prop.scope()) + " · " + supportBadge(support));
        lore.add(supportHint(prop, support));
        return named(icon, "<white>" + prop.displayName() + " <dark_gray>(" + prop.id() + ")",
                lore.toArray(new String[0]));
    }

    private static String supportBadge(EntityProperty.Support support) {
        return switch (support) {
            case SUPPORTED -> "<green>Supported";
            case PARTIAL -> "<yellow>Partially supported";
            case UNAVAILABLE -> "<red>Temporarily unavailable";
            case READ_ONLY -> "<gray>Read-only";
            case SPECIAL -> "<aqua>Special handling";
            case UNSUPPORTED -> "<dark_gray>Unsupported";
        };
    }

    private static String supportHint(EntityProperty<?> prop,
            EntityProperty.Support support) {
        if (support.writable()) {
            return prop.type() == EntityProperty.PropertyType.BOOLEAN
                    ? "<yellow>Click to toggle"
                    : "<yellow>Click to type a new value";
        }
        return switch (support) {
            case READ_ONLY -> "<gray>View only — no setter exists";
            case UNAVAILABLE -> "<gray>Not available for this entity right now";
            case SPECIAL -> "<aqua>Click: opens its dedicated editor";
            default -> "<dark_gray>Not editable here";
        };
    }

    private String riskColor(EntityProperty.RiskLevel risk) {
        return switch (risk) {
            case NORMAL -> "<green>";
            case ADVANCED -> "<yellow>";
            case DESTRUCTIVE -> "<red>";
        };
    }

    // --------------------------------------------------------------- equipment

    public void openEquipment(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.getEquipment() == null) {
            plugin.send(player, "<red>This entity has no equipment.");
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.INV_VIEW)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "equipment");
        GuiHolder holder = new GuiHolder(Page.EQUIPMENT, entity.getUniqueId(), null, 0);
        Inventory inv = Bukkit.createInventory(holder, 27,
                plugin.mm().deserialize("<dark_gray>Equipment"));
        holder.inv = inv;
        EntityEquipment eq = living.getEquipment();
        ItemStack[] slots = { eq.getItemInMainHand(), eq.getItemInOffHand(), eq.getHelmet(),
                eq.getChestplate(), eq.getLeggings(), eq.getBoots() };
        for (int i = 0; i < 6; i++) {
            inv.setItem(EQUIP_SLOTS[i], named(Material.PAPER, "<aqua>" + EQUIP_NAMES[i],
                    "<gray>Left-click: swap with cursor",
                    "<gray>Right-click: item editor"));
            inv.setItem(EQUIP_SLOTS[i] + 9, slots[i] == null ? emptySlot(EQUIP_NAMES[i])
                    : slots[i].clone());
        }
        boolean mob = entity instanceof Mob;
        inv.setItem(0, named(Material.ANVIL, "<yellow>Drop chances" + (mob ? "" : " (mobs only)"),
                mob ? "<gray>Click a label slot below to cycle +25%" : "<gray>Loot drop chances apply to mobs"));
        inv.setItem(1, helpItem("Left-click a shown item: swap with cursor.",
                "Right-click: open the item editor."));
        inv.setItem(7, navRefresh());
        inv.setItem(8, navBack());
        inv.setItem(25, navClose());
        inv.setItem(26, named(Material.BARRIER, "<red>Clear all equipment",
                "<red>Destructive — needs confirm", "<yellow>Shift-click to confirm"));
        player.openInventory(inv);
    }

    private ItemStack emptySlot(String name) {
        return named(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Empty " + name,
                "<gray>Place an item with left-click");
    }

    // -------------------------------------------------------------- attributes

    public void openAttributes(Player player, Entity entity) {
        openAttributes(player, entity, 0);
    }

    public void openAttributes(Player player, Entity entity, int page) {
        if (!(entity instanceof Attributable attr)) {
            plugin.send(player, "<red>This entity has no attributes.");
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.DATA_VIEW)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "attributes");
        List<AttributeInstance> list = new ArrayList<>();
        for (Attribute key : org.bukkit.Registry.ATTRIBUTE) {
            AttributeInstance inst = attr.getAttribute(key);
            if (inst != null) {
                list.add(inst);
            }
        }
        int pages = ContainerPages.pageCount(Math.max(1, list.size()));
        page = ContainerPages.clampPage(page, Math.max(1, list.size()));
        GuiHolder holder = new GuiHolder(Page.ATTRIBUTES, entity.getUniqueId(), null, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Attributes"
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        int start = ContainerPages.startIndex(page, Math.max(1, list.size()));
        for (int i = 0; i < 45 && start + i < list.size(); i++) {
            AttributeInstance inst = list.get(start + i);
            String key = inst.getAttribute().getKey().getKey();
            inv.setItem(i, named(Material.GOLD_NUGGET, "<white>" + key,
                    "<gray>Base: <aqua>" + round3(inst.getBaseValue()),
                    "<gray>Final: <aqua>" + round3(inst.getValue()),
                    "<gray>Default: <white>" + round3(inst.getDefaultValue()),
                    "<gray>Modifiers: <white>" + inst.getModifiers().size(),
                    "<yellow>Click to set base value",
                    "<gray>Right-click to reset to default"));
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler());
        }
        inv.setItem(GuiNav.BACK_54, navBack());
        inv.setItem(GuiNav.HELP_54, helpItem("Click a row to type a new base value.",
                "Right-click resets to the vanilla default."));
        if (ContainerPages.hasPrev(page, Math.max(1, list.size()))) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        if (ContainerPages.hasNext(page, Math.max(1, list.size()))) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        player.openInventory(inv);
    }

    // ---------------------------------------------------------------- effects

    public void openEffects(Player player, Entity entity) {
        openEffects(player, entity, 0);
    }

    public void openEffects(Player player, Entity entity, int page) {
        if (!(entity instanceof LivingEntity living)) {
            plugin.send(player, "<red>Only living entities have effects.");
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.DATA_VIEW)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "effects");
        List<org.bukkit.potion.PotionEffect> active = new ArrayList<>(living.getActivePotionEffects());
        // Slot 44 is the Add button, so 44 content rows per page.
        int perPage = 44;
        int pages = Math.max(1, (Math.max(1, active.size() + 1) + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        GuiHolder holder = new GuiHolder(Page.EFFECTS, entity.getUniqueId(), null, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Effects (" + active.size() + ")"
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        int start = page * perPage;
        int i = 0;
        for (int k = start; k < active.size() && i < perPage; k++, i++) {
            var fx = active.get(k);
            String key = fx.getType().getKey().getKey();
            inv.setItem(i, named(Material.POTION, "<aqua>" + key,
                    "<gray>Duration: <white>" + fx.getDuration() + " ticks",
                    "<gray>Amplifier: <white>" + fx.getAmplifier(),
                    "<gray>Current value shown above",
                    "<red>Click to remove (needs confirm)"));
        }
        if (start + perPage >= active.size()) {
            int addSlot = Math.min(44, active.size() - start);
            inv.setItem(addSlot, named(Material.BREWING_STAND, "<green>Add effect",
                    "<gray>Format: TYPE:DURATION_TICKS:AMPLIFIER",
                    "<gray>e.g. SPEED:1200:1", "<yellow>Click to type"));
        }
        for (int j = 45; j < 54; j++) {
            inv.setItem(j, filler());
        }
        inv.setItem(GuiNav.BACK_54, navBack());
        inv.setItem(GuiNav.HELP_54, helpItem("Add uses TYPE:DURATION:AMPLIFIER.",
                "Removing or clearing needs the confirm screen."));
        if (page > 0) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        if (page < pages - 1) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(53, named(Material.BARRIER, "<red>Clear all effects",
                "<red>Destructive — needs confirm", "<yellow>Shift-click to confirm"));
        player.openInventory(inv);
    }

    // ---------------------------------------------------------- player mount container

    public void openPlayerInv(Player player, Entity entity) {
        if (entity instanceof Player target) {
            if (!plugin.permitted(player, StudioPermissions.INV_VIEW)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "player_inv");
            GuiHolder holder = new GuiHolder(Page.PLAYER_INV, entity.getUniqueId(), null, 0);
            Inventory inv = Bukkit.createInventory(holder, 27,
                    plugin.mm().deserialize("<dark_gray>Player inventory"));
            holder.inv = inv;
            fill(inv, 27);
            inv.setItem(0, helpItem("Opens the LIVE player inventory.",
                    "Offline editing is not supported."));
            inv.setItem(8, navRefresh());
            inv.setItem(11, named(Material.CHEST, "<aqua>Open inventory (live)",
                    "<gray>Edits apply to the real inventory", "<green>Click to open"));
            inv.setItem(13, named(Material.ENDER_CHEST, "<light_purple>Open ender chest (live)",
                    "<green>Click to open"));
            inv.setItem(15, named(Material.BOOK, "<gray>About offline players",
                    "<gray>Offline editing needs a dedicated",
                    "<gray>persistence strategy — not supported",
                    "<gray>in this version."));
            inv.setItem(22, navBack());
            inv.setItem(26, navClose());
            player.openInventory(inv);
            return;
        }
        // Everything else opens through the size-safe mirror: native storage
        // for holders, or the persistent plugin stash for entities (cows,
        // pigs, ...) that have no inventory of their own.
        openContainerMirror(player, entity);
    }

    /** Editable backing store: the entity's own inventory for holders, the
     * persistent plugin stash for everyone else. Null only when a native
     * inventory is unreadable. */
    private Inventory backingInventory(Entity entity) {
        if (entity instanceof InventoryHolder holderEnt) {
            try {
                return holderEnt.getInventory();
            } catch (Exception e) {
                plugin.getLogger().warning("Could not read inventory of "
                        + entity.getType().name() + " " + entity.getUniqueId() + ": " + e);
                return null;
            }
        }
        return plugin.stashes().stashFor(entity.getUniqueId());
    }

    // ------------------------------------------------------- container mirror

    /** Size-safe mirror of an entity's editable storage — native inventory
     * for holders, persistent plugin stash for everyone else. Click an item
     * to withdraw it into your inventory; shift-click items in your own
     * inventory to deposit them. Transfers write straight through with index
     * validation — never a detached copy. Any backing size works: content
     * beyond one page spills onto more pages instead of crashing the view,
     * and unused slots show placeholders. */
    public void openContainerMirror(Player player, Entity entity) {
        openContainerMirror(player, entity, 0);
    }

    public void openContainerMirror(Player player, Entity entity, int page) {
        if (!plugin.checkTargetAccess(player, entity)) {
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.INV_VIEW)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        Inventory real = backingInventory(entity);
        if (real == null) {
            plugin.send(player, "<red>Could not read that inventory.");
            return;
        }
        boolean stash = !(entity instanceof InventoryHolder);
        int pages = ContainerPages.pageCount(real.getSize());
        page = ContainerPages.clampPage(page, real.getSize());
        int start = ContainerPages.startIndex(page, real.getSize());
        plugin.sessions().setPage(player.getUniqueId(), "container");
        GuiHolder holder = new GuiHolder(Page.CONTAINER, entity.getUniqueId(),
                String.valueOf(page), 0);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>" + (stash ? "Stash: " : "Container: ")
                        + escape(trunc(entity.getType().name().toLowerCase(), 14))
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        for (int i = 0; i < ContainerPages.PAGE_CONTENT; i++) {
            int realIdx = start + i;
            if (realIdx >= real.getSize()) {
                break;
            }
            ItemStack s = real.getItem(realIdx);
            inv.setItem(i, isAir(s) ? null : s.clone());
        }
        inv.setItem(GuiNav.BACK_54, navBack());
        inv.setItem(46, named(Material.BOOK, stash ? "<aqua>Entity stash"
                : "<aqua>How this works",
                stash ? "<gray>Plugin storage for entities"
                        : "<gray>Click an item: withdraw to you",
                stash ? "<gray>with no inventory of their own"
                        : "<gray>Shift-click your items: deposit",
                stash ? "<gray>Persists across restarts"
                        : "<gray>Edits apply to the live entity"));
        if (ContainerPages.hasPrev(page, real.getSize())) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        inv.setItem(GuiNav.ACTION_54, named(Material.HOPPER, "<gold>Withdraw all",
                "<gray>Moves everything into your inventory",
                "<gray>Destructive — needs confirm", "<yellow>Click to confirm"));
        if (ContainerPages.hasNext(page, real.getSize())) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.HELP_54, helpItem(stash ? "Plugin stash for entities" : "Click an item: withdraw to you.",
                stash ? "with no inventory of their own." : "Shift-click your items: deposit."));
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
        if (stash) {
            plugin.stashes().saveOne(entity.getUniqueId());
        }
        player.openInventory(inv);
    }

    private void clickContainer(Player player, GuiHolder holder, InventoryClickEvent event) {
        Entity entity = resolve(player, holder);
        if (entity == null) {
            return;
        }
        Inventory real = backingInventory(entity);
        if (real == null) {
            plugin.send(player, "<red>Could not read that inventory.");
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        int page = ContainerPages.parsePage(holder.data);
        // Standard bar inside the mirror: Back / Refresh / Close / Help
        // behave like every other page; only content + withdraw-all mutate.
        if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
            int s = event.getSlot();
            if (s == GuiNav.BACK_54) {
                openDashboard(player, entity);
                return;
            }
            if (s == GuiNav.REFRESH_54) {
                openContainerMirror(player, entity, page);
                return;
            }
            if (s == GuiNav.CLOSE_54 || s == GuiNav.HELP_54) {
                if (s == GuiNav.CLOSE_54) {
                    player.closeInventory();
                } else {
                    plugin.send(player, "<aqua>Click an item: withdraw · Shift-click yours: deposit.");
                }
                return;
            }
        }
        boolean top = event.getClickedInventory().equals(event.getView().getTopInventory());
        switch (event.getClick()) {
            case LEFT, RIGHT -> {
                if (top) {
                    containerTopClick(player, entity, real, page, event.getSlot());
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if (top) {
                    containerTopClick(player, entity, real, page, event.getSlot());
                } else {
                    containerDeposit(player, entity, real, page, event);
                }
            }
            default -> {
                // number keys, double-click, drops: ignored (already cancelled)
            }
        }
    }

    private void containerTopClick(Player player, Entity entity, Inventory real, int page,
            int slot) {
        if (slot == 45) {
            openDashboard(player, entity);
            return;
        }
        if (slot == 48 && ContainerPages.hasPrev(page, real.getSize())) {
            openContainerMirror(player, entity, page - 1);
            return;
        }
        if (slot == 50 && ContainerPages.hasNext(page, real.getSize())) {
            openContainerMirror(player, entity, page + 1);
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.INV_EDIT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        if (slot == 49) {
            List<ItemStack> preview = new ArrayList<>();
            for (int i = 0; i < real.getSize(); i++) {
                ItemStack s = real.getItem(i);
                if (!isAir(s)) {
                    preview.add(s.clone());
                }
            }
            if (preview.isEmpty()) {
                plugin.send(player, "<gray>Container is empty.");
                return;
            }
            confirm(player, entity, "Withdraw all",
                    "Move " + preview.size() + " stack(s) into your inventory? Recorded for undo.",
                    StudioPermissions.INV_EDIT,
                    (p, e) -> containerWithdrawAll(p, e, backingInventory(e), page));
            return;
        }
        if (slot < 0 || slot >= ContainerPages.PAGE_CONTENT) {
            return;
        }
        containerWithdrawOne(player, entity, real, page,
                ContainerPages.startIndex(page, real.getSize()) + slot);
    }

    private void containerWithdrawOne(Player player, Entity entity, Inventory real, int page,
            int realSlot) {
        if (realSlot < 0 || realSlot >= real.getSize()) {
            return;
        }
        ItemStack inReal = real.getItem(realSlot);
        if (isAir(inReal)) {
            return;
        }
        ItemStack moving = inReal.clone();
        real.setItem(realSlot, null);
        ItemStack rest = player.getInventory().addItem(moving).values().stream()
                .findFirst().orElse(null);
        if (rest != null) {
            real.setItem(realSlot, rest); // your inventory was full: put it back
        }
        String what = moving.getType().name().toLowerCase() + " x" + moving.getAmount();
        recordSimple(player, entity, "Withdraw " + what + " (slot " + realSlot + ")",
                what, rest == null ? "to " + player.getName() : "partially moved",
                e2 -> {
                    Inventory inv = backingInventory(e2);
                    if (inv != null) {
                        inv.addItem(moving.clone());
                    }
                });
        plugin.send(player, "<green>Withdrew <white>" + what);
        openContainerMirror(player, entity, page);
    }

    private void containerWithdrawAll(Player player, Entity entity, Inventory real, int page) {
        List<ItemStack> taken = new ArrayList<>();
        for (int i = 0; i < real.getSize(); i++) {
            ItemStack s = real.getItem(i);
            if (!isAir(s)) {
                taken.add(s.clone());
                real.setItem(i, null);
            }
        }
        if (taken.isEmpty()) {
            plugin.send(player, "<gray>Container is empty.");
            return;
        }
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack s : taken) {
            overflow.addAll(player.getInventory().addItem(s).values());
        }
        for (ItemStack s : overflow) {
            real.addItem(s); // your inventory filled up: put the rest back
        }
        int moved = taken.size() - overflow.size();
        final List<ItemStack> snapshot = new ArrayList<>(taken);
        recordSimple(player, entity, "Withdraw all (container)", taken.size() + " stacks",
                moved + " moved", e2 -> {
                    Inventory inv = backingInventory(e2);
                    if (inv != null) {
                        for (ItemStack s : snapshot) {
                            for (ItemStack o : inv.addItem(s.clone()).values()) {
                                e2.getWorld().dropItemNaturally(e2.getLocation(), o);
                            }
                        }
                    }
                });
        plugin.send(player, "<green>Withdrew <white>" + moved + "<green> stack(s)"
                + (overflow.isEmpty() ? ""
                        : " (<yellow>" + overflow.size() + " did not fit — kept<green>)"));
        openContainerMirror(player, entity, page);
    }

    private void containerDeposit(Player player, Entity entity, Inventory real, int page,
            InventoryClickEvent event) {
        if (!plugin.permitted(player, StudioPermissions.INV_EDIT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        ItemStack offering = event.getCurrentItem();
        if (isAir(offering)) {
            return;
        }
        ItemStack moving = offering.clone();
        ItemStack left = real.addItem(moving).values().stream()
                .findFirst().orElse(null);
        event.getClickedInventory().setItem(event.getSlot(), left);
        String what = moving.getType().name().toLowerCase() + " x" + moving.getAmount();
        UUID actor = player.getUniqueId();
        recordSimple(player, entity, "Deposit " + what + " (container)",
                "held by " + player.getName(), left == null ? "stored" : "partially stored",
                e2 -> {
                    Inventory r2 = backingInventory(e2);
                    if (r2 != null) {
                        int need = moving.getAmount();
                        for (int i = 0; i < r2.getSize() && need > 0; i++) {
                            ItemStack s = r2.getItem(i);
                            if (s != null && s.isSimilar(moving)) {
                                int take = Math.min(need, s.getAmount());
                                if (s.getAmount() == take) {
                                    r2.setItem(i, null);
                                } else {
                                    s.setAmount(s.getAmount() - take);
                                }
                                need -= take;
                            }
                        }
                        int back = moving.getAmount() - need;
                        if (back > 0) {
                            ItemStack ret = moving.clone();
                            ret.setAmount(back);
                            Player p = Bukkit.getPlayer(actor);
                            if (p != null) {
                                p.getInventory().addItem(ret);
                            } else {
                                e2.getWorld().dropItemNaturally(e2.getLocation(), ret);
                            }
                        }
                    }
                });
        plugin.send(player, "<green>Deposited <white>" + what);
        openContainerMirror(player, entity, page);
    }

    // ---------------------------------------------------------------- behavior

    public void openBehavior(Player player, Entity entity) {
        if (!plugin.permitted(player, StudioPermissions.BEHAVIOR)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "behavior");
        ManagedRecord rec = plugin.allegiance().getOrCreate(entity);
        GuiHolder holder = new GuiHolder(Page.BEHAVIOR, entity.getUniqueId(), null, 0);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Behavior"));
        holder.inv = inv;
        boolean ai = entity instanceof LivingEntity l && l.hasAI();
        inv.setItem(10, toggleItem(ai, "AI enabled",
                "Off = stands still (gravity/knockback still apply)"));
        if (entity instanceof Mob m) {
            inv.setItem(11, toggleItem(m.isAware(), "Aware",
                    "Unaware mobs don't notice targets or paths"));
            inv.setItem(12, named(Material.LEAD, "<white>Target: <aqua>"
                    + (m.getTarget() == null ? "none" : m.getTarget().getType().name().toLowerCase()),
                    "<red>Click to clear target"));
        }
        // profiles
        ManagedRecord.Profile[] profiles = ManagedRecord.Profile.values();
        for (int i = 0; i < profiles.length && 19 + i < 28; i++) {
            ManagedRecord.Profile p = profiles[i];
            boolean active = rec.profile() == p;
            inv.setItem(19 + i, named(active ? Material.LIME_DYE : Material.GRAY_DYE,
                    (active ? "<green><bold>" : "<gray>") + p.name(),
                    "<gray>" + profileHint(p), active ? "<green>Active" : "<yellow>Click to apply"));
        }
        // allegiance toggles
        inv.setItem(28, toggleItem(rec.defendOwner(), "Defend owner",
                "Attack whoever attacks the owner"));
        inv.setItem(29, toggleItem(rec.defendAllies(), "Defend trusted",
                "Attack whoever attacks trusted players"));
        inv.setItem(30, toggleItem(rec.retaliate(), "Retaliate when attacked",
                "Strike back at attackers"));
        inv.setItem(31, toggleItem(rec.attackOwnerTarget(), "Attack owner's target",
                "Join in when the owner attacks"));
        inv.setItem(32, named(Material.NAME_TAG,
                "<white>Owner: <aqua>" + nameOf(rec.owner()),
                "<yellow>Click to set (name/UUID)", "<gray>Right-click to clear"));
        inv.setItem(33, named(Material.PLAYER_HEAD, "<white>Trusted: <aqua>" + rec.trusted().size(),
                "<yellow>Click to add", "<gray>Right-click to remove one"));
        inv.setItem(34, named(Material.SKELETON_SKULL, "<white>Enemies: <aqua>" + rec.enemies().size(),
                "<yellow>Click to add", "<gray>Right-click to remove one"));
        inv.setItem(35, toggleItem(rec.friendlyFireProtect(), "Friendly fire guard",
                "Same-owner / trusted mobs won't hurt each other"));
        // anchors
        inv.setItem(37, named(Material.ANVIL, rec.anchored() && !rec.hardAnchor()
                ? "<green><bold>Soft anchor: ON" : "<gray>Soft anchor: off",
                "<gray>Anchors to current position, tolerates drift",
                "<yellow>Click to anchor here"));
        inv.setItem(38, named(Material.ANVIL, rec.anchored() && rec.hardAnchor()
                ? "<green><bold>Hard anchor: ON" : "<gray>Hard anchor: off",
                "<gray>Holds exact position + rotation",
                "<yellow>Click to anchor here"));
        if (rec.anchored()) {
            inv.setItem(39, named(Material.BARRIER, "<red>Clear anchor", "<yellow>Click to clear"));
        }
        // native taming status (honest label)
        if (entity instanceof org.bukkit.entity.Tameable t) {
            inv.setItem(41, named(Material.BONE,
                    t.isTamed() ? "<green>Native tame: yes" : "<gray>Native tame: no",
                    t.getOwnerUniqueId() == null ? "<gray>No native owner"
                            : "<gray>Native owner: <white>" + nameOf(t.getOwnerUniqueId()),
                    pluginManaged(entity) ? "<aqua>Also plugin-managed"
                            : "<gray>Not plugin-managed — set owner above"));
        }
        inv.setItem(GuiNav.BACK_54, navBack());
        inv.setItem(GuiNav.HELP_54, helpItem("Profiles: PASSIVE never attacks,",
                "GUARD defends owner + trusted.", "Anchors: soft tolerates drift, hard holds spot."));
        inv.setItem(GuiNav.ACTION_54, named(Material.ENDER_PEARL, "<aqua>Teleport entity here",
                "<gray>Brings the entity to you", "<yellow>Click to teleport"));
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        inv.setItem(53, named(Material.PAPER, "<gray>Precise teleport",
                "<gray>Format: x y z [yaw pitch]", "<yellow>Click to type"));
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
        player.openInventory(inv);
    }

    private boolean pluginManaged(Entity entity) {
        ManagedRecord r = plugin.allegiance().get(entity.getUniqueId());
        return r != null && (r.owner() != null || !r.trusted().isEmpty()
                || !r.enemies().isEmpty() || r.profile() != ManagedRecord.Profile.CUSTOM);
    }

    private String profileHint(ManagedRecord.Profile p) {
        return switch (p) {
            case PASSIVE -> "Never initiates attacks";
            case NEUTRAL -> "Vanilla retaliation only";
            case GUARD -> "Protects owner + trusted";
            case AGGRESSIVE -> "Vanilla targeting rules";
            case STATIONARY -> "AI off, stays put";
            case CUSTOM -> "Configured rule by rule";
        };
    }

    // ------------------------------------------------------------------ trades

    public void openTrades(Player player, Entity entity) {
        openTrades(player, entity, 0);
    }

    public void openTrades(Player player, Entity entity, int page) {
        if (!(entity instanceof org.bukkit.inventory.Merchant merchant)) {
            plugin.send(player, "<red>This entity is not a merchant.");
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.TRADES)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "trades");
        List<org.bukkit.inventory.MerchantRecipe> recipes;
        try {
            recipes = new ArrayList<>(merchant.getRecipes());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read merchant offers of "
                    + entity.getType().name() + " " + entity.getUniqueId() + ": " + e);
            plugin.send(player, "<red>Could not read offers.");
            return;
        }
        int pages = ContainerPages.pageCount(Math.max(1, recipes.size() + 1));
        page = ContainerPages.clampPage(page, Math.max(1, recipes.size() + 1));
        GuiHolder holder = new GuiHolder(Page.TRADES, entity.getUniqueId(), null, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Trades (" + merchant.getRecipeCount() + ")"
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        int start = ContainerPages.startIndex(page, Math.max(1, recipes.size() + 1));
        for (int i = 0; i < 44 && start + i < recipes.size(); i++) {
            var r = recipes.get(i);
            ItemStack show = r.getResult().clone();
            ItemMeta meta = show.getItemMeta();
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(comp("<gray>Uses: <white>" + r.getUses() + "<gray>/<white>" + r.getMaxUses()));
            lore.add(comp("<gray>Price mult: <white>" + r.getPriceMultiplier()));
            lore.add(comp("<gray>Villager XP: <white>" + r.getVillagerExperience()));
            lore.add(comp("<yellow>Click: reset uses  <gray>|  <red>Shift-click: remove"));
            meta.lore(lore);
            show.setItemMeta(meta);
            inv.setItem(i, show);
        }            if (start + 44 >= recipes.size()) {
                inv.setItem(Math.min(44, recipes.size() - start), named(Material.EMERALD, "<green>New offer from hand",
                        "<gray>Result = held item, cost = 1 emerald",
                        "<gray>max 12 uses, 5 XP", "<yellow>Click with item in hand"));
            }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler());
        }
        inv.setItem(GuiNav.BACK_54, navBack());
        inv.setItem(GuiNav.HELP_54, helpItem("Click an offer to restock (reset uses).",
                "Shift-click removes one offer (confirm)."));
        if (ContainerPages.hasPrev(page, Math.max(1, recipes.size() + 1))) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        if (ContainerPages.hasNext(page, Math.max(1, recipes.size() + 1))) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        inv.setItem(53, named(Material.BARRIER, "<red>Remove ALL offers",
                "<red>Destructive — needs confirm", "<yellow>Shift-click to confirm"));
        if (entity instanceof org.bukkit.entity.Villager v) {
            // Keep the action slot free for the standard bar: profession lives
            // on the help-adjacent slot 46 instead of clobbering refresh.
            inv.setItem(46, named(Material.BOOK, "<aqua>Profession/level",
                    "<gray>Edit in Properties (villager.*)", "<yellow>Click to open"));
        }
        player.openInventory(inv);
    }

    // -------------------------------------------------------------- item editor

    /** Edits the equipment slot's live stack. idx follows EQUIP_SLOTS order. */
    public void openItemEditor(Player player, Entity entity, int idx) {
        if (!(entity instanceof LivingEntity living) || living.getEquipment() == null) {
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.INV_VIEW)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "item:" + idx);
        GuiHolder holder = new GuiHolder(Page.ITEM, entity.getUniqueId(), String.valueOf(idx), 0);
        Inventory inv = Bukkit.createInventory(holder, 27,
                plugin.mm().deserialize("<dark_gray>Item: " + EQUIP_NAMES[idx]));
        holder.inv = inv;
        fill(inv, 27);
        ItemStack stack = slotStack(living, idx);
        inv.setItem(4, stack == null ? emptySlot(EQUIP_NAMES[idx]) : stack.clone());
        String name = stack == null || !stack.hasItemMeta()
                || !stack.getItemMeta().hasDisplayName() ? "<none>" : "<set>";
        inv.setItem(10, named(Material.NAME_TAG, "<white>Rename", "<gray>Current: " + name,
                "<yellow>Click to type (empty = clear)"));
        inv.setItem(11, named(Material.WRITABLE_BOOK, "<white>Add lore line",
                "<yellow>Click to type"));
        inv.setItem(12, named(Material.BARRIER, "<white>Clear lore", "<yellow>Click to clear"));
        int amount = stack == null ? 0 : stack.getAmount();
        int max = stack == null ? 64 : stack.getMaxStackSize();
        inv.setItem(13, named(Material.GOLD_NUGGET, "<white>Amount: <aqua>" + amount,
                "<gray>Left-click +1, right-click −1 (max " + max + ")"));
        String dmg = "n/a";
        if (stack != null && stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d
                && stack.getType().getMaxDurability() > 0) {
            dmg = d.getDamage() + " / " + stack.getType().getMaxDurability();
        }
        inv.setItem(14, named(Material.ANVIL, "<white>Damage: <aqua>" + dmg,
                "<yellow>Click to type (0 = repaired)"));
        inv.setItem(15, named(Material.ENCHANTED_BOOK, "<white>Add enchant",
                "<gray>Format: name:level (e.g. sharpness:5)", "<yellow>Click to type"));
        inv.setItem(16, named(Material.MILK_BUCKET, "<white>Clear enchants",
                "<yellow>Click to clear"));
        inv.setItem(0, helpItem("Edits apply to the live equipment slot.",
                "Amount clamps to the item max stack."));
        inv.setItem(8, navRefresh());
        inv.setItem(22, navBack());
        inv.setItem(26, navClose());
        player.openInventory(inv);
    }

    // ---------------------------------------------------------------- history

    // --------------------------------------------------- advanced data (read-only)

    /** Read-only inspector: PDC entries, Bukkit serialization snapshot, and a
     * few derived facts. Getters only — this page has no write path, and the
     * raw serialize() map is rendered, never written back (writing a partial
     * snapshot could erase unknown fields). */
    public void openAdvanced(Player player, Entity entity, String filter, int page) {
        if (!plugin.getConfig().getBoolean("settings.advanced-data.enabled", true)) {
            plugin.send(player, "<gray>Advanced data is disabled in config.");
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.DATA_VIEW)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "advanced");
        List<DataRows.Row> shown = filteredAdvancedRows(entity, filter);
        int pages = ContainerPages.pageCount(shown.size());
        page = ContainerPages.clampPage(page, shown.size());
        GuiHolder holder = new GuiHolder(Page.ADVANCED, entity.getUniqueId(), filter, page);
        String f = filter == null ? "" : filter;
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Advanced data (read-only)"
                        + (f.isBlank() ? "" : " <gray>[" + escape(trunc(f, 12)) + "]")
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        int start = ContainerPages.startIndex(page, shown.size());
        for (int i = 0; i < ContainerPages.PAGE_CONTENT && start + i < shown.size(); i++) {
            DataRows.Row r = shown.get(start + i);
            inv.setItem(i, named(Material.PAPER, "<white>" + escape(trunc(r.path(), 40)),
                    "<gray>" + escape(trunc(r.value(), 60)),
                    "<dark_gray>Click: full value in chat"));
        }
        inv.setItem(GuiNav.BACK_54, navBack());
        inv.setItem(GuiNav.HELP_54, helpItem("Read-only: PDC + supported facts.",
                "No write path — click a row for full value."));
        if (ContainerPages.hasPrev(page, shown.size())) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        inv.setItem(GuiNav.ACTION_54, named(Material.BOOK, "<aqua>Filter",
                f.isBlank() ? "<gray>No filter" : "<gray>Filter: <white>" + escape(trunc(f, 24)),
                "<yellow>Click to type a filter"));
        if (ContainerPages.hasNext(page, shown.size())) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        for (int i = 0; i < 54; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
        player.openInventory(inv);
    }

    private List<DataRows.Row> filteredAdvancedRows(Entity entity, String filter) {
        String f = filter == null ? "" : filter.toLowerCase();
        List<DataRows.Row> out = new ArrayList<>();
        for (DataRows.Row r : collectAdvancedRows(entity)) {
            if (f.isBlank() || r.path().toLowerCase().contains(f)
                    || r.value().toLowerCase().contains(f)) {
                out.add(r);
            }
        }
        return out;
    }

    private static List<DataRows.Row> collectAdvancedRows(Entity entity) {
        Map<String, Object> all = new LinkedHashMap<>();
        Map<String, Object> pdc = new LinkedHashMap<>();
        try {
            PersistentDataContainer c = entity.getPersistentDataContainer();
            for (NamespacedKey key : c.getKeys()) {
                pdc.put(key.getNamespace() + ":" + key.getKey(), readPdc(c, key));
            }
        } catch (Exception e) {
            pdc.put("<error>", String.valueOf(e));
        }
        all.put("pdc", pdc);
        // NOTE: no raw-NBT or serialize-and-rewrite path here on purpose.
        // Paper's public API has no safe universal readable snapshot, so the
        // browser covers PDC + supported getters only (see docs/property-support.md).
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("class", entity.getClass().getName());
        info.put("entity-id", entity.getEntityId());
        info.put("uuid", entity.getUniqueId().toString());
        info.put("ticks-lived", entity.getTicksLived());
        info.put("velocity", String.valueOf(entity.getVelocity()));
        info.put("passengers", entity.getPassengers().size());
        info.put("vehicle", entity.getVehicle() == null ? "none"
                : entity.getVehicle().getType().name().toLowerCase());
        info.put("portal-cooldown", entity.getPortalCooldown());
        all.put("info", info);
        return DataRows.flatten(all);
    }

    private static Object readPdc(PersistentDataContainer c, NamespacedKey key) {
        try {
            if (c.has(key, PersistentDataType.STRING)) {
                return c.get(key, PersistentDataType.STRING);
            }
            if (c.has(key, PersistentDataType.INTEGER)) {
                return c.get(key, PersistentDataType.INTEGER);
            }
            if (c.has(key, PersistentDataType.LONG)) {
                return c.get(key, PersistentDataType.LONG);
            }
            if (c.has(key, PersistentDataType.DOUBLE)) {
                return c.get(key, PersistentDataType.DOUBLE);
            }
            if (c.has(key, PersistentDataType.FLOAT)) {
                return c.get(key, PersistentDataType.FLOAT);
            }
            if (c.has(key, PersistentDataType.SHORT)) {
                return c.get(key, PersistentDataType.SHORT);
            }
            if (c.has(key, PersistentDataType.BYTE)) {
                return c.get(key, PersistentDataType.BYTE);
            }
            if (c.has(key, PersistentDataType.INTEGER_ARRAY)) {
                return c.get(key, PersistentDataType.INTEGER_ARRAY);
            }
            if (c.has(key, PersistentDataType.LONG_ARRAY)) {
                return c.get(key, PersistentDataType.LONG_ARRAY);
            }
            if (c.has(key, PersistentDataType.BYTE_ARRAY)) {
                return c.get(key, PersistentDataType.BYTE_ARRAY);
            }
        } catch (Exception ignored) {
            // fall through to unreadable
        }
        return "<unreadable type>";
    }

    private void clickAdvanced(Player player, Entity entity, GuiHolder holder, int slot) {
        if (slot == 45) {
            openDashboard(player, entity);
            return;
        }
        List<DataRows.Row> shown = filteredAdvancedRows(entity, holder.data);
        if (slot == 48 && ContainerPages.hasPrev(holder.pageNum, shown.size())) {
            openAdvanced(player, entity, holder.data, holder.pageNum - 1);
            return;
        }
        if (slot == 50 && ContainerPages.hasNext(holder.pageNum, shown.size())) {
            openAdvanced(player, entity, holder.data, holder.pageNum + 1);
            return;
        }
        if (slot == 49) {
            askChat(player, entity.getUniqueId(), InputKind.DATA_SEARCH, null,
                    "<yellow>Type a filter (blank clears, cancel aborts):");
            return;
        }
        if (slot < 0 || slot >= ContainerPages.PAGE_CONTENT) {
            return;
        }
        int idx = ContainerPages.startIndex(holder.pageNum, shown.size()) + slot;
        if (idx < 0 || idx >= shown.size()) {
            return;
        }
        DataRows.Row r = shown.get(idx);
        plugin.send(player, "<aqua>" + escape(r.path()) + "<gray> = <white>"
                + escape(trunc(r.value(), 400)));
    }

    public void openHistory(Player player, Entity entity) {
        openHistory(player, entity, 0);
    }

    public void openHistory(Player player, Entity entity, int page) {
        if (!plugin.permitted(player, StudioPermissions.UNDO)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "history");
        List<EditRecord> all = plugin.history().recent(player.getUniqueId(), Integer.MAX_VALUE);
        int pages = ContainerPages.pageCount(Math.max(1, all.size()));
        page = ContainerPages.clampPage(page, Math.max(1, all.size()));
        List<EditRecord> records = all.subList(ContainerPages.startIndex(page, Math.max(1, all.size())),
                Math.min(all.size(), ContainerPages.startIndex(page, Math.max(1, all.size())) + ContainerPages.PAGE_CONTENT));
        GuiHolder holder = new GuiHolder(Page.HISTORY,
                entity == null ? null : entity.getUniqueId(), null, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>History (" + all.size() + ")"
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        for (int i = 0; i < records.size(); i++) {
            EditRecord r = records.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + r.entityType().toLowerCase() + " <dark_gray>" + shortUuid(r.entityUuid()));
            lore.add("<gray>Was: <white>" + escape(trunc(r.oldValue(), 40)));
            lore.add("<gray>Now: <aqua>" + escape(trunc(r.newValue(), 40)));
            lore.add("<gray>Risk: " + r.riskLevel().toLowerCase());
            if (r.reversible()) {
                lore.add("<yellow>Click to undo (newest first)");
            } else {
                lore.add("<red>Cannot be undone");
            }
            inv.setItem(i, named(r.reversible() ? Material.CLOCK : Material.GRAY_DYE,
                    "<white>" + r.label(), lore.toArray(new String[0])));
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler());
        }
        if (entity != null) {
            inv.setItem(GuiNav.BACK_54, navBack());
        } else {
            inv.setItem(GuiNav.BACK_54, navClose());
        }
        inv.setItem(GuiNav.HELP_54, helpItem("Newest edit first.",
                "Undo runs in order — no skipping."));
        if (ContainerPages.hasPrev(page, Math.max(1, all.size()))) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        inv.setItem(GuiNav.ACTION_54, named(Material.LIME_DYE, "<green>Undo last",
                "<yellow>Click to undo the newest reversible edit"));
        if (ContainerPages.hasNext(page, Math.max(1, all.size()))) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        player.openInventory(inv);
    }

    // ------------------------------------------------------- snapshots & presets

    public void openSnapshots(Player player, Entity entity) {
        openSnapshots(player, entity, 0);
    }

    public void openSnapshots(Player player, Entity entity, int page) {
        if (!plugin.permitted(player, StudioPermissions.SNAPSHOT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "snapshots");
        List<String> names = new ArrayList<>(plugin.snapshots().snapshotNames());
        int pages = ContainerPages.pageCount(Math.max(1, names.size() + 1));
        page = ContainerPages.clampPage(page, Math.max(1, names.size() + 1));
        GuiHolder holder = new GuiHolder(Page.SNAPSHOTS,
                entity == null ? null : entity.getUniqueId(), null, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Snapshots (" + names.size() + ")"
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        int start = ContainerPages.startIndex(page, Math.max(1, names.size() + 1));
        int i = 0;
        for (int k = start; k < names.size() && i < 44; k++, i++) {
            String name = names.get(k);
            inv.setItem(i, named(Material.ENDER_CHEST, "<aqua>" + escape(name),
                    entity == null ? "<gray>No entity selected — pick one to restore"
                            : "<yellow>Click to restore onto this entity",
                    entity == null ? "" : "<gray>Restoring overwrites listed fields"));
        }
        if (start + 44 >= names.size() && entity != null) {
            inv.setItem(Math.min(44, names.size() - start), named(Material.WRITABLE_BOOK,
                    "<green>Save snapshot of this entity", "<yellow>Click to name it"));
        }
        for (int j = 45; j < 54; j++) {
            inv.setItem(j, filler());
        }
        if (entity != null) {
            inv.setItem(GuiNav.BACK_54, navBack());
        } else {
            inv.setItem(GuiNav.BACK_54, navClose());
        }
        inv.setItem(GuiNav.HELP_54, helpItem("Snapshots capture listed fields only.",
                "Restore previews nothing — it applies now."));
        if (ContainerPages.hasPrev(page, Math.max(1, names.size() + 1))) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        if (ContainerPages.hasNext(page, Math.max(1, names.size() + 1))) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        player.openInventory(inv);
    }

    public void openPresets(Player player, Entity entity) {
        openPresets(player, entity, 0);
    }

    public void openPresets(Player player, Entity entity, int page) {
        if (!plugin.permitted(player, StudioPermissions.PRESETS)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        plugin.sessions().setPage(player.getUniqueId(), "presets");
        List<String> names = new ArrayList<>(plugin.snapshots().presetNames());
        int pages = ContainerPages.pageCount(Math.max(1, names.size() + 1));
        page = ContainerPages.clampPage(page, Math.max(1, names.size() + 1));
        GuiHolder holder = new GuiHolder(Page.PRESETS,
                entity == null ? null : entity.getUniqueId(), null, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                plugin.mm().deserialize("<dark_gray>Presets (" + names.size() + ")"
                        + (pages > 1 ? " <gray>(p. " + (page + 1) + "/" + pages + ")" : "")));
        holder.inv = inv;
        int start = ContainerPages.startIndex(page, Math.max(1, names.size() + 1));
        int i = 0;
        for (int k = start; k < names.size() && i < 44; k++, i++) {
            String name = names.get(k);
            List<String> lore = new ArrayList<>();
            if (entity != null) {
                var preview = plugin.snapshots().previewPreset(name, entity,
                        EnumSet.allOf(
                                com.entitystudio.snapshot.SnapshotService.Section.class));
                if (preview.isEmpty()) {
                    lore.add("<green>Already matches — nothing would change");
                } else {
                    lore.add("<gray>Would change " + preview.size() + " field(s):");
                    for (int m = 0; m < Math.min(4, preview.size()); m++) {
                        var pe = preview.get(m);
                        lore.add("<dark_gray>- <white>" + escape(trunc(pe.label(), 30)));
                    }
                    if (preview.size() > 4) {
                        lore.add("<dark_gray>…and " + (preview.size() - 4) + " more");
                    }
                }
                lore.add("<yellow>Click: apply ALL  <gray>|  <aqua>Right-click: apply menu");
            } else {
                lore.add("<gray>Select an entity to apply");
            }
            inv.setItem(i, named(Material.ENDER_EYE, "<light_purple>" + escape(name),
                    lore.toArray(new String[0])));
        }
        if (start + 44 >= names.size() && entity != null) {
            inv.setItem(Math.min(44, Math.max(0, names.size() - start)), named(Material.WRITABLE_BOOK,
                    "<green>Save current entity as preset", "<yellow>Click to name it"));
        }
        for (int j = 45; j < 54; j++) {
            inv.setItem(j, filler());
        }
        if (entity != null) {
            inv.setItem(GuiNav.BACK_54, navBack());
        } else {
            inv.setItem(GuiNav.BACK_54, navClose());
        }
        inv.setItem(GuiNav.HELP_54, helpItem("Preview shows the first 4 changed fields.",
                "Right-click picks properties / equipment / behavior."));
        if (ContainerPages.hasPrev(page, Math.max(1, names.size() + 1))) {
            inv.setItem(GuiNav.PREV_54, prevButton(page, pages));
        }
        if (ContainerPages.hasNext(page, Math.max(1, names.size() + 1))) {
            inv.setItem(GuiNav.NEXT_54, nextButton(page, pages));
        }
        inv.setItem(GuiNav.REFRESH_54, navRefresh());
        inv.setItem(GuiNav.CLOSE_54, navClose());
        player.openInventory(inv);
    }

    /** Section picker opened by right-clicking a preset: narrow the apply. */
    public void openPresetSections(Player player, Entity entity, String preset) {
        GuiHolder picker = new GuiHolder(Page.PRESETS, entity.getUniqueId(),
                "sections:" + preset, 0);
        Inventory inv = Bukkit.createInventory(picker, 27,
                plugin.mm().deserialize("<dark_gray>Apply: " + escape(trunc(preset, 16))));
        picker.inv = inv;
        fill(inv, 27);
        inv.setItem(10, named(Material.BOOK, "<aqua>Properties only",
                "<gray>Preview this section first", "<yellow>Click to apply"));
        inv.setItem(12, named(Material.IRON_CHESTPLATE, "<aqua>Equipment only",
                "<yellow>Click to apply"));
        inv.setItem(14, named(Material.LEAD, "<aqua>Behavior only",
                "<yellow>Click to apply"));
        inv.setItem(16, named(Material.ENDER_EYE, "<green>Everything",
                "<yellow>Click to apply"));
        inv.setItem(GuiNav.BACK_27, navBack());
        inv.setItem(25, navRefresh());
        inv.setItem(GuiNav.CLOSE_27, navClose());
        player.openInventory(inv);
    }

    // ------------------------------------------------------------ click routing

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        // Button boards, never storage: cancel first so shift-clicks,
        // number keys, offhand swaps, double-clicks and drags can never
        // move GUI items into player inventories or vice versa.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (GuiNav.isUnsafeClick(event.getClick().name(), false)) {
            return;
        }
        if (holder.page == Page.CONTAINER) {
            // Container mirror: bottom-inventory shift-clicks deposit items,
            // so this page bypasses the top-inventory-only gate below.
            clickContainer(player, holder, event);
            return;
        }
        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        Entity entity = holder.entityUuid == null ? null : Bukkit.getEntity(holder.entityUuid);
        // HISTORY/SNAPSHOTS/PRESETS pages may legitimately have no entity.
        boolean needsEntity = holder.page != Page.HISTORY && holder.page != Page.SNAPSHOTS
                && holder.page != Page.PRESETS;
        if (!needsEntity && entity != null && (entity.isDead() || !entity.isValid())) {
            entity = null;
        }
        if (needsEntity && entity == null) {
            plugin.send(player, plugin.msg("session-invalid"));
            player.closeInventory();
            return;
        }
        if (needsEntity) {
            entity = resolve(player, holder);
            if (entity == null) {
                return;
            }
        }
        int slot = event.getSlot();
        // Universal navigation: Refresh (52) redraws the live entity on
        // every 54-page; Close (53) closes except where 53 is a guarded
        // destructive action (dashboard / effects / trades / behavior).
        if (event.getView().getTopInventory().getSize() == 54 && holder.page != Page.CONFIRM) {
            if (slot == GuiNav.REFRESH_54) {
                refresh(player, holder);
                return;
            }
            if (slot == GuiNav.CLOSE_54 && holder.page != Page.DASHBOARD
                    && holder.page != Page.EFFECTS && holder.page != Page.TRADES
                    && holder.page != Page.BEHAVIOR) {
                player.closeInventory();
                return;
            }
            if (slot == GuiNav.HELP_54) {
                plugin.send(player, "<aqua>Back <gray>= dashboard · <yellow>Refresh <gray>= re-read entity · <red>Close <gray>= Esc works too · <gray>type <red>cancel <gray>in chat to abort input.");
                return;
            }
        }
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        switch (holder.page) {
            case DASHBOARD -> clickDashboard(player, entity, slot, right, shift);
            case ADVANCED -> clickAdvanced(player, entity, holder, slot);
            case PROPERTIES -> clickProperties(player, entity, holder, slot, right);
            case EQUIPMENT -> clickEquipment(player, entity, slot, right, shift,
                    event.getCurrentItem());
            case ATTRIBUTES -> clickAttributes(player, entity, holder, slot, right);
            case EFFECTS -> clickEffects(player, entity, holder, slot, shift);
            case BEHAVIOR -> clickBehavior(player, entity, slot, right);
            case TRADES -> clickTrades(player, entity, holder, slot, shift);
            case ITEM -> clickItem(player, entity, holder, slot, right);
            case HISTORY -> clickHistory(player, entity, holder, slot);
            case SNAPSHOTS -> clickSnapshots(player, entity, holder, slot);
            case PRESETS -> clickPresets(player, entity, holder, slot, right);
            case CONFIRM -> clickConfirm(player, holder, slot);
            case PLAYER_INV -> clickPlayerInv(player, entity, slot);
            case CONTAINER -> {
                // routed earlier; unreachable here
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        // Our GUIs are button boards, not storage: never let drags park items
        // inside them, or players lose items into fake inventories on close.
        if (event.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            event.setCancelled(true);
        }
    }

    private void clickDashboard(Player player, Entity entity, int slot, boolean right,
            boolean shift) {
        switch (slot) {
            case 4 -> plugin.send(player, "<gray>Type: <aqua>"
                    + entity.getType().name().toLowerCase()
                    + "<gray> · EID <white>#" + entity.getEntityId()
                    + "<gray> · UUID <yellow>" + entity.getUniqueId()
                    + "<gray> · At <white>" + loc(entity));
            case 10 -> openEquipment(player, entity);
            case 17 -> openAdvanced(player, entity, null, 0);
            case 11 -> openPlayerInv(player, entity);
            case 12 -> openProperties(player, entity, null);
            case 13 -> openAttributes(player, entity);
            case 14 -> openEffects(player, entity);
            case 15 -> openBehavior(player, entity);
            case 16 -> openTrades(player, entity);
            case 45 -> askChat(player, entity.getUniqueId(), InputKind.SEARCH, null,
                    "<yellow>Type a property name to filter by:");
            case 46 -> {
                if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                boolean old = entity.isCustomNameVisible();
                entity.setCustomNameVisible(!old);
                recordSimple(player, entity, "Name visible", String.valueOf(old),
                        String.valueOf(!old), e2 -> e2.setCustomNameVisible(old));
                plugin.send(player, "<green>Nameplate visible → <white>" + (!old));
                openDashboard(player, entity);
            }
            case 47 -> {
                if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                if (right) {
                    String old = entity.getCustomName();
                    entity.setCustomName(null);
                    recordSimple(player, entity, "Custom name",
                            old == null || old.isBlank() ? "—" : old, "—",
                            e2 -> e2.setCustomName(old));
                    plugin.send(player, "<gray>Custom name cleared.");
                    openDashboard(player, entity);
                } else {
                    askChat(player, entity.getUniqueId(), InputKind.NAME_SET, null,
                            "<yellow>Type the new custom name (blank clears, cancel aborts):");
                }
            }
            case 44 -> player.closeInventory();
            case 48 -> openHistory(player, entity);
            case 49 -> openSnapshots(player, entity);
            case 50 -> openPresets(player, entity);
            case 53 -> {
                if (!shift) {
                    plugin.send(player,
                            "<red>Shift-click the barrier to confirm entity removal.");
                    return;
                }
                if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                ConfirmAction remove = (p, e) -> {
                    plugin.history().record(new EditRecord(p.getUniqueId(),
                            e.getUniqueId(), e.getType().name(), "Remove entity",
                            "existed", "removed", false, "DESTRUCTIVE", null));
                    plugin.sessions().closeForEntity(e.getUniqueId());
                    plugin.stashes().drop(e.getUniqueId());
                    e.remove();
                    p.closeInventory();
                    plugin.send(p, "<red>Entity removed.");
                };
                if (plugin.getConfig().getBoolean("settings.editor.confirm-destructive-actions", true)) {
                    confirm(player, entity, "Remove entity",
                            "Permanently remove this " + entity.getType().name().toLowerCase()
                                    + "? This cannot be undone.", StudioPermissions.DATA_EDIT,
                            remove);
                    return;
                }
                // Confirmations disabled in config: the shift-click above is
                // the explicit confirmation, so execute with a fresh
                // permission + target re-check inside the confirm path.
                confirm(player, entity, "Remove entity",
                        "Confirm: remove this " + entity.getType().name().toLowerCase() + "?",
                        StudioPermissions.DATA_EDIT, remove);
            }
            default -> {
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void clickProperties(Player player, Entity entity, GuiHolder holder, int slot,
            boolean right) {
        if (slot == 45) {
            openDashboard(player, entity);
            return;
        }
        String filter = holder.data;
        List<EntityProperty<?>> all = filter == null || filter.isBlank()
                ? plugin.properties().listFor(entity)
                : plugin.properties().searchFor(entity, filter);
        int total = Math.max(1, all.size());
        int pages = ContainerPages.pageCount(total);
        if (slot == 48 && ContainerPages.hasPrev(holder.pageNum, total)) {
            openProperties(player, entity, filter, holder.pageNum - 1);
            return;
        }
        if (slot == 50 && ContainerPages.hasNext(holder.pageNum, total)) {
            openProperties(player, entity, filter, holder.pageNum + 1);
            return;
        }
        if (slot == 49) {
            if (right && filter != null && !filter.isBlank()) {
                openProperties(player, entity, null, 0);
            } else {
                askChat(player, entity.getUniqueId(), InputKind.SEARCH, null,
                        "<yellow>Type a property name to filter by:");
            }
            return;
        }
        if (slot >= 45) {
            return;
        }
        int idx = ContainerPages.startIndex(holder.pageNum, total) + slot;
        if (idx < 0 || idx >= all.size()) {
            return;
        }
        EntityProperty prop = all.get(idx);
        EntityProperty.Support support = prop.support(entity);
        if (!support.writable()) {
            // Never pretend an unsupported control works: read-only values are
            // shown, special ones route to their dedicated flow.
            switch (support) {
                case READ_ONLY -> {
                    Object v;
                    try {
                        v = prop.read(entity);
                    } catch (Exception e) {
                        v = "<unreadable>";
                    }
                    plugin.send(player, "<gray>" + prop.displayName() + ": <white>"
                            + escape(String.valueOf(v)) + " <dark_gray>(read-only)");
                }
                case SPECIAL -> {
                    if ("horse.chest".equals(prop.id()) && entity instanceof InventoryHolder) {
                        plugin.send(player,
                                "<aqua>Chest changes go through the container — withdrawing first avoids losing items.");
                        openContainerMirror(player, entity);
                    } else {
                        plugin.send(player, "<aqua>" + prop.displayName()
                                + " needs its dedicated editor; it cannot be typed here.");
                    }
                }
                default -> plugin.send(player, "<gray>" + prop.displayName()
                        + " is not available for this entity right now.");
            }
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        if (prop.type() == EntityProperty.PropertyType.BOOLEAN && !right) {
            boolean next = !((Boolean) prop.read(entity));                String err = plugin.properties().apply(player, entity, prop, next,
                        StudioPermissions.DATA_EDIT, node -> plugin.permitted(player, node),
                        plugin.history(),
                        plugin.getConfig().getBoolean("settings.history.enabled", true));
            if (err == null) {
                plugin.send(player, "<green>" + prop.displayName() + " → <white>" + next);
            } else {
                plugin.send(player, "<red>" + err);
            }
            openProperties(player, entity, filter, holder.pageNum);
            return;
        }
        askChat(player, entity.getUniqueId(), InputKind.PROP_VALUE, prop.id(),
                "<yellow>New value for <white>" + prop.displayName() + "<yellow> (now: <white>"
                        + escape(String.valueOf(prop.read(entity))) + "<yellow>):");
    }

    private void clickEquipment(Player player, Entity entity, int slot, boolean right,
            boolean shift, ItemStack clicked) {
        if (!(entity instanceof LivingEntity living) || living.getEquipment() == null) {
            return;
        }
        if (slot == 8) {
            openDashboard(player, entity);
            return;
        }
        if (slot == 7) {
            openEquipment(player, entity);
            return;
        }
        if (slot == 25) {
            player.closeInventory();
            return;
        }
        if (slot == 0 || slot == 1) {
            plugin.send(player,
                    "<gray>Click a label slot below to cycle drop chance (mobs only).");
            return;
        }
        if (slot == 26) {
            if (!shift) {
                plugin.send(player, "<red>Shift-click to confirm clearing all equipment.");
                return;
            }
            if (!plugin.permitted(player, StudioPermissions.INV_EDIT)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            confirm(player, entity, "Clear equipment",
                    "Remove all 6 equipment slots? Item snapshots are recorded.",
                    StudioPermissions.INV_EDIT, (p, e) -> {
                        LivingEntity l = (LivingEntity) e;
                        snapshotEquipment(p, e);
                        EntityEquipment eq = l.getEquipment();
                        eq.setItemInMainHand(null);
                        eq.setItemInOffHand(null);
                        eq.setHelmet(null);
                        eq.setChestplate(null);
                        eq.setLeggings(null);
                        eq.setBoots(null);
                        openEquipment(p, e);
                    });
            return;
        }
        // item rows: EQUIP_SLOTS[i] = label, EQUIP_SLOTS[i]+9 = item
        for (int i = 0; i < 6; i++) {
            if (slot == EQUIP_SLOTS[i] + 9) {
                if (right) {
                    openItemEditor(player, entity, i);
                } else {
                    swapEquipmentSlot(player, entity, i);
                }
                return;
            }
            if (slot == EQUIP_SLOTS[i] && entity instanceof Mob) {
                cycleDropChance(player, (Mob) entity, i);
                return;
            }
        }
    }

    private void swapEquipmentSlot(Player player, Entity entity, int idx) {
        if (!plugin.permitted(player, StudioPermissions.INV_EDIT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        if (!(entity instanceof LivingEntity living) || living.getEquipment() == null) {
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        ItemStack inSlot = slotStack(living, idx);
        ItemStack old = inSlot == null ? null : inSlot.clone();
        setSlot(living, idx, isAir(cursor) ? null : cursor.clone());
        player.setItemOnCursor(isAir(inSlot) ? null : inSlot);
        // refresh the shown item
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof GuiHolder) {
            ItemStack now = slotStack(living, idx);
            top.setItem(EQUIP_SLOTS[idx] + 9,
                    now == null ? emptySlot(EQUIP_NAMES[idx]) : now.clone());
        }
        ItemStack after = slotStack(living, idx);
        String o = old == null ? "empty" : old.getType().name().toLowerCase();
        String n = after == null ? "empty" : after.getType().name().toLowerCase();
        if (plugin.getConfig().getBoolean("settings.history.enabled", true)) {
            plugin.history().record(new EditRecord(player.getUniqueId(), entity.getUniqueId(),
                    entity.getType().name(), "Equipment: " + EQUIP_NAMES[idx], o, n, true,
                    "NORMAL", () -> {
                        Entity e = Bukkit.getEntity(entity.getUniqueId());
                        if (!(e instanceof LivingEntity l) || !e.isValid() || e.isDead()) {
                            throw new IllegalStateException("Entity is gone.");
                        }
                        setSlot(l, idx, old == null ? null : old.clone());
                    }));
        }
    }

    private void snapshotEquipment(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.getEquipment() == null) {
            return;
        }
        ItemStack[] snap = new ItemStack[6];
        for (int i = 0; i < 6; i++) {
            ItemStack s = slotStack(living, i);
            snap[i] = s == null ? null : s.clone();
        }
        plugin.history().record(new EditRecord(player.getUniqueId(), entity.getUniqueId(),
                entity.getType().name(), "Equipment snapshot", describeLoadout(snap),
                "cleared", true, "ADVANCED", () -> {
                    Entity e = Bukkit.getEntity(entity.getUniqueId());
                    if (!(e instanceof LivingEntity l) || !e.isValid() || e.isDead()) {
                        throw new IllegalStateException("Entity is gone.");
                    }
                    for (int i = 0; i < 6; i++) {
                        setSlot(l, i, snap[i] == null ? null : snap[i].clone());
                    }
                }));
    }

    private String describeLoadout(ItemStack[] snap) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snap.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(EQUIP_NAMES[i]).append(":")
                    .append(snap[i] == null ? "empty" : snap[i].getType().name().toLowerCase());
        }
        return sb.toString();
    }

    private void cycleDropChance(Player player, Mob mob, int idx) {
        if (!plugin.permitted(player, StudioPermissions.INV_EDIT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) {
            return;
        }
        float cur = switch (idx) {
            case 0 -> eq.getItemInMainHandDropChance();
            case 1 -> eq.getItemInOffHandDropChance();
            case 2 -> eq.getHelmetDropChance();
            case 3 -> eq.getChestplateDropChance();
            case 4 -> eq.getLeggingsDropChance();
            case 5 -> eq.getBootsDropChance();
            default -> 0;
        };
        float next = cur >= 1.0f ? 0f : Math.round((cur + 0.25f) * 100) / 100f;
        String label = "Drop chance: " + EQUIP_NAMES[idx];
        String old = String.valueOf(cur);
        switch (idx) {
            case 0 -> eq.setItemInMainHandDropChance(next);
            case 1 -> eq.setItemInOffHandDropChance(next);
            case 2 -> eq.setHelmetDropChance(next);
            case 3 -> eq.setChestplateDropChance(next);
            case 4 -> eq.setLeggingsDropChance(next);
            case 5 -> eq.setBootsDropChance(next);
            default -> {
            }
        }
        float done = next;
        if (plugin.getConfig().getBoolean("settings.history.enabled", true)) {
            plugin.history().record(new EditRecord(player.getUniqueId(), mob.getUniqueId(),
                    mob.getType().name(), label, old, String.valueOf(done), true, "NORMAL",
                    () -> {
                        Entity e = Bukkit.getEntity(mob.getUniqueId());
                        if (!(e instanceof Mob m2) || !e.isValid() || e.isDead()) {
                            throw new IllegalStateException("Entity is gone.");
                        }
                        setDropChance(m2, idx, Float.parseFloat(old));
                    }));
        }
        openEquipment(player, mob);
    }

    private void setDropChance(Mob mob, int idx, float v) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) {
            return;
        }
        switch (idx) {
            case 0 -> eq.setItemInMainHandDropChance(v);
            case 1 -> eq.setItemInOffHandDropChance(v);
            case 2 -> eq.setHelmetDropChance(v);
            case 3 -> eq.setChestplateDropChance(v);
            case 4 -> eq.setLeggingsDropChance(v);
            case 5 -> eq.setBootsDropChance(v);
            default -> {
            }
        }
    }

    private void clickAttributes(Player player, Entity entity, GuiHolder holder, int slot, boolean right) {
        if (!(entity instanceof Attributable attr)) {
            return;
        }
        if (slot == 45) {
            openDashboard(player, entity);
            return;
        }
        List<AttributeInstance> list = new ArrayList<>();
        for (Attribute key : org.bukkit.Registry.ATTRIBUTE) {
            AttributeInstance inst = attr.getAttribute(key);
            if (inst != null) {
                list.add(inst);
            }
        }
        int total = Math.max(1, list.size());
        int pages = ContainerPages.pageCount(total);
        if (slot == 48 && ContainerPages.hasPrev(holder.pageNum, total)) {
            openAttributes(player, entity, holder.pageNum - 1);
            return;
        }
        if (slot == 50 && ContainerPages.hasNext(holder.pageNum, total)) {
            openAttributes(player, entity, holder.pageNum + 1);
            return;
        }
        if (slot >= 45) {
            return;
        }
        int idx = ContainerPages.startIndex(holder.pageNum, total) + slot;
        if (idx < 0 || idx >= list.size()) {
            return;
        }
        AttributeInstance inst = list.get(idx);
        if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        String key = inst.getAttribute().getKey().getKey();
        if (right) {
            double old = inst.getBaseValue();
            inst.setBaseValue(inst.getDefaultValue());
            if (plugin.getConfig().getBoolean("settings.history.enabled", true)) {
                plugin.history().record(new EditRecord(player.getUniqueId(),
                        entity.getUniqueId(), entity.getType().name(), "Attribute " + key,
                        String.valueOf(old), String.valueOf(inst.getDefaultValue()), true,
                        "NORMAL", () -> {
                            Entity e = Bukkit.getEntity(entity.getUniqueId());
                            if (e instanceof Attributable a && e.isValid() && !e.isDead()) {
                                AttributeInstance ai = a.getAttribute(inst.getAttribute());
                                if (ai != null) {
                                    ai.setBaseValue(old);
                                }
                            }
                        }));
            }
            openAttributes(player, entity, holder.pageNum);
            return;
        }
        askChat(player, entity.getUniqueId(), InputKind.ATTR_VALUE, key,
                "<yellow>New base value for <white>" + key + "<yellow> (now <white>"
                        + round3(inst.getBaseValue()) + "<yellow>, default <white>"
                        + round3(inst.getDefaultValue()) + "<yellow>):");
    }

    private void clickEffects(Player player, Entity entity, GuiHolder holder, int slot, boolean shift) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (slot == 45) {
            openDashboard(player, entity);
            return;
        }
        if (slot == 46) {
            player.closeInventory();
            return;
        }
        int perPage = 44;
        var allFx = new ArrayList<>(living.getActivePotionEffects());
        int pages = Math.max(1, (Math.max(1, allFx.size() + 1) + perPage - 1) / perPage);
        if (slot == 48 && holder.pageNum > 0) {
            openEffects(player, entity, holder.pageNum - 1);
            return;
        }
        if (slot == 50 && holder.pageNum < pages - 1) {
            openEffects(player, entity, holder.pageNum + 1);
            return;
        }
        if (slot == 53) {
            if (!shift) {
                plugin.send(player, "<red>Shift-click to confirm clearing all effects.");
                return;
            }
            if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            var before = new ArrayList<>(living.getActivePotionEffects());
            if (before.isEmpty()) {
                plugin.send(player, "<gray>No effects to clear.");
                return;
            }
            confirm(player, entity, "Clear effects",
                    "Remove all " + before.size() + " effect(s)? Recorded for undo.",
                    StudioPermissions.DATA_EDIT, (p, e) -> {
                        LivingEntity l = (LivingEntity) e;
                        l.clearActivePotionEffects();
                        if (plugin.getConfig().getBoolean("settings.history.enabled", true)) {
                            plugin.history().record(new EditRecord(p.getUniqueId(),
                                    e.getUniqueId(), e.getType().name(), "Clear all effects",
                                    before.size() + " active", "none", true, "ADVANCED", () -> {
                                        Entity e2 = Bukkit.getEntity(e.getUniqueId());
                                        if (e2 instanceof LivingEntity l2 && e2.isValid() && !e2.isDead()) {
                                            l2.addPotionEffects(before);
                                        }
                                    }));
                        }
                        openEffects(p, e, 0);
                    });
            return;
        }
        int start = holder.pageNum * perPage;
        // Add button sits right after the last effect on the final page.
        if (start + perPage >= allFx.size() && slot == Math.min(44, allFx.size() - start)) {
            if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            askChat(player, entity.getUniqueId(), InputKind.EFFECT_ADD, null,
                    "<yellow>Type <white>TYPE:DURATION_TICKS:AMPLIFIER<yellow> (e.g. SPEED:1200:1):");
            return;
        }
        int idx = start + slot;
        if (slot < 0 || slot >= perPage || idx < 0 || idx >= allFx.size()) {
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        var fx = allFx.get(idx);
        confirm(player, entity, "Remove effect",
                "Remove " + fx.getType().getKey().getKey() + "? Recorded for undo.",
                StudioPermissions.DATA_EDIT, (p, e) -> {
                    if (e instanceof LivingEntity l) {
                        l.removePotionEffect(fx.getType());
                    }
                    plugin.history().record(new EditRecord(p.getUniqueId(), e.getUniqueId(),
                            e.getType().name(), "Remove effect " + fx.getType().getKey().getKey(),
                            fx.getAmplifier() + "x" + fx.getDuration() + "t", "removed", true,
                            "NORMAL", () -> {
                                Entity e2 = Bukkit.getEntity(e.getUniqueId());
                                if (e2 instanceof LivingEntity l2 && e2.isValid() && !e2.isDead()) {
                                    l2.addPotionEffect(fx);
                                }
                            }));
                    openEffects(p, e, holder.pageNum);
                });
    }

    private void clickBehavior(Player player, Entity entity, int slot, boolean right) {
        if (slot == 45) {
            openDashboard(player, entity);
            return;
        }
        if (slot == 46 || slot == 51) {
            player.closeInventory();
            return;
        }
        if (slot == 49) {
            if (!(entity instanceof LivingEntity l)) {
                return;
            }
            entity.teleport(player.getLocation());
            plugin.send(player, "<gray>Teleported entity to you.");
            openBehavior(player, entity);
            return;
        }
        if (slot == 53) {
            askChat(player, entity.getUniqueId(), InputKind.TELEPORT, null,
                    "<yellow>Type coordinates <white>x y z [yaw pitch]<yellow>:");
            return;
        }
        ManagedRecord rec = plugin.allegiance().getOrCreate(entity);
        switch (slot) {
            case 10 -> {
                if (!(entity instanceof LivingEntity l)) {
                    return;
                }
                if (!plugin.permitted(player, StudioPermissions.BEHAVIOR)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                boolean old = l.hasAI();
                l.setAI(!old);
                plugin.allegiance().getOrCreate(entity).aiEnabled(!old);
                recordSimple(player, entity, "AI enabled", String.valueOf(old),
                        String.valueOf(!old), e2 -> ((LivingEntity) e2).setAI(old));
                plugin.send(player, "<green>AI enabled → <white>" + (!old));
                openBehavior(player, entity);
            }
            case 11 -> {
                if (entity instanceof Mob m) {
                    if (!plugin.permitted(player, StudioPermissions.BEHAVIOR)) {
                        plugin.send(player, plugin.msg("no-permission"));
                        return;
                    }
                    m.setAware(!m.isAware());
                    plugin.send(player,
                            "<green>Aware → <white>" + m.isAware());
                    openBehavior(player, entity);
                }
            }
            case 12 -> {
                if (entity instanceof Mob m && m.getTarget() != null) {
                    if (!plugin.permitted(player, StudioPermissions.BEHAVIOR)) {
                        plugin.send(player, plugin.msg("no-permission"));
                        return;
                    }
                    m.setTarget(null);
                    plugin.send(player, "<gray>Target cleared.");
                    openBehavior(player, entity);
                }
            }
            case 19, 20, 21, 22, 23, 24 -> {
                if (!(entity instanceof Mob m)) {
                    plugin.send(player, "<red>Profiles apply to mobs.");
                    return;
                }
                if (!plugin.permitted(player, StudioPermissions.BEHAVIOR)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                ManagedRecord.Profile[] profiles = ManagedRecord.Profile.values();
                int idx = slot - 19;
                if (idx < profiles.length) {
                    String out = com.entitystudio.behavior.AllegianceService.applyProfile(m,
                            profiles[idx], rec);
                    plugin.send(player, "<green>" + out);
                    openBehavior(player, entity);
                }
            }
            case 28, 29, 30, 31 -> {
                if (!plugin.permitted(player, StudioPermissions.ALLEGIANCE)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                switch (slot) {
                    case 28 -> rec.defendOwner(!rec.defendOwner());
                    case 29 -> rec.defendAllies(!rec.defendAllies());
                    case 30 -> rec.retaliate(!rec.retaliate());
                    case 31 -> rec.attackOwnerTarget(!rec.attackOwnerTarget());
                    default -> {
                    }
                }
                plugin.send(player, "<green>Allegiance updated.");
                openBehavior(player, entity);
            }
            case 35 -> {
                if (!plugin.permitted(player, StudioPermissions.ALLEGIANCE)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                rec.friendlyFireProtect(!rec.friendlyFireProtect());
                plugin.send(player, rec.friendlyFireProtect()
                        ? "<green>Friendly fire guard ON — allies won't hurt each other."
                        : "<gray>Friendly fire guard OFF.");
                openBehavior(player, entity);
            }
            case 32 -> {
                if (!plugin.permitted(player, StudioPermissions.ALLEGIANCE)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                if (right) {
                    rec.owner(null);
                    plugin.send(player, "<gray>Owner cleared.");
                    openBehavior(player, entity);
                } else {
                    askChat(player, entity.getUniqueId(), InputKind.ALLY_OWNER, null,
                            "<yellow>Type an online player name or UUID for the owner:");
                }
            }
            case 33 -> {
                if (!plugin.permitted(player, StudioPermissions.ALLEGIANCE)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                askChat(player, entity.getUniqueId(),
                        right ? InputKind.TRUST_DEL : InputKind.TRUST_ADD, null,
                        right ? "<yellow>Type a trusted name/UUID to REMOVE:"
                                : "<yellow>Type a player name/UUID to TRUST:");
            }
            case 34 -> {
                if (!plugin.permitted(player, StudioPermissions.ALLEGIANCE)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                askChat(player, entity.getUniqueId(),
                        right ? InputKind.ENEMY_DEL : InputKind.ENEMY_ADD, null,
                        right ? "<yellow>Type an enemy name/UUID to REMOVE:"
                                : "<yellow>Type a player name/UUID to mark ENEMY:");
            }
            case 37, 38 -> {
                if (!plugin.permitted(player, StudioPermissions.BEHAVIOR)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    return;
                }
                var at = entity.getLocation();
                rec.setAnchor(true, slot == 38, at.getWorld().getName(), at.getX(), at.getY(),
                        at.getZ(), at.getYaw(), at.getPitch());
                plugin.send(player, "<green>" + (slot == 38 ? "Hard" : "Soft")
                        + " anchor set here.");
                openBehavior(player, entity);
            }
            case 39 -> {
                rec.setAnchor(false, false, null, 0, 0, 0, 0, 0);
                plugin.send(player, "<gray>Anchor cleared.");
                openBehavior(player, entity);
            }
            default -> {
            }
        }
    }

    private void clickTrades(Player player, Entity entity, GuiHolder holder, int slot, boolean shift) {
        if (!(entity instanceof org.bukkit.inventory.Merchant merchant)) {
            return;
        }
        if (slot == 45) {
            openDashboard(player, entity);
            return;
        }
        if (slot == 46 && entity instanceof org.bukkit.entity.Villager) {
            openProperties(player, entity, "villager", 0);
            return;
        }
        if (slot == 51) {
            player.closeInventory();
            return;
        }
        List<org.bukkit.inventory.MerchantRecipe> all;
        try {
            all = new ArrayList<>(merchant.getRecipes());
        } catch (Exception e) {
            return;
        }
        int total = Math.max(1, all.size() + 1);
        if (slot == 48 && ContainerPages.hasPrev(holder.pageNum, total)) {
            openTrades(player, entity, holder.pageNum - 1);
            return;
        }
        if (slot == 50 && ContainerPages.hasNext(holder.pageNum, total)) {
            openTrades(player, entity, holder.pageNum + 1);
            return;
        }
        int start = ContainerPages.startIndex(holder.pageNum, total);
        // Add-offer button sits right after the last offer on the final page.
        if (start + 44 >= all.size() && slot == Math.min(44, all.size() - start)) {
            if (!plugin.permitted(player, StudioPermissions.TRADES)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (isAir(hand)) {
                plugin.send(player, "<red>Hold the result item in your main hand first.");
                return;
            }
            List<org.bukkit.inventory.MerchantRecipe> recipes =
                    new ArrayList<>(merchant.getRecipes());
            ItemStack result = hand.clone();
            result.setAmount(1);
            org.bukkit.inventory.MerchantRecipe recipe =
                    new org.bukkit.inventory.MerchantRecipe(result, 12);
            recipe.addIngredient(new ItemStack(Material.EMERALD, 1));
            recipe.setExperienceReward(true);
            recipe.setVillagerExperience(5);
            recipes.add(recipe);
            merchant.setRecipes(recipes);
            int index = recipes.size() - 1;
            recordSimple(player, entity, "Add offer (" + result.getType().name().toLowerCase()
                    + " for 1 emerald)", "—", "offer #" + (index + 1), e2 -> {
                        if (e2 instanceof org.bukkit.inventory.Merchant m2) {
                            List<org.bukkit.inventory.MerchantRecipe> rs =
                                    new ArrayList<>(m2.getRecipes());
                            if (index < rs.size()) {
                                rs.remove(index);
                                m2.setRecipes(rs);
                            }
                        }
                    });
            plugin.send(player, "<green>Offer added. Edit uses via the offer icon.");
            openTrades(player, entity, holder.pageNum);
            return;
        }
        if (slot == 53) {
            if (!shift) {
                plugin.send(player, "<red>Shift-click to confirm removing ALL offers.");
                return;
            }
            if (!plugin.permitted(player, StudioPermissions.TRADES)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            var before = new ArrayList<>(all);
            if (before.isEmpty()) {
                plugin.send(player, "<gray>No offers to remove.");
                return;
            }
            confirm(player, entity, "Remove offers",
                    "Remove all " + before.size() + " offer(s)? Recorded for undo.",
                    StudioPermissions.TRADES, (p, e) -> {
                        if (e instanceof org.bukkit.inventory.Merchant m) {
                            m.setRecipes(new ArrayList<>());
                        }
                        recordSimple(p, e, "Remove ALL offers", before.size() + " offers",
                                "none", e2 -> {
                                    if (e2 instanceof org.bukkit.inventory.Merchant m2) {
                                        m2.setRecipes(new ArrayList<>(before));
                                    }
                                });
                        plugin.send(p, "<red>All offers removed.");
                        openTrades(p, e, 0);
                    });
            return;
        }
        List<org.bukkit.inventory.MerchantRecipe> recipes = all;
        int idx = start + slot;
        if (slot < 0 || slot >= 44 || idx < 0 || idx >= recipes.size()) {
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.TRADES)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        if (shift) {
            var removed = recipes.get(idx);
            confirm(player, entity, "Remove offer",
                    "Remove offer #" + (idx + 1) + " (" + removed.getResult().getType().name().toLowerCase() + ")?",
                    StudioPermissions.TRADES, (p, e) -> {
                        if (e instanceof org.bukkit.inventory.Merchant m) {
                            List<org.bukkit.inventory.MerchantRecipe> rs = new ArrayList<>(m.getRecipes());
                            if (idx < rs.size()) {
                                rs.remove(idx);
                                m.setRecipes(rs);
                            }
                        }
                        recordSimple(p, e,
                                "Remove offer (" + removed.getResult().getType().name().toLowerCase() + ")",
                                "offer #" + (idx + 1), "removed", e2 -> {
                                    if (e2 instanceof org.bukkit.inventory.Merchant m2) {
                                        List<org.bukkit.inventory.MerchantRecipe> rs2 =
                                                new ArrayList<>(m2.getRecipes());
                                        rs2.add(Math.min(idx, rs2.size()), removed);
                                        m2.setRecipes(rs2);
                                    }
                                });
                        plugin.send(p, "<gray>Offer removed.");
                        openTrades(p, e, holder.pageNum);
                    });
            return;
        }
        // plain click: reset uses (restock this offer)
        {
            var r = recipes.get(idx);
            int oldUses = r.getUses();
            // MerchantRecipe is mutable in the live list for most implementations;
            // re-set the list to be certain the change sticks.
            r.setUses(0);
            merchant.setRecipes(new ArrayList<>(recipes));
            recordSimple(player, entity, "Restock offer #" + (idx + 1), oldUses + " uses",
                    "0 uses", e2 -> {
                        if (e2 instanceof org.bukkit.inventory.Merchant m2
                                && idx < m2.getRecipeCount()) {
                            var rr = m2.getRecipe(idx);
                            rr.setUses(oldUses);
                            var rs3 = new ArrayList<>(m2.getRecipes());
                            m2.setRecipes(rs3);
                        }
                    });
            plugin.send(player, "<green>Offer restocked.");
            openTrades(player, entity, holder.pageNum);
        }
    }

    private void clickItem(Player player, Entity entity, GuiHolder holder, int slot,
            boolean right) {
        int idx;
        try {
            idx = Integer.parseInt(holder.data);
        } catch (NumberFormatException e) {
            return;
        }
        if (!(entity instanceof LivingEntity living) || living.getEquipment() == null) {
            return;
        }
        if (slot == 22) {
            openEquipment(player, entity);
            return;
        }
        if (slot == 8) {
            openItemEditor(player, entity, idx);
            return;
        }
        if (slot == 26) {
            player.closeInventory();
            return;
        }
        if (slot == 0) {
            plugin.send(player, "<gray>Edits apply to the live slot · amount clamps to max stack.");
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.INV_EDIT)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        ItemStack stack = slotStack(living, idx);
        switch (slot) {
            case 10 -> askChat(player, entity.getUniqueId(), InputKind.ITEM_NAME,
                    holder.data, "<yellow>Type a new name (empty clears):");
            case 11 -> askChat(player, entity.getUniqueId(), InputKind.ITEM_LORE,
                    holder.data, "<yellow>Type a lore line to ADD:");
            case 12 -> {
                if (stack != null && stack.hasItemMeta()) {
                    ItemStack before = stack.clone();
                    ItemMeta meta = stack.getItemMeta();
                    meta.lore(null);
                    stack.setItemMeta(meta);
                    setSlot(living, idx, stack);
                    recordItem(player, entity, idx, before, stack, "Clear lore");
                }
                openItemEditor(player, entity, idx);
            }
            case 13 -> {
                if (stack == null) {
                    return;
                }
                int delta = right ? -1 : 1;
                ItemStack before = stack.clone();
                stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(),
                        stack.getAmount() + delta)));
                setSlot(living, idx, stack);
                recordItem(player, entity, idx, before, stack, "Amount → " + stack.getAmount());
                openItemEditor(player, entity, idx);
            }
            case 14 -> askChat(player, entity.getUniqueId(), InputKind.ITEM_DAMAGE,
                    holder.data, "<yellow>Type damage value (0 = repaired):");
            case 15 -> askChat(player, entity.getUniqueId(), InputKind.ITEM_ENCHANT,
                    holder.data, "<yellow>Type <white>name:level<yellow> (e.g. sharpness:5):");
            case 16 -> {
                if (stack != null && stack.hasItemMeta()) {
                    ItemStack before = stack.clone();
                    ItemMeta meta = stack.getItemMeta();
                    for (var ench : new ArrayList<>(meta.getEnchants().keySet())) {
                        meta.removeEnchant(ench);
                    }
                    stack.setItemMeta(meta);
                    setSlot(living, idx, stack);
                    recordItem(player, entity, idx, before, stack, "Clear enchants");
                }
                openItemEditor(player, entity, idx);
            }
            default -> {
            }
        }
    }

    private void recordItem(Player player, Entity entity, int idx, ItemStack before,
            ItemStack after, String label) {
        if (!plugin.getConfig().getBoolean("settings.history.enabled", true)) {
            return;
        }
        ItemStack b = before == null ? null : before.clone();
        ItemStack a = after == null ? null : after.clone();
        plugin.history().record(new EditRecord(player.getUniqueId(), entity.getUniqueId(),
                entity.getType().name(), label + " (" + EQUIP_NAMES[idx] + ")",
                b == null ? "empty" : b.getType().name().toLowerCase(),
                a == null ? "empty" : a.getType().name().toLowerCase(), true, "NORMAL",
                () -> {
                    Entity e = Bukkit.getEntity(entity.getUniqueId());
                    if (!(e instanceof LivingEntity l) || !e.isValid() || e.isDead()) {
                        throw new IllegalStateException("Entity is gone.");
                    }
                    setSlot(l, idx, b == null ? null : b.clone());
                }));
        plugin.send(player, "<green>" + label + " done.");
    }

    private void clickHistory(Player player, Entity entity, GuiHolder holder, int slot) {
        if (slot == 45 && entity != null) {
            openDashboard(player, entity);
            return;
        }
        if (slot == 45) {
            player.closeInventory();
            return;
        }
        List<EditRecord> all = plugin.history().recent(player.getUniqueId(), Integer.MAX_VALUE);
        int total = Math.max(1, all.size());
        if (slot == 48 && ContainerPages.hasPrev(holder.pageNum, total)) {
            openHistory(player, entity, holder.pageNum - 1);
            return;
        }
        if (slot == 50 && ContainerPages.hasNext(holder.pageNum, total)) {
            openHistory(player, entity, holder.pageNum + 1);
            return;
        }
        if (slot == 49) {
            undoLast(player, entity, holder.pageNum);
            return;
        }
        int idx = ContainerPages.startIndex(holder.pageNum, total) + slot;
        if (idx < 0 || idx >= all.size()) {
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.UNDO)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        // Undo this specific record is approximated by undoing in order until
        // it is reached; single-step undo is exposed as "Undo last".
        undoLast(player, entity, holder.pageNum);
    }

    private void undoLast(Player player, Entity entity, int page) {
        if (!plugin.permitted(player, StudioPermissions.UNDO)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        // The record about to be undone targets a specific entity: honor
        // current protections even when the history page was opened stale.
        EditRecord peek = plugin.history().peekFirst(player.getUniqueId());
        if (peek != null) {
            Entity target = Bukkit.getEntity(peek.entityUuid());
            if (target != null && !plugin.checkTargetAccess(player, target)) {
                return;
            }
        }
        HistoryService.UndoOutcome out = plugin.history().undoLast(player.getUniqueId());
        switch (out.result()) {
            case DONE -> plugin.send(player,
                    plugin.msg("undo-done", "label", out.record().label()));
            case EMPTY -> plugin.send(player, plugin.msg("undo-empty"));
            case IRREVERSIBLE -> plugin.send(player,
                    plugin.msg("undo-irreversible", "label",
                            out.record() == null ? "?" : out.record().label()));
            case FAILED -> {
                plugin.getLogger().warning("Undo failed for " + player.getName()
                        + ": " + out.detail());
                plugin.send(player, "<red>Undo failed — see console.");
            }
        }
        if (entity != null && Bukkit.getEntity(entity.getUniqueId()) != null) {
            openHistory(player, entity, page);
        } else if (entity == null) {
            openHistory(player, null, page);
        }
    }

    private void clickSnapshots(Player player, Entity entity, GuiHolder holder, int slot) {
        if (slot == 45) {
            if (entity != null) {
                openDashboard(player, entity);
            } else {
                player.closeInventory();
            }
            return;
        }
        List<String> names = new ArrayList<>(plugin.snapshots().snapshotNames());
        int total = Math.max(1, names.size() + 1);
        if (slot == 48 && ContainerPages.hasPrev(holder.pageNum, total)) {
            openSnapshots(player, entity, holder.pageNum - 1);
            return;
        }
        if (slot == 50 && ContainerPages.hasNext(holder.pageNum, total)) {
            openSnapshots(player, entity, holder.pageNum + 1);
            return;
        }
        int start = ContainerPages.startIndex(holder.pageNum, total);
        if (entity != null && start + 44 >= names.size() && slot == Math.min(44, names.size() - start)) {
            if (!plugin.permitted(player, StudioPermissions.SNAPSHOT)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            askChat(player, entity.getUniqueId(), InputKind.SNAP_SAVE, null,
                    "<yellow>Type a name for this snapshot:");
            return;
        }
        int idx = start + slot;
        if (idx < 0 || idx >= names.size() || entity == null || slot >= 44) {
            if (!plugin.permitted(player, StudioPermissions.SNAPSHOT)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            Entity target = resolve(player, holder);
            if (target == null) {
                return;
            }
            if (!plugin.checkTargetAccess(player, target)) {
                player.closeInventory();
                return;
            }
            String name = names.get(idx);
            int[] res = plugin.snapshots().applySnapshot(name, target, player);
            plugin.send(player, "<green>Snapshot '<white>" + escape(name) + "<green>' restored: "
                    + res[0] + " changed, " + res[1] + " skipped.");
            openSnapshots(player, entity, holder.pageNum);
        }
    }

    private void clickPresets(Player player, Entity entity, GuiHolder holder, int slot,
            boolean right) {
        // Section-picker sub-page (opened via right-click).
        if (holder.data != null && holder.data.startsWith("sections:")) {
            // The picker outlives the click that opened it: re-check everything.
            if (!plugin.permitted(player, StudioPermissions.PRESETS)) {
                plugin.send(player, plugin.msg("no-permission"));
                player.closeInventory();
                return;
            }
            Entity target = resolve(player, holder);
            if (target == null) {
                return;
            }
            if (!plugin.checkTargetAccess(player, target)) {
                player.closeInventory();
                return;
            }
            String preset = holder.data.substring("sections:".length());
            if (slot == 22) {
                openPresets(player, target, 0);
                return;
            }
            if (slot == 25) {
                openPresetSections(player, target, preset);
                return;
            }
            if (slot == 26) {
                player.closeInventory();
                return;
            }
            var sections = switch (slot) {
                case 10 -> EnumSet.of(
                        com.entitystudio.snapshot.SnapshotService.Section.PROPERTIES);
                case 12 -> EnumSet.of(
                        com.entitystudio.snapshot.SnapshotService.Section.EQUIPMENT);
                case 14 -> EnumSet.of(
                        com.entitystudio.snapshot.SnapshotService.Section.BEHAVIOR);
                case 16 -> EnumSet.allOf(
                        com.entitystudio.snapshot.SnapshotService.Section.class);
                default -> null;
            };
            if (sections != null) {
                int[] res = plugin.snapshots().applyPreset(preset, target, sections, player);
                plugin.send(player, plugin.msg("preset-applied", "name", escape(preset))
                        .replace("<changed>", String.valueOf(res[0])));
                openPresets(player, target);
            }
            return;
        }
        if (slot == 45) {
            if (entity != null) {
                openDashboard(player, entity);
            } else {
                player.closeInventory();
            }
            return;
        }
        List<String> names = new ArrayList<>(plugin.snapshots().presetNames());
        int total = Math.max(1, names.size() + 1);
        if (slot == 48 && ContainerPages.hasPrev(holder.pageNum, total)) {
            openPresets(player, entity, holder.pageNum - 1);
            return;
        }
        if (slot == 50 && ContainerPages.hasNext(holder.pageNum, total)) {
            openPresets(player, entity, holder.pageNum + 1);
            return;
        }
        int start = ContainerPages.startIndex(holder.pageNum, total);
        if (entity != null && start + 44 >= names.size() && slot == Math.min(44, names.size() - start)) {
            if (!plugin.permitted(player, StudioPermissions.PRESETS)) {
                plugin.send(player, plugin.msg("no-permission"));
                return;
            }
            askChat(player, entity.getUniqueId(), InputKind.PRESET_SAVE, null,
                    "<yellow>Type a name for this preset:");
            return;
        }
        int idx = start + slot;
        if (idx < 0 || idx >= names.size() || entity == null || slot >= 44) {
            return;
        }
        if (!plugin.permitted(player, StudioPermissions.PRESETS)) {
            plugin.send(player, plugin.msg("no-permission"));
            return;
        }
        String name = names.get(idx);
        if (right) {
            openPresetSections(player, entity, name);
            return;
        }
        Entity target = resolve(player, holder);
        if (target == null) {
            return;
        }
        if (!plugin.checkTargetAccess(player, target)) {
            player.closeInventory();
            return;
        }
        int[] res = plugin.snapshots().applyPreset(name, target,
                EnumSet.allOf(com.entitystudio.snapshot.SnapshotService.Section.class), player);
        plugin.send(player, plugin.msg("preset-applied", "name", escape(name))
                .replace("<changed>", String.valueOf(res[0])));
        openPresets(player, entity, holder.pageNum);
    }

    private void clickConfirm(Player player, GuiHolder holder, int slot) {
        if (slot == 22 || slot == 15) {
            // Cancel: drop the token, never fire the action, back to dashboard.
            confirms.remove(holder.data);
            Entity back = holder.entityUuid == null ? null : Bukkit.getEntity(holder.entityUuid);
            if (back != null && back.isValid() && !back.isDead()) {
                openDashboard(player, back);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (slot == 26 || slot == 0) {
            if (slot == 26) {
                confirms.remove(holder.data);
                player.closeInventory();
            } else {
                plugin.send(player, "<gray>Confirm = run with fresh checks · Cancel = safe return.");
            }
            return;
        }
        ConfirmEntry entry = confirms.get(holder.data);
        if (entry == null) {
            player.closeInventory();
            return;
        }
        if (slot == 11) {
            confirms.remove(holder.data);
            // Re-validate everything: token creation-time checks are stale by now.
            if (!plugin.permitted(player, entry.permissionNode())) {
                plugin.send(player, plugin.msg("no-permission"));
                player.closeInventory();
                return;
            }
            Entity entity = resolve(player, holder);
            if (entity == null) {
                return;
            }
            if (!plugin.checkTargetAccess(player, entity)) {
                player.closeInventory();
                return;
            }
            entry.action().run(player, entity);
        } else {
            confirms.remove(holder.data);
            Entity entity = Bukkit.getEntity(holder.entityUuid);
            if (entity != null) {
                openDashboard(player, entity);
            } else {
                player.closeInventory();
            }
        }
    }

    private void clickPlayerInv(Player player, Entity entity, int slot) {
        if (!(entity instanceof Player target)) {
            return;
        }
        if (slot == 22) {
            openDashboard(player, entity);
            return;
        }
        if (slot == 8) {
            openPlayerInv(player, entity);
            return;
        }
        if (slot == 26) {
            player.closeInventory();
            return;
        }
        if (slot == 11 && plugin.permitted(player, StudioPermissions.INV_VIEW)) {
            player.openInventory(target.getInventory());
            return;
        }
        if (slot == 13 && plugin.permitted(player, StudioPermissions.INV_VIEW)) {
            player.openInventory(target.getEnderChest());
        }
    }

    /** Functional setter used by history undo (entity re-resolved fresh). */
    private interface EntitySetter {
        void set(Entity entity) throws Exception;
    }

    private void recordSimple(Player player, Entity entity, String label, String oldV,
            String newV, EntitySetter restore) {
        if (!plugin.getConfig().getBoolean("settings.history.enabled", true)) {
            return;
        }
        plugin.history().record(new EditRecord(player.getUniqueId(), entity.getUniqueId(),
                entity.getType().name(), label, oldV, newV, true, "NORMAL", () -> {
                    Entity e = Bukkit.getEntity(entity.getUniqueId());
                    if (e == null || !e.isValid() || e.isDead()) {
                        throw new IllegalStateException("Entity is gone.");
                    }
                    restore.set(e);
                }));
    }

    // ----------------------------------------------------------------- confirm

    public void confirm(Player player, Entity entity, String title, String question,
            String permissionNode, ConfirmAction action) {
        String token = UUID.randomUUID().toString();
        confirms.put(token, new ConfirmEntry(title, question, permissionNode, action));
        GuiHolder holder = new GuiHolder(Page.CONFIRM, entity.getUniqueId(), token, 0);
        Inventory inv = Bukkit.createInventory(holder, 27,
                plugin.mm().deserialize("<dark_gray>" + escape(trunc(title, 24))));
        holder.inv = inv;
        fill(inv, 27);
        inv.setItem(0, helpItem("Confirm runs a fresh permission + target check.",
                "Cancel returns to the dashboard safely."));
        inv.setItem(11, named(Material.LIME_DYE, "<green><bold>CONFIRM",
                "<gray>" + escape(question)));
        inv.setItem(15, named(Material.RED_DYE, "<red><bold>Cancel", "<gray>Go back to dashboard"));
        inv.setItem(22, navBack());
        inv.setItem(26, navClose());
        player.openInventory(inv);
    }

    // -------------------------------------------------------------- chat input

    private void askChat(Player player, UUID entityUuid, InputKind kind, String data,
            String prompt) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new PendingInput(kind, entityUuid, data));
        plugin.send(player, prompt);
        plugin.send(player, plugin.msg("type-cancel-hint"));            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pending.remove(player.getUniqueId()) != null && player.isOnline()) {
                    plugin.send(player, "<gray>Input expired.");
                    reopen(player, entityUuid);
                }
            }, 20L * 60);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatInput(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PendingInput req = pending.remove(player.getUniqueId());
        if (req == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (text.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.send(player, "<gray>Cancelled.");
                reopen(player, req.entityUuid());
            });
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> handleChatInput(player, req, text));
    }

    private void reopen(Player player, UUID entityUuid) {
        // Reopens happen on timer expiry / cancel — the lock or permissions
        // may have lapsed since the GUI closed, so gate again.
        if (!plugin.canUse(player) || !plugin.permitted(player, StudioPermissions.USE)) {
            return;
        }
        Entity e = entityUuid == null ? null : Bukkit.getEntity(entityUuid);
        if (e != null && e.isValid() && !e.isDead()) {
            openDashboard(player, e);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void handleChatInput(Player player, PendingInput req, String text) {
        Entity entity = req.entityUuid() == null ? null : Bukkit.getEntity(req.entityUuid());
        if (entity != null && (entity.isDead() || !entity.isValid())) {
            plugin.send(player, plugin.msg("session-invalid"));
            return;
        }
        if (entity != null) {
            // Chat input arrives up to 60s after the click that requested it:
            // the session, permissions, or target protections may have changed.
            EditorSession s = plugin.sessions().get(player.getUniqueId());
            if (s == null || !s.entityUuid().equals(entity.getUniqueId())
                    || plugin.sessions().validate(plugin, s) != SessionManager.Validation.OK) {
                plugin.send(player, plugin.msg("session-invalid"));
                return;
            }
            if (!plugin.checkTargetAccess(player, entity)) {
                return;
            }
        }
        switch (req.kind()) {
            case SEARCH -> {
                if (entity == null) {
                    return;
                }
                openProperties(player, entity, text);
            }
            case PROP_VALUE -> {
                if (entity == null) {
                    return;
                }
                EntityProperty prop = plugin.properties().byId(req.data());
                if (prop == null) {
                    reopen(player, req.entityUuid());
                    return;
                }
                EntityProperty.ParseResult parsed = prop.parse(text);
                if (!parsed.ok()) {
                    plugin.send(player, "<red>" + parsed.error());
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                String err = plugin.properties().apply(player, entity, prop, parsed.value(),
                        StudioPermissions.DATA_EDIT, node -> plugin.permitted(player, node),
                        plugin.history(),
                        plugin.getConfig().getBoolean("settings.history.enabled", true));
                if (err == null) {
                    plugin.send(player,
                            "<green>" + prop.displayName() + " → <white>" + parsed.value());
                } else {
                    plugin.send(player, "<red>" + err);
                }
                openProperties(player, entity, null);
            }
            case DATA_SEARCH -> {
                if (entity == null) {
                    return;
                }
                if (text.length() > 40) {
                    plugin.send(player, "<red>Filter must be at most 40 characters.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                openAdvanced(player, entity, text.isBlank() ? null : text, 0);
            }
            case NAME_SET -> {
                if (entity == null) {
                    return;
                }
                if (!plugin.permitted(player, StudioPermissions.DATA_EDIT)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    reopen(player, req.entityUuid());
                    return;
                }
                if (text.length() > 256) {
                    plugin.send(player, "<red>Name must be at most 256 characters.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                String oldName = entity.getCustomName();
                String shownOld = oldName == null || oldName.isBlank() ? "—" : oldName;
                if (text.isBlank()) {
                    entity.setCustomName(null);
                    recordSimple(player, entity, "Custom name", shownOld, "—",
                            e2 -> e2.setCustomName(oldName));
                    plugin.send(player, "<gray>Custom name cleared.");
                } else {
                    entity.setCustomName(text);
                    recordSimple(player, entity, "Custom name", shownOld, text,
                            e2 -> e2.setCustomName(oldName));
                    plugin.send(player, "<green>Custom name → <white>" + escape(text));
                }
                openDashboard(player, entity);
            }
            case ATTR_VALUE -> {
                if (!(entity instanceof Attributable attr)) {
                    return;
                }
                AttributeInstance inst = null;
                for (Attribute key : org.bukkit.Registry.ATTRIBUTE) {
                    if (key.getKey().getKey().equals(req.data())) {
                        inst = attr.getAttribute(key);
                    }
                }
                if (inst == null) {
                    reopen(player, req.entityUuid());
                    return;
                }
                EntityProperty.ParseResult<Double> parsedAttr =
                        com.entitystudio.property.Validators.parseDouble(text, 0,
                                Double.MAX_VALUE, "value");
                if (!parsedAttr.ok()) {
                    plugin.send(player, "<red>" + parsedAttr.error());
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                double v = parsedAttr.value();
                double old = inst.getBaseValue();
                inst.setBaseValue(v);
                double done = v;
                recordSimple(player, entity, "Attribute " + req.data(), String.valueOf(old),
                        String.valueOf(done), e2 -> {
                            if (e2 instanceof Attributable a2) {
                                for (Attribute key : org.bukkit.Registry.ATTRIBUTE) {
                                    if (key.getKey().getKey().equals(req.data())) {
                                        AttributeInstance ai = a2.getAttribute(key);
                                        if (ai != null) {
                                            ai.setBaseValue(old);
                                        }
                                    }
                                }
                            }
                        });
                openAttributes(player, entity);
            }
            case EFFECT_ADD -> {
                if (!(entity instanceof LivingEntity living)) {
                    return;
                }
                String[] parts = text.split(":");
                if (parts.length != 3) {
                    plugin.send(player, "<red>Format: TYPE:DURATION_TICKS:AMPLIFIER");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                org.bukkit.potion.PotionEffectType type = null;
                for (var t : org.bukkit.potion.PotionEffectType.values()) {
                    if (t.getKey().getKey().equalsIgnoreCase(parts[0])
                            || t.getName().equalsIgnoreCase(parts[0])) {
                        type = t;
                        break;
                    }
                }
                if (type == null) {
                    plugin.send(player, "<red>Unknown effect '" + parts[0] + "'.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                int dur;
                int amp;
                try {
                    dur = Integer.parseInt(parts[1].trim());
                    amp = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException e) {
                    plugin.send(player, "<red>Duration and amplifier must be numbers.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                if (dur < 1 || dur > 20 * 60 * 60 * 24 || amp < 0 || amp > 255) {
                    plugin.send(player, "<red>Out of range (1..1728000 ticks, amp 0..255).");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                var fx = new org.bukkit.potion.PotionEffect(type, dur, amp);
                living.addPotionEffect(fx);
                final var fType = type;
                recordSimple(player, entity, "Add effect " + fType.getKey().getKey(), "—",
                        amp + "x" + dur + "t", e2 -> {
                            if (e2 instanceof LivingEntity l2) {
                                l2.removePotionEffect(fType);
                            }
                        });
                openEffects(player, entity);
            }
            case ITEM_NAME -> editItemMeta(player, entity, req, meta -> {
                if (text.isEmpty()) {
                    meta.displayName(null);
                } else {
                    meta.displayName(plugin.mm().deserialize(text));
                }
            }, text.isEmpty() ? "Clear name" : "Rename");
            case ITEM_LORE -> editItemMeta(player, entity, req, meta -> {
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore())
                        : new ArrayList<>();
                lore.add(plugin.mm().deserialize(text));
                meta.lore(lore);
            }, "Add lore");
            case ITEM_AMOUNT -> {
                int idx = Integer.parseInt(req.data());
                int v;
                try {
                    v = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    plugin.send(player, "<red>Expected a number.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                mutateSlot(player, entity, idx, stack -> {
                    if (stack != null) {
                        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), v)));
                    }
                }, "Amount → " + v);
            }
            case ITEM_DAMAGE -> {
                int v;
                try {
                    v = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    plugin.send(player, "<red>Expected a number.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                final int dmg = Math.max(0, v);
                mutateSlot(player, entity, Integer.parseInt(req.data()), stack -> {
                    if (stack != null
                            && stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d) {
                        d.setDamage(Math.min(dmg, stack.getType().getMaxDurability()));
                        stack.setItemMeta(d);
                    }
                }, "Damage → " + dmg);
            }
            case ITEM_ENCHANT -> {
                String[] parts = text.split(":");
                if (parts.length != 2) {
                    plugin.send(player, "<red>Format: name:level (e.g. sharpness:5).");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                org.bukkit.enchantments.Enchantment ench = null;
                for (var e2 : org.bukkit.Registry.ENCHANTMENT) {
                    if (e2.getKey().getKey().equalsIgnoreCase(parts[0].trim())) {
                        ench = e2;
                        break;
                    }
                }
                if (ench == null) {
                    plugin.send(player, "<red>Unknown enchantment '" + parts[0].trim() + "'.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                int lvl;
                try {
                    lvl = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    plugin.send(player, "<red>Level must be a number.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                if (lvl < 1 || lvl > 255) {
                    plugin.send(player, "<red>Level must be 1..255.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                final var fEnch = ench;
                final int fLvl = lvl;
                mutateSlot(player, entity, Integer.parseInt(req.data()), stack -> {
                    if (stack != null) {
                        stack.addUnsafeEnchantment(fEnch, fLvl);
                    }
                }, "Enchant " + ench.getKey().getKey() + " " + lvl);
            }
            case ALLY_OWNER, TRUST_ADD, TRUST_DEL, ENEMY_ADD, ENEMY_DEL -> {
                if (entity == null) {
                    return;
                }
                if (!plugin.permitted(player, StudioPermissions.ALLEGIANCE)) {
                    plugin.send(player, plugin.msg("no-permission"));
                    reopen(player, req.entityUuid());
                    return;
                }
                UUID id = resolvePlayerId(text);
                if (id == null) {
                    plugin.send(player,
                            "<red>Unknown player. Type an online name or a UUID.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                ManagedRecord rec = plugin.allegiance().getOrCreate(entity);
                switch (req.kind()) {
                    case ALLY_OWNER -> {
                        rec.owner(id);
                        plugin.send(player, "<green>Owner set to <white>" + nameOf(id));
                    }
                    case TRUST_ADD -> {
                        rec.trusted().add(id);
                        plugin.send(player, "<green>Trusted <white>" + nameOf(id));
                    }
                    case TRUST_DEL -> {
                        rec.trusted().remove(id);
                        plugin.send(player, "<gray>Untrusted <white>" + nameOf(id));
                    }
                    case ENEMY_ADD -> {
                        rec.enemies().add(id);
                        plugin.send(player, "<red>Marked enemy: <white>" + nameOf(id));
                    }
                    case ENEMY_DEL -> {
                        rec.enemies().remove(id);
                        plugin.send(player, "<gray>Unmarked <white>" + nameOf(id));
                    }
                    default -> {
                    }
                }
                openBehavior(player, entity);
            }
            case PRESET_SAVE -> {
                if (entity == null) {
                    return;
                }
                if (!com.entitystudio.snapshot.SnapshotService.isValidEntryName(text)) {
                    plugin.send(player, "<red>Use 1..32 letters, numbers, spaces, - or _.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                plugin.snapshots().savePreset(text, entity);
                plugin.send(player, "<green>Preset '<white>" + escape(text) + "<green>' saved.");
                openPresets(player, entity);
            }
            case SNAP_SAVE -> {
                if (entity == null) {
                    return;
                }
                if (!com.entitystudio.snapshot.SnapshotService.isValidEntryName(text)) {
                    plugin.send(player, "<red>Use 1..32 letters, numbers, spaces, - or _.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                plugin.snapshots().saveSnapshot(text, entity);
                plugin.send(player, plugin.msg("snapshot-saved", "name", escape(text)));
                openSnapshots(player, entity);
            }
            case TELEPORT -> {
                if (entity == null) {
                    return;
                }
                String[] parts = text.split("\\s+");
                if (parts.length < 3) {
                    plugin.send(player, "<red>Format: x y z [yaw pitch].");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                try {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double z = Double.parseDouble(parts[2]);
                    float yaw = parts.length > 3 ? Float.parseFloat(parts[3])
                            : entity.getLocation().getYaw();
                    float pitch = parts.length > 4 ? Float.parseFloat(parts[4])
                            : entity.getLocation().getPitch();
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                            || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                        plugin.send(player, "<red>Coordinates must be finite numbers.");
                        pending.put(player.getUniqueId(), req);
                        return;
                    }
                    entity.teleport(new org.bukkit.Location(entity.getWorld(), x, y, z, yaw,
                            pitch));
                    plugin.send(player, "<gray>Teleported.");
                } catch (NumberFormatException e) {
                    plugin.send(player, "<red>Coordinates must be numbers.");
                    pending.put(player.getUniqueId(), req);
                    return;
                }
                openBehavior(player, entity);
            }
        }
    }

    private UUID resolvePlayerId(String text) {
        Player online = Bukkit.getPlayerExact(text.trim());
        if (online != null) {
            return online.getUniqueId();
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private interface MetaEdit {
        void edit(ItemMeta meta);
    }

    private void editItemMeta(Player player, Entity entity, PendingInput req, MetaEdit edit,
            String label) {
        int idx = Integer.parseInt(req.data());
        mutateSlot(player, entity, idx, stack -> {
            if (stack != null) {
                ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta()
                        : Bukkit.getItemFactory().getItemMeta(stack.getType());
                if (meta != null) {
                    edit.edit(meta);
                    stack.setItemMeta(meta);
                }
            }
        }, label);
    }

    private interface SlotEdit {
        void edit(ItemStack stack);
    }

    private void mutateSlot(Player player, Entity entity, int idx, SlotEdit edit, String label) {
        if (!(entity instanceof LivingEntity living) || living.getEquipment() == null) {
            return;
        }
        ItemStack stack = slotStack(living, idx);
        if (stack == null) {
            plugin.send(player, "<red>That slot is empty.");
            openItemEditor(player, entity, idx);
            return;
        }
        ItemStack before = stack.clone();
        edit.edit(stack);
        setSlot(living, idx, stack);
        recordItem(player, entity, idx, before, stack, label);
        openItemEditor(player, entity, idx);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Sessions never survive a disconnect: the next login re-selects with
        // fresh permission checks, and no stale lock can block other editors.
        pending.remove(event.getPlayer().getUniqueId());
        plugin.selector().exitInspect(event.getPlayer().getUniqueId());
        plugin.sessions().close(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Sessions persist across GUI closes; nothing to clean up per page.
    }

    // ----------------------------------------------------------------- helpers

    private ItemStack named(Material mat, String nameMini, String... loreMini) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.mm().deserialize(nameMini));
        List<Component> lore = new ArrayList<>();
        for (String line : loreMini) {
            if (line.isEmpty()) {
                continue;
            }
            lore.add(plugin.mm().deserialize(line));
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
            lore.add(plugin.mm().deserialize("<gray>" + line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component comp(String mini) {
        return plugin.mm().deserialize(mini);
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navBack() {
        return named(Material.ARROW, "<red>Back", "<gray>Back to dashboard", "<dark_gray>Slot 45 on every page");
    }

    private ItemStack navClose() {
        return named(Material.BARRIER, "<red>Close", "<gray>Close this menu", "<dark_gray>Session stays — /entity menu reopens");
    }

    private ItemStack navRefresh() {
        return named(Material.SUNFLOWER, "<yellow>Refresh", "<gray>Re-read the entity and redraw", "<dark_gray>Use after outside changes");
    }

    private ItemStack helpItem(String... lines) {
        List<String> lore = new ArrayList<>();
        for (String l : lines) {
            lore.add("<gray>" + l);
        }
        lore.add("<dark_gray>Cancel: type cancel in chat · Esc closes");
        return named(Material.BOOK, "<aqua>How to use", lore.toArray(new String[0]));
    }

    private ItemStack prevButton(int page, int pages) {
        return named(Material.ARROW, "<yellow>Previous page",
                "<gray>Page " + (page + 1) + " of " + pages, "<yellow>Click to go back");
    }

    private ItemStack nextButton(int page, int pages) {
        return named(Material.ARROW, "<yellow>Next page",
                "<gray>Page " + (page + 2) + " of " + pages, "<yellow>Click to continue");
    }

    /** Re-reads the live entity and redraws whatever page this menu shows. */
    private void refresh(Player player, GuiHolder holder) {
        Entity entity = holder.entityUuid == null ? null : Bukkit.getEntity(holder.entityUuid);
        if (entity != null && (entity.isDead() || !entity.isValid())) {
            entity = null;
        }
        boolean needsEntity = holder.page != Page.HISTORY && holder.page != Page.SNAPSHOTS
                && holder.page != Page.PRESETS;
        if (needsEntity && entity == null) {
            plugin.send(player, plugin.msg("session-invalid"));
            player.closeInventory();
            return;
        }
        switch (holder.page) {
            case DASHBOARD -> { if (entity != null) openDashboard(player, entity); }
            case PROPERTIES -> { if (entity != null) openProperties(player, entity, holder.data, holder.pageNum); }
            case ATTRIBUTES -> { if (entity != null) openAttributes(player, entity, holder.pageNum); }
            case EFFECTS -> { if (entity != null) openEffects(player, entity, holder.pageNum); }
            case BEHAVIOR -> { if (entity != null) openBehavior(player, entity); }
            case TRADES -> { if (entity != null) openTrades(player, entity, holder.pageNum); }
            case PLAYER_INV -> { if (entity != null) openPlayerInv(player, entity); }
            case CONTAINER -> { if (entity != null) openContainerMirror(player, entity, ContainerPages.parsePage(holder.data)); }
            case ADVANCED -> { if (entity != null) openAdvanced(player, entity, holder.data, holder.pageNum); }
            case ITEM -> { if (entity != null) openItemEditor(player, entity, safeIdx(holder.data)); }
            case HISTORY -> openHistory(player, entity, holder.pageNum);
            case SNAPSHOTS -> openSnapshots(player, entity, holder.pageNum);
            case PRESETS -> {
                if (holder.data != null && holder.data.startsWith("sections:")) {
                    if (entity != null) openPresetSections(player, entity, holder.data.substring("sections:".length()));
                } else {
                    openPresets(player, entity, holder.pageNum);
                }
            }
            default -> { if (entity != null) openDashboard(player, entity); }
        }
    }

    private int safeIdx(String data) {
        try {
            return Integer.parseInt(data);
        } catch (Exception e) {
            return 0;
        }
    }

    private ItemStack toggleItem(boolean on, String name, String hint) {
        return named(on ? Material.LIME_DYE : Material.GRAY_DYE,
                (on ? "<green><bold>" : "<red><bold>") + name + (on ? ": ON" : ": OFF"),
                "<gray>" + hint, "<yellow>Click to toggle");
    }

    private void fill(Inventory inv, int size) {
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler());
            }
        }
    }

    private ItemStack slotStack(LivingEntity living, int idx) {
        EntityEquipment eq = living.getEquipment();
        if (eq == null) {
            return null;
        }
        ItemStack s = switch (idx) {
            case 0 -> eq.getItemInMainHand();
            case 1 -> eq.getItemInOffHand();
            case 2 -> eq.getHelmet();
            case 3 -> eq.getChestplate();
            case 4 -> eq.getLeggings();
            case 5 -> eq.getBoots();
            default -> null;
        };
        return isAir(s) ? null : s;
    }

    private void setSlot(LivingEntity living, int idx, ItemStack stack) {
        EntityEquipment eq = living.getEquipment();
        if (eq == null) {
            return;
        }
        switch (idx) {
            case 0 -> eq.setItemInMainHand(stack);
            case 1 -> eq.setItemInOffHand(stack);
            case 2 -> eq.setHelmet(stack);
            case 3 -> eq.setChestplate(stack);
            case 4 -> eq.setLeggings(stack);
            case 5 -> eq.setBoots(stack);
            default -> {
            }
        }
    }

    private boolean isAir(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private String nameOf(UUID id) {
        if (id == null) {
            return "none";
        }
        var off = Bukkit.getOfflinePlayer(id);
        String n = off.getName();
        return n == null ? shortUuid(id) : n;
    }

    private static String shortUuid(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String loc(Entity e) {
        var l = e.getLocation();
        return (int) l.getX() + ", " + (int) l.getY() + ", " + (int) l.getZ() + " @ "
                + l.getWorld().getName();
    }

    private static String displayName(Entity e) {
        String custom = e.getCustomName();
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        return e.getType().name().toLowerCase().replace('_', ' ');
    }

    private static String round1(double v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }

    private static String round3(double v) {
        return String.valueOf(Math.round(v * 1000) / 1000.0);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("<", "&lt;");
    }

    private static String trunc(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]+>", "");
    }
}
