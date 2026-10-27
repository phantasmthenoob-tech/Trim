package com.entitystudio.mobform;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

/**
 * Mob "costumes": armor, mob heads and hand props worn while morphed so the
 * player reads as the mob to everyone — including themselves in F5. This is
 * deliberately a costume, not a model swap: Paper gives plugins no way to
 * change a player's client-side model without client mods, so anyone
 * promising a literal transformation from a plugin alone is lying. Heads
 * (dragon, creeper, zombie, skeletons, piglin), dyed leather "skin"
 * suits, elytra wings and hand props are the closest the public API allows.
 *
 * <p>Every worn piece is tagged and the replaced gear is cloned beforehand,
 * so unmorphing restores exactly what you had and death drops your real
 * gear instead of the costume.
 */
final class MobFormCostumes {

    /**
     * Pure-data costume spec: helmet/chest/legs/boots materials, an optional
     * tint applied to leather pieces only, and an optional hand prop (worn
     * only when the main hand is empty, so kits never fight costumes).
     */
    record Spec(Material helmet, Material chest, Material legs, Material boots,
            Color tint, Material hand) {
    }

    private static final Color PIG_PINK = Color.fromRGB(242, 160, 170);

    private static final Map<String, Spec> SPECS = Map.ofEntries(
            Map.entry("EVOKER", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.GRAY, Material.TOTEM_OF_UNDYING)),
            Map.entry("BLAZE", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.ORANGE, Material.BLAZE_ROD)),
            Map.entry("SHEEP", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.WHITE, Material.WHEAT)),
            Map.entry("CREEPER", new Spec(Material.CREEPER_HEAD,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.GREEN, Material.TNT)),
            Map.entry("ENDERMAN", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.BLACK, Material.GRASS_BLOCK)),
            Map.entry("SKELETON", new Spec(Material.SKELETON_SKULL,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.WHITE, Material.BONE)),
            Map.entry("STRAY", new Spec(Material.SKELETON_SKULL,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.SILVER, Material.PACKED_ICE)),
            Map.entry("PILLAGER", new Spec(Material.LEATHER_HELMET,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.GRAY, Material.CROSSBOW)),
            Map.entry("PIGLIN", new Spec(Material.GOLDEN_HELMET,
                    Material.GOLDEN_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.ORANGE, null)),
            Map.entry("PIGLIN_BRUTE", new Spec(Material.GOLDEN_HELMET,
                    Material.GOLDEN_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.BLACK, null)),
            Map.entry("HUSK", new Spec(Material.ZOMBIE_HEAD,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.OLIVE, Material.SAND)),
            Map.entry("DROWNED", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.TEAL, Material.NAUTILUS_SHELL)),
            Map.entry("WITHER_SKELETON", new Spec(Material.WITHER_SKELETON_SKULL,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.BLACK, Material.COAL)),
            Map.entry("VINDICATOR", new Spec(Material.IRON_HELMET,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.GRAY, null)),
            Map.entry("ZOMBIE", new Spec(Material.ZOMBIE_HEAD,
                    Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.TEAL, Material.ROTTEN_FLESH)),
            Map.entry("SPIDER", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.BLACK, Material.COBWEB)),
            Map.entry("CAVE_SPIDER", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.BLACK, Material.FERMENTED_SPIDER_EYE)),
            Map.entry("SLIME", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.LIME, Material.SLIME_BALL)),
            Map.entry("GHAST", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.WHITE, Material.GHAST_TEAR)),
            Map.entry("WITCH", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.PURPLE, Material.STICK)),
            Map.entry("WITHER", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.BLACK, Material.NETHER_STAR)),
            Map.entry("ENDER_DRAGON", new Spec(Material.DRAGON_HEAD,
                    Material.ELYTRA, Material.LEATHER_LEGGINGS,
                    Material.LEATHER_BOOTS, Color.PURPLE, null)),
            Map.entry("PIG", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    PIG_PINK, Material.CARROT)),
            Map.entry("COW", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.GRAY, Material.MILK_BUCKET)),
            Map.entry("CHICKEN", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.YELLOW, Material.FEATHER)),
            Map.entry("WOLF", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.GRAY, Material.BONE)),
            Map.entry("HORSE", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.MAROON, Material.APPLE)),
            Map.entry("PHANTOM", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.AQUA, Material.PHANTOM_MEMBRANE)),
            Map.entry("VEX", new Spec(null, Material.LEATHER_CHESTPLATE,
                    Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                    Color.SILVER, Material.IRON_SWORD)));

    /** Gear stashed when the costume goes on, for exact restore later. */
    record Prior(ItemStack[] armor, ItemStack hand) {
    }

    private MobFormCostumes() {
    }

    static Spec specFor(String typeName) {
        return typeName == null ? null : SPECS.get(typeName.toUpperCase());
    }

    static boolean isMarked(ItemStack stack, NamespacedKey mark) {
        if (stack == null || stack.getType().isAir() || mark == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null
                && meta.getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
    }

    private static ItemStack marked(ItemStack stack, NamespacedKey mark) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
            if (meta instanceof LeatherArmorMeta leather) {
                // Tint is applied by the caller before marking; nothing to do here.
                stack.setItemMeta(leather);
            } else {
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }

    private static ItemStack tinted(Material mat, Color tint) {
        ItemStack stack = new ItemStack(mat);
        if (tint != null && stack.getItemMeta() instanceof LeatherArmorMeta leather) {
            leather.setColor(tint);
            stack.setItemMeta(leather);
        }
        return stack;
    }

    private static ItemStack[] copyArmor(ItemStack[] armor) {
        ItemStack[] out = new ItemStack[armor.length];
        for (int i = 0; i < armor.length; i++) {
            out[i] = armor[i] == null ? null : armor[i].clone();
        }
        return out;
    }

    /**
     * Wears the form's costume, stashing replaced gear. Pieces go only onto
     * empty armor slots; the hand prop only onto an empty main hand.
     */
    static Prior wear(Player player, String typeName, NamespacedKey mark) {
        Spec spec = specFor(typeName);
        if (spec == null) {
            return null;
        }
        PlayerInventory inv = player.getInventory();
        Prior prior = new Prior(copyArmor(inv.getArmorContents()),
                inv.getItemInMainHand().clone());
        if (spec.helmet() != null && isAir(inv.getHelmet())) {
            inv.setHelmet(marked(new ItemStack(spec.helmet()), mark));
        }
        if (spec.chest() != null && isAir(inv.getChestplate())) {
            inv.setChestplate(spec.chest() == Material.ELYTRA
                    ? marked(new ItemStack(Material.ELYTRA), mark)
                    : marked(tinted(spec.chest(), spec.tint()), mark));
        }
        if (spec.legs() != null && isAir(inv.getLeggings())) {
            inv.setLeggings(marked(tinted(spec.legs(), spec.tint()), mark));
        }
        if (spec.boots() != null && isAir(inv.getBoots())) {
            inv.setBoots(marked(tinted(spec.boots(), spec.tint()), mark));
        }
        if (spec.hand() != null) {
            ItemStack hand = inv.getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                inv.setItemInMainHand(marked(new ItemStack(spec.hand()), mark));
            }
        }
        return prior;
    }

    private static boolean isAir(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    /** Removes tagged costume pieces and puts the stashed gear back. */
    static void restore(Player player, Prior prior, NamespacedKey mark) {
        if (prior == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length && i < prior.armor().length; i++) {
            if (isMarked(armor[i], mark)) {
                armor[i] = null;
            }
        }
        inv.setArmorContents(armor);
        if (isMarked(inv.getItemInMainHand(), mark)) {
            inv.setItemInMainHand(null);
        }
        armor = inv.getArmorContents();
        for (int i = 0; i < armor.length && i < prior.armor().length; i++) {
            ItemStack back = prior.armor()[i];
            if (back == null || back.getType().isAir()) {
                continue;
            }
            if (armor[i] == null || armor[i].getType().isAir()) {
                armor[i] = back.clone();
            } else {
                giveOrDrop(player, back.clone());
            }
        }
        inv.setArmorContents(armor);
        ItemStack hand = prior.hand();
        if (hand != null && !hand.getType().isAir()) {
            ItemStack cur = inv.getItemInMainHand();
            if (cur == null || cur.getType().isAir()) {
                inv.setItemInMainHand(hand.clone());
            } else {
                giveOrDrop(player, hand.clone());
            }
        }
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        for (ItemStack left : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /**
     * Death drops: strip tagged costume pieces, drop the stashed gear
     * instead — dying morphed keeps your real equipment.
     */
    static void fixDrops(List<ItemStack> drops, Prior prior, NamespacedKey mark) {
        if (drops == null) {
            return;
        }
        drops.removeIf(stack -> isMarked(stack, mark));
        if (prior == null) {
            return;
        }
        for (ItemStack piece : prior.armor()) {
            if (piece != null && !piece.getType().isAir()) {
                drops.add(piece.clone());
            }
        }
        if (prior.hand() != null && !prior.hand().getType().isAir()) {
            drops.add(prior.hand().clone());
        }
    }
}
