package com.entitystudio.mobform;

import com.entitystudio.EntityStudioPlugin;
import com.entitystudio.behavior.AllegianceService;
import com.entitystudio.behavior.ManagedRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Vex;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.entity.WitherSkull;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Signature abilities for morphed players. F is the primary, Shift+F the
 * secondary (only evokers and sheep have one). Everything runs on the
 * server thread from the swap-hand event. Cooldowns live in
 * {@link MobFormService}; damage numbers and timings are vanilla-tuned
 * approximations, documented in docs/morph.md.
 */
final class MobFormAbilities {

    private MobFormAbilities() {
    }

    static void execute(EntityStudioPlugin plugin, Player player, EntityType type,
            boolean secondary) {
        String name = type.name();
        if (!secondary) {
            switch (name) {
                case "EVOKER" -> summonVexes(plugin, player);
                case "BLAZE" -> blazeBurst(plugin, player);
                case "SHEEP" -> graze(plugin, player);
                case "CREEPER" -> detonate(player);
                case "ENDERMAN" -> teleport(plugin, player);
                case "SKELETON", "STRAY", "PILLAGER", "PIGLIN" -> fireAmmoArrow(plugin, player);
                case "DROWNED" -> tridentThrow(plugin, player);
                case "WITCH" -> witchThrow(plugin, player);
                case "ZOMBIE" -> surge(plugin, player);
                case "SPIDER", "CAVE_SPIDER" -> pounce(plugin, player);
                case "SLIME" -> slam(plugin, player);
                case "GHAST" -> shootBig(plugin, player, Fireball.class, Sound.ENTITY_GHAST_SHOOT, false);
                case "WITHER" -> shootBig(plugin, player, WitherSkull.class, Sound.ENTITY_WITHER_SHOOT, false);
                case "ENDER_DRAGON" -> shootBig(plugin, player, DragonFireball.class,
                        Sound.ENTITY_ENDER_DRAGON_SHOOT, false);
                default -> dash(plugin, player);
            }
            return;
        }
        switch (name) {
            case "EVOKER" -> fangLine(plugin, player);
            case "SHEEP" -> lamb(plugin, player);
            case "WITHER" -> shootBig(plugin, player, WitherSkull.class,
                    Sound.ENTITY_WITHER_SHOOT, true);
            default -> plugin.send(player, "<gray>This form has no second ability.");
        }
    }

    private static Vector facing(Player player) {
        return player.getEyeLocation().getDirection().normalize();
    }

    private static void summonVexes(EntityStudioPlugin plugin, Player player) {
        Location base = player.getLocation();
        player.getWorld().spawnParticle(Particle.PORTAL, base.clone().add(0, 1, 0),
                40, 1, 1, 1, 0.5);
        int made = 0;
        for (int i = 0; i < 3; i++) {
            Location at = safeMinionSpot(base);
            try {
                Vex vex = player.getWorld().spawn(at, Vex.class);
                Entity seen = player.getTargetEntity(30);
                if (seen instanceof LivingEntity l
                        && !l.getUniqueId().equals(player.getUniqueId())
                        && !(l instanceof Vex)) {
                    vex.setTarget(l);
                }
                ManagedRecord rec = plugin.allegiance().getOrCreate(vex);
                rec.owner(player.getUniqueId());
                AllegianceService.applyProfile(vex, ManagedRecord.Profile.GUARD, rec);
                made++;
            } catch (Exception e) {
                plugin.getLogger().warning("Vex summon failed: " + e.getMessage());
            }
        }
        player.getWorld().playSound(base, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f);
        plugin.send(player, made > 0 ? "<aqua>Vexes rise to defend you."
                : "<red>The summoning fizzles.");
    }

    private static void fangLine(EntityStudioPlugin plugin, Player player) {
        Vector dir = facing(player);
        dir.setY(0).normalize();
        Location origin = player.getLocation().clone().add(dir.clone().multiply(2));
        origin.setY(player.getLocation().getY());
        for (int i = 0; i < 16; i++) {
            Location at = snapToGround(origin.clone().add(dir.clone().multiply(i)));
            try {
                EvokerFangs fangs = player.getWorld().spawn(at, EvokerFangs.class);
                fangs.setOwner(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Fang summon failed: " + e.getMessage());
                break;
            }
        }
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.0f, 1.0f);
    }

    /** Air pocket with solid ground so summons never suffocate in walls. */
    private static Location safeMinionSpot(Location base) {
        for (int i = 0; i < 8; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = 1.5 + Math.random() * 1.5;
            Location at = base.clone().add(Math.cos(a) * r, 1, Math.sin(a) * r);
            if (at.getBlock().getType().isAir()
                    && at.clone().add(0, 1, 0).getBlock().getType().isAir()
                    && at.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                return at;
            }
        }
        return base.clone().add(0, 1, 0);
    }

    /** Drops a fang column onto the ground below, so fangs erupt from earth. */
    private static Location snapToGround(Location at) {
        Location probe = at.clone().add(0, 1, 0);
        for (int d = 0; d < 10; d++) {
            if (probe.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
                Location out = probe.getBlock().getLocation().add(0.5, 1.01, 0.5);
                out.setYaw(at.getYaw());
                return out;
            }
            probe.add(0, -1, 0);
        }
        return at;
    }

    private static void blazeBurst(EntityStudioPlugin plugin, Player player) {
        for (int i = 0; i < 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                Location eye = player.getEyeLocation();
                try {
                    SmallFireball ball = player.getWorld().spawn(
                            eye.clone().add(eye.getDirection().multiply(1.5)), SmallFireball.class);
                    ball.setDirection(eye.getDirection());
                    ball.setShooter(player);
                } catch (Exception e) {
                    plugin.getLogger().warning("Fireball failed: " + e.getMessage());
                }
                player.getWorld().playSound(player.getLocation(),
                        Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                        player.getEyeLocation(), 6, 0.3, 0.3, 0.3, 0.02);
            }, i * 5L);
        }
    }

    private static void shootBig(EntityStudioPlugin plugin, Player player,
            Class<? extends Fireball> cls, Sound sound, boolean charged) {
        Location eye = player.getEyeLocation();
        try {
            Fireball ball = player.getWorld().spawn(eye.clone()
                    .add(eye.getDirection().multiply(2)), cls);
            ball.setDirection(eye.getDirection());
            ball.setShooter(player);
            if (ball instanceof WitherSkull skull) {
                skull.setCharged(charged);
            }
            if (ball instanceof org.bukkit.entity.Explosive ex) {
                ex.setYield(1.0f);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Fireball failed: " + e.getMessage());
            return;
        }
        player.getWorld().playSound(player.getLocation(), sound, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.LARGE_SMOKE, eye, 12, 0.4, 0.4, 0.4, 0.03);
    }

    private static void graze(EntityStudioPlugin plugin, Player player) {
        Location feet = player.getLocation();
        Block[] candidates = {
                feet.getBlock(), feet.clone().add(0, -1, 0).getBlock(),
        };
        for (Block b : candidates) {
            Material m = b.getType();
            if (m == Material.GRASS_BLOCK) {
                b.setType(Material.DIRT);
                grazeEffects(plugin, player);
                return;
            }
            if (m == Material.SHORT_GRASS || m == Material.TALL_GRASS
                    || m == Material.FERN || m == Material.LARGE_FERN) {
                b.setType(Material.AIR);
                grazeEffects(plugin, player);
                return;
            }
        }
        plugin.send(player, "<gray>No grass in reach to munch.");
    }

    private static void grazeEffects(EntityStudioPlugin plugin, Player player) {
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                player.getLocation().clone().add(0, 1.5, 0), 6, 0.4, 0.4, 0.4, 0.0);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 2));
        plugin.send(player, "<green>Munch munch.");
    }

    private static void lamb(EntityStudioPlugin plugin, Player player) {
        try {
            Sheep baby = player.getWorld().spawn(
                    player.getLocation().clone().add(0, 0.5, 0), Sheep.class);
            baby.setBaby();
        } catch (Exception e) {
            plugin.getLogger().warning("Lamb spawn failed: " + e.getMessage());
            plugin.send(player, "<red>The lamb never arrives.");
            return;
        }
        player.getWorld().spawnParticle(Particle.HEART,
                player.getLocation().clone().add(0, 1.5, 0), 8, 0.5, 0.5, 0.5, 0.0);
        plugin.send(player, "<aqua>A lamb joins the flock!");
    }

    private static void detonate(Player player) {
        player.getWorld().createExplosion(player.getLocation(), 3.0f, false, true, player);
    }

    private static void teleport(EntityStudioPlugin plugin, Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location dest = eye.clone().add(dir.multiply(16));
        Location safe = null;
        for (int dy = 0; dy < 12; dy++) {
            Location at = dest.clone().add(0, -dy, 0);
            Block feet = at.getBlock();
            Block head = at.clone().add(0, 1, 0).getBlock();
            Block legs = at.clone().add(0, 2, 0).getBlock();
            if (feet.getType().isSolid() && head.getType().isAir() && legs.getType().isAir()) {
                safe = at.clone().add(0.5, 1.01, 0.5);
                safe.setYaw(eye.getYaw());
                safe.setPitch(eye.getPitch());
                break;
            }
        }
        if (safe == null) {
            plugin.send(player, "<gray>No safe landing in reach.");
            return;
        }
        Location from = player.getLocation().clone();
        player.teleport(safe);
        player.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.3);
        player.getWorld().spawnParticle(Particle.PORTAL, safe, 30, 0.5, 0.8, 0.5, 0.3);
        player.damage(5.0);
        player.getWorld().playSound(safe, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        plugin.send(player, "<dark_purple>Vwoop.");
    }

    private static void shootArrow(EntityStudioPlugin plugin, Player player) {
        try {
            player.launchProjectile(Arrow.class, facing(player).multiply(2.5));
        } catch (Exception e) {
            plugin.getLogger().warning("Arrow failed: " + e.getMessage());
            return;
        }
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
    }

    private static void surge(EntityStudioPlugin plugin, Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1,
                false, true, true));
        player.getWorld().spawnParticle(Particle.WITCH,
                player.getLocation().clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_WOLF_GROWL, 1.0f, 0.6f);
        plugin.send(player, "<red>You feel a surge of strength!");
    }

    private static void pounce(EntityStudioPlugin plugin, Player player) {
        Vector v = facing(player).multiply(1.3);
        v.setY(0.9);
        player.setVelocity(v);
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_SLIME_SQUISH_SMALL, 1.0f, 1.4f);
        player.getWorld().spawnParticle(Particle.POOF, player.getLocation(), 10, 0.4, 0.2, 0.4, 0.02);
    }

    private static void slam(EntityStudioPlugin plugin, Player player) {
        Location center = player.getLocation();
        for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, 3.5)) {
            if (e.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            try {
                e.damage(4.0, player);
                Vector push = e.getLocation().toVector().subtract(center.toVector())
                        .normalize().multiply(1.2);
                push.setY(0.8);
                e.setVelocity(push);
            } catch (Exception ignored) {
            }
        }
        player.getWorld().playSound(center, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.7f);
        center.getWorld().spawnParticle(Particle.POOF, center.clone().add(0, 0.5, 0),
                20, 1.2, 0.5, 1.2, 0.03);
        Vector hop = player.getVelocity();
        hop.setY(0.5);
        player.setVelocity(hop);
    }

    private static void dash(EntityStudioPlugin plugin, Player player) {
        Vector v = facing(player).multiply(1.4);
        v.setY(Math.max(v.getY(), 0.3));
        player.setVelocity(v);
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_SLIME_SQUISH_SMALL, 0.6f, 1.8f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.3, 0.2, 0.3, 0.02);
    }

    /** Fires one arrow from the archer's quiver (plain or tipped). */
    private static void fireAmmoArrow(EntityStudioPlugin plugin, Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        int found = -1;
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] != null && (storage[i].getType() == Material.ARROW
                    || storage[i].getType() == Material.TIPPED_ARROW)) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            plugin.send(player, "<gray>No arrows left in your quiver.");
            return;
        }
        ItemStack ammo = storage[found].clone();
        ammo.setAmount(1);
        if (storage[found].getAmount() <= 1) {
            storage[found] = null;
        } else {
            storage[found].setAmount(storage[found].getAmount() - 1);
        }
        inv.setStorageContents(storage);
        Location eye = player.getEyeLocation();
        try {
            Arrow arrow = player.launchProjectile(
                    Arrow.class, eye.getDirection().multiply(2.5));
            arrow.setItemStack(ammo);
            arrow.setCritical(false);
        } catch (Exception e) {
            plugin.getLogger().warning("Mob arrow failed: " + e.getMessage());
            player.getInventory().addItem(ammo);
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
    }

    /** Throws one loyalty trident, consuming it from the loadout. */
    private static void tridentThrow(EntityStudioPlugin plugin, Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack use = null;
        ItemStack held = inv.getItemInMainHand();
        if (held != null && held.getType() == Material.TRIDENT) {
            use = held.clone();
            use.setAmount(1);
            if (held.getAmount() <= 1) {
                inv.setItemInMainHand(null);
            } else {
                held.setAmount(held.getAmount() - 1);
            }
        } else {
            ItemStack[] storage = inv.getStorageContents();
            for (int i = 0; i < storage.length; i++) {
                if (storage[i] != null && storage[i].getType() == Material.TRIDENT) {
                    use = storage[i].clone();
                    use.setAmount(1);
                    if (storage[i].getAmount() <= 1) {
                        storage[i] = null;
                    } else {
                        storage[i].setAmount(storage[i].getAmount() - 1);
                    }
                    inv.setStorageContents(storage);
                    break;
                }
            }
        }
        if (use == null) {
            plugin.send(player, "<gray>No trident to throw.");
            return;
        }
        int loyalty = 0;
        ItemMeta meta = use.getItemMeta();
        if (meta != null) {
            loyalty = meta.getEnchantLevel(Enchantment.LOYALTY);
        }
        Location eye = player.getEyeLocation();
        try {
            Trident thrown = player.getWorld().spawn(
                    eye.clone().add(eye.getDirection().multiply(1.5)), Trident.class);
            thrown.setVelocity(eye.getDirection().multiply(2.2));
            thrown.setLoyaltyLevel(loyalty);
            thrown.setShooter(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Trident throw failed: " + e.getMessage());
            player.getInventory().addItem(use);
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.0f);
    }

    private static final PotionType[] HEXES = {
            PotionType.STRONG_HARMING, PotionType.POISON,
            PotionType.SLOWNESS, PotionType.WEAKNESS };

    /** Throws one of the witch's negative splashes, preferring real ammo. */
    private static void witchThrow(EntityStudioPlugin plugin, Player player) {
        PotionType hex = HEXES[(int) (Math.random() * HEXES.length)];
        ItemStack conjured = null;
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack s = storage[i];
            if (s != null && s.getType() == Material.SPLASH_POTION
                    && s.getItemMeta() instanceof PotionMeta pm
                    && pm.getBasePotionType() == hex) {
                conjured = s.clone();
                conjured.setAmount(1);
                if (s.getAmount() <= 1) {
                    storage[i] = null;
                } else {
                    s.setAmount(s.getAmount() - 1);
                }
                inv.setStorageContents(storage);
                break;
            }
        }
        if (conjured == null) {
            conjured = MobFormKits.potionStack(Material.SPLASH_POTION, hex, 1);
        }
        Location eye = player.getEyeLocation();
        try {
            ThrownPotion thrown = player.getWorld().spawn(
                    eye.clone().add(eye.getDirection()), ThrownPotion.class);
            thrown.setItem(conjured);
            thrown.setShooter(player);
            thrown.setVelocity(eye.getDirection().multiply(1.4)
                    .add(new org.bukkit.util.Vector(0, 0.2, 0)));
        } catch (Exception e) {
            plugin.getLogger().warning("Witch throw failed: " + e.getMessage());
            return;
        }
        player.getWorld().playSound(
                player.getLocation(), Sound.ENTITY_SPLASH_POTION_THROW, 1.0f, 1.0f);
    }
}
