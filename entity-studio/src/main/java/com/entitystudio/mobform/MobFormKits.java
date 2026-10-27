package com.entitystudio.mobform;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loadouts handed to morphed players: archer quivers (plain and negative
 * tipped arrows only), drowned tridents, brute/vindicator axes, wither
 * skeleton swords, and the witch's potion stock with splash resupply.
 * Everything given is tracked so unmorphing removes exactly what the
 * plugin added — never the player's own gear.
 */
final class MobFormKits {

    /** Witch hotbar splash set: positives first, then negatives. */
    static final List<PotionType> WITCH_SPLASH = List.of(
            PotionType.HEALING, PotionType.REGENERATION,
            PotionType.SWIFTNESS, PotionType.STRENGTH,
            PotionType.STRONG_HARMING, PotionType.POISON,
            PotionType.SLOWNESS, PotionType.WEAKNESS);

    private static final PotionType[] NASTY_TIPS = {
            PotionType.POISON, PotionType.LONG_POISON,
            PotionType.WEAKNESS, PotionType.SLOWNESS };

    private MobFormKits() {
    }

    static ItemStack potionStack(Material mat, PotionType type, int amount) {
        ItemStack s = new ItemStack(mat, amount);
        ItemMeta m = s.getItemMeta();
        if (m instanceof PotionMeta pm) {
            pm.setBasePotionType(type);
            s.setItemMeta(pm);
        }
        return s;
    }

    static ItemStack tippedArrow(PotionType base, int amount) {
        return potionStack(Material.TIPPED_ARROW, base, amount);
    }

    /** Hunger-tipped arrow (custom effect on a mundane base). */
    static ItemStack hungerArrow(int amount) {
        ItemStack s = new ItemStack(Material.TIPPED_ARROW, amount);
        ItemMeta m = s.getItemMeta();
        if (m instanceof PotionMeta pm) {
            pm.setBasePotionType(PotionType.MUNDANE);
            pm.addCustomEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 0), true);
            s.setItemMeta(pm);
        }
        return s;
    }

    /** Full-inventory quiver: strays favor slowness, others mix in nasty tips. */
    static List<ItemStack> archerKit(String typeName, Random random) {
        List<ItemStack> kit = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            if (typeName.equals("STRAY")) {
                kit.add(i % 4 == 3 ? new ItemStack(Material.ARROW, 64)
                        : tippedArrow(PotionType.LONG_SLOWNESS, 64));
            } else if (random.nextInt(4) == 0) {
                if (random.nextInt(3) == 0) {
                    kit.add(hungerArrow(64));
                } else {
                    kit.add(tippedArrow(NASTY_TIPS[random.nextInt(NASTY_TIPS.length)], 64));
                }
            } else {
                kit.add(new ItemStack(Material.ARROW, 64));
            }
        }
        return kit;
    }

    static ItemStack tridentKit(Random random) {
        ItemStack s = new ItemStack(Material.TRIDENT);
        ItemMeta m = s.getItemMeta();
        m.addEnchant(Enchantment.LOYALTY, 3, true);
        if (random.nextBoolean()) {
            m.addEnchant(Enchantment.IMPALING, 5, true);
        }
        if (random.nextBoolean()) {
            m.addEnchant(Enchantment.UNBREAKING, 3, true);
        }
        if (random.nextInt(4) == 0) {
            m.addEnchant(Enchantment.MENDING, 1, true);
        }
        s.setItemMeta(m);
        return s;
    }

    static ItemStack axeKit(Material mat, boolean unbreakable, int sharp, int sharpChance,
            int unbChance, Random random) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (unbreakable) {
            m.setUnbreakable(true);
        }
        if (sharp > 0 && random.nextInt(100) < sharpChance) {
            m.addEnchant(Enchantment.SHARPNESS, sharp, true);
        }
        if (unbChance > 0 && random.nextInt(100) < unbChance) {
            m.addEnchant(Enchantment.UNBREAKING, 2, true);
        }
        s.setItemMeta(m);
        return s;
    }

    static List<ItemStack> witchDrinkables() {
        return List.of(
                potionStack(Material.POTION, PotionType.STRONG_HEALING, 3),
                potionStack(Material.POTION, PotionType.LONG_REGENERATION, 2),
                potionStack(Material.POTION, PotionType.LONG_SWIFTNESS, 2),
                potionStack(Material.POTION, PotionType.LONG_FIRE_RESISTANCE, 2),
                potionStack(Material.POTION, PotionType.LONG_NIGHT_VISION, 1),
                potionStack(Material.POTION, PotionType.WATER_BREATHING, 1));
    }

    /**
     * Applies the form loadout, filling only empty slots and the main hand.
     * Returns clones of everything placed, for exact removal on unmorph.
     */
    static List<ItemStack> applyLoadout(Player player, String typeName, Random random) {
        List<ItemStack> given = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        switch (typeName) {
            case "SKELETON", "STRAY", "PILLAGER", "PIGLIN" -> {
                ItemStack[] storage = inv.getStorageContents();
                int k = 0;
                List<ItemStack> kit = archerKit(typeName, random);
                for (int i = 0; i < storage.length && k < kit.size(); i++) {
                    if (storage[i] == null || storage[i].getType().isAir()) {
                        storage[i] = kit.get(k);
                        given.add(kit.get(k).clone());
                        k++;
                    }
                }
                inv.setStorageContents(storage);
            }
            case "DROWNED" -> giveWeapon(inv, tridentKit(random), given);
            case "PIGLIN_BRUTE" -> giveWeapon(inv,
                    axeKit(Material.GOLDEN_AXE, true, 3, 50, 0, random), given);
            case "VINDICATOR" -> giveWeapon(inv,
                    axeKit(Material.IRON_AXE, false, 2, 30, 20, random), given);
            case "WITHER_SKELETON" -> giveWeapon(inv, new ItemStack(Material.STONE_SWORD), given);
            case "WITCH" -> {
                ItemStack[] storage = inv.getStorageContents();
                for (ItemStack s : witchDrinkables()) {
                    for (int i = 9; i < storage.length; i++) {
                        if (storage[i] == null || storage[i].getType().isAir()) {
                            storage[i] = s.clone();
                            given.add(s.clone());
                            break;
                        }
                    }
                }
                inv.setStorageContents(storage);
                for (PotionType t : WITCH_SPLASH) {
                    if (hotbarHasSplash(inv, t)) {
                        continue;
                    }
                    ItemStack splash = potionStack(Material.SPLASH_POTION, t, 1);
                    for (int i = 0; i < 9; i++) {
                        ItemStack cur = inv.getItem(i);
                        if (cur == null || cur.getType().isAir()) {
                            inv.setItem(i, splash);
                            given.add(splash.clone());
                            break;
                        }
                    }
                }
            }
            default -> {
            }
        }
        return given;
    }

    private static void giveWeapon(PlayerInventory inv, ItemStack weapon, List<ItemStack> given) {
        ItemStack hand = inv.getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            inv.setItemInMainHand(weapon);
            given.add(weapon.clone());
            return;
        }
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] == null || storage[i].getType().isAir()) {
                storage[i] = weapon;
                inv.setStorageContents(storage);
                given.add(weapon.clone());
                return;
            }
        }
    }

    private static boolean hotbarHasSplash(PlayerInventory inv, PotionType type) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && s.getType() == Material.SPLASH_POTION
                    && s.getItemMeta() instanceof PotionMeta pm
                    && pm.getBasePotionType() == type) {
                return true;
            }
        }
        return false;
    }

    /** Tops up missing witch splash potions when there is room. */
    static void resupplySplash(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean full = true;
        for (ItemStack s : storage) {
            if (s == null || s.getType().isAir()) {
                full = false;
                break;
            }
        }
        if (full) {
            return;
        }
        for (PotionType t : WITCH_SPLASH) {
            if (hotbarHasSplash(inv, t)) {
                continue;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack cur = inv.getItem(i);
                if (cur == null || cur.getType().isAir()) {
                    inv.setItem(i, potionStack(Material.SPLASH_POTION, t, 1));
                    break;
                }
            }
        }
    }

    /** Removes exactly what the plugin gave (matching stacks only). */
    static void removeGiven(Player player, List<ItemStack> given) {
        if (given == null || given.isEmpty()) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (ItemStack g : given) {
            int remaining = g.getAmount();
            for (int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack s = storage[i];
                if (s != null && s.isSimilar(g)) {
                    int take = Math.min(remaining, s.getAmount());
                    if (s.getAmount() == take) {
                        storage[i] = null;
                    } else {
                        s.setAmount(s.getAmount() - take);
                    }
                    remaining -= take;
                }
            }
        }
        inv.setStorageContents(storage);
        ItemStack hand = inv.getItemInMainHand();
        for (ItemStack g : given) {
            if (hand != null && hand.isSimilar(g)) {
                int remaining = g.getAmount();
                int take = Math.min(remaining, hand.getAmount());
                if (hand.getAmount() == take) {
                    inv.setItemInMainHand(null);
                    hand = null;
                } else {
                    hand.setAmount(hand.getAmount() - take);
                }
            }
        }
    }
}
