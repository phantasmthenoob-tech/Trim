package com.entitystudio.mobform;

import com.entitystudio.EntityStudioPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTables;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Become the mob" system. A morphed player keeps their own client-side body
 * (Paper cannot change that without client mods — see docs/morph.md) but
 * genuinely gains the mob's health, damage, speed, size, flight, fire
 * immunity, signature abilities on F / Shift+F, and death drops.
 */
public class MobFormService implements Listener {

    /** Applied form plus everything needed to restore the player. */
    public record ActiveForm(EntityType type,
            double prevMaxHealth, double prevHealth,
            double prevSpeed, double prevDamage, double prevScale, double prevFlySpeed,
            boolean prevAllowFlight, boolean prevFlying, boolean addedFireResist) {
    }

    private final EntityStudioPlugin plugin;
    private final Map<UUID, ActiveForm> forms = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveForm> pendingRestore = new ConcurrentHashMap<>();
    private final FormCooldowns cooldowns = new FormCooldowns();
    private final Map<UUID, List<ItemStack>> formKits = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> formTasks = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public MobFormService(EntityStudioPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isMorphed(UUID player) {
        return forms.containsKey(player);
    }

    public EntityType formType(UUID player) {
        ActiveForm f = forms.get(player);
        return f == null ? null : f.type();
    }

    /** Morphs into a living entity type. False + feedback when not allowed. */
    public boolean morph(Player player, EntityType type) {
        if (!plugin.permitted(player, com.entitystudio.permission.StudioPermissions.MORPH)) {
            plugin.send(player, plugin.msg("no-permission"));
            return false;
        }
        if (type == null || !type.isAlive() || type == EntityType.PLAYER
                || type == EntityType.ARMOR_STAND) {
            plugin.send(player, "<red>You cannot become that.");
            return false;
        }
        unmorph(player, true);
        FormStats.Stats s = FormStats.forType(type.name());
        double prevMax = baseOf(player, Attribute.MAX_HEALTH, 20.0);
        double prevHealth = Math.min(player.getHealth(), prevMax);
        double prevSpeed = baseOf(player, Attribute.MOVEMENT_SPEED, 0.1);
        double prevDamage = baseOf(player, Attribute.ATTACK_DAMAGE, 1.0);
        double prevScale = baseOf(player, Attribute.SCALE, 1.0);
        double prevFly = baseOf(player, Attribute.FLYING_SPEED, 0.1);
        boolean prevAllow = player.getAllowFlight();
        boolean prevFlying = player.isFlying();
        setBase(player, Attribute.MAX_HEALTH, s.health());
        double ratio = prevMax <= 0 ? 1.0 : prevHealth / prevMax;
        player.setHealth(Math.max(1.0, Math.min(s.health(), s.health() * ratio)));
        if (s.damage() > 0) {
            setBase(player, Attribute.ATTACK_DAMAGE, s.damage());
        }
        if (s.speed() > 0) {
            setBase(player, Attribute.MOVEMENT_SPEED, s.speed());
        }
        setBase(player, Attribute.SCALE, s.scale());
        if (s.flySpeed() > 0) {
            setBase(player, Attribute.FLYING_SPEED, s.flySpeed());
        }
        boolean addedResist = false;
        if (s.fireImmune()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                    PotionEffect.INFINITE_DURATION, 0, false, false, false));
            addedResist = true;
        }
        if (s.fly()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        forms.put(player.getUniqueId(), new ActiveForm(type, prevMax, prevHealth,
                prevSpeed, prevDamage, prevScale, prevFly, prevAllow, prevFlying,
                addedResist));
        List<ItemStack> given = MobFormKits.applyLoadout(player, type.name(), random);
        if (!given.isEmpty()) {
            formKits.put(player.getUniqueId(), given);
        }
        if (type == EntityType.WITCH) {
            UUID id = player.getUniqueId();
            formTasks.put(id, Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline() || formType(id) != EntityType.WITCH) {
                    return;
                }
                MobFormKits.resupplySplash(p);
            }, 120L, 120L));
        }
        try {
            Sound ambient = Sound.valueOf(type.name() + "_AMBIENT");
            player.getWorld().playSound(player.getLocation(), ambient, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {
        }
        String pretty = type.name().toLowerCase().replace('_', ' ');
        plugin.send(player, "<gold>You are now a <white>" + pretty + "<gold>!"
                + (FormStats.isDetailed(type.name()) ? "" : " <gray>(generic form)"));
        plugin.send(player, "<yellow>" + s.primaryDesc()
                + (s.secondaryDesc() == null ? "" : " · <yellow>" + s.secondaryDesc()));
        plugin.getLogger().info(player.getName() + " morphed into " + type.name());
        return true;
    }

    /** Restores the player. Silent when nothing was active. */
    public boolean unmorph(Player player, boolean silent) {
        ActiveForm f = forms.remove(player.getUniqueId());
        cooldowns.clear(player.getUniqueId());
        if (f == null) {
            pendingRestore.remove(player.getUniqueId());
            return false;
        }
        takeKit(player);
        cancelFormTask(player.getUniqueId());
        restore(player, f);
        if (!player.isOnGround()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                    100, 0, false, false, true));
        }
        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
        if (!silent) {
            plugin.send(player, "<gray>Back to yourself.");
        }
        plugin.getLogger().info(player.getName() + " unmorphed from " + f.type().name());
        return true;
    }

    public boolean unmorph(Player player) {
        return unmorph(player, false);
    }

    private void restore(Player player, ActiveForm f) {
        setBase(player, Attribute.MAX_HEALTH, f.prevMaxHealth());
        player.setHealth(Math.max(1.0, Math.min(f.prevHealth(), f.prevMaxHealth())));
        setBase(player, Attribute.MOVEMENT_SPEED, f.prevSpeed());
        setBase(player, Attribute.ATTACK_DAMAGE, f.prevDamage());
        setBase(player, Attribute.SCALE, f.prevScale());
        setBase(player, Attribute.FLYING_SPEED, f.prevFlySpeed());
        player.setAllowFlight(f.prevAllowFlight());
        try {
            player.setFlying(f.prevFlying() && f.prevAllowFlight());
        } catch (IllegalArgumentException ignored) {
        }
        if (f.addedFireResist()) {
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
        }
    }

    private double baseOf(Player player, Attribute attr, double fallback) {
        try {
            AttributeInstance inst = player.getAttribute(attr);
            return inst == null ? fallback : inst.getBaseValue();
        } catch (Exception e) {
            return fallback;
        }
    }

    private void setBase(Player player, Attribute attr, double value) {
        try {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) {
                inst.setBaseValue(value);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not set " + attr + ": " + e.getMessage());
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ActiveForm f = forms.get(player.getUniqueId());
        if (f == null) {
            return;
        }
        event.setCancelled(true);
        boolean secondary = player.isSneaking();
        FormStats.Stats s = FormStats.forType(f.type().name());
        long cd = secondary ? s.secondaryCooldownMs() : s.primaryCooldownMs();
        String key = f.type().name() + (secondary ? ":secondary" : ":primary");
        if (cd > 0 && !cooldowns.tryUse(player.getUniqueId(), key, cd)) {
            long left = cooldowns.remainingMs(player.getUniqueId(), key);
            player.sendActionBar(plugin.mm().deserialize(
                    "<gray>Recharging… <white>" + String.format("%.1fs", left / 1000.0)));
            return;
        }
        MobFormAbilities.execute(plugin, player, f.type(), secondary);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ActiveForm f = forms.remove(player.getUniqueId());
        cooldowns.clear(player.getUniqueId());
        formKits.remove(player.getUniqueId());
        cancelFormTask(player.getUniqueId());
        if (f == null) {
            return;
        }
        // Death already resets attributes, effects and flight; drops are the
        // one thing we add: the mob's own loot table, like the mob died.
        for (ItemStack loot : mobLoot(f.type(), player)) {
            try {
                event.getDrops().add(loot);
            } catch (Exception ignored) {
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        pendingRestore.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ActiveForm f = forms.remove(player.getUniqueId());
        cooldowns.clear(player.getUniqueId());
        formKits.remove(player.getUniqueId());
        cancelFormTask(player.getUniqueId());
        if (f == null) {
            return;
        }
        try {
            restore(player, f);
        } catch (Exception e) {
            pendingRestore.put(player.getUniqueId(), f);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ActiveForm f = pendingRestore.remove(event.getPlayer().getUniqueId());
        if (f != null) {
            try {
                restore(event.getPlayer(), f);
            } catch (Exception ignored) {
            }
        }
    }

    private List<ItemStack> mobLoot(EntityType type, Player player) {
        List<ItemStack> out = new ArrayList<>();
        LootTables table;
        try {
            table = LootTables.valueOf(type.name());
        } catch (IllegalArgumentException e) {
            return out;
        }
        try {
            LootContext.Builder builder = new LootContext.Builder(player.getLocation())
                    .lootedEntity(player);
            if (player.getKiller() instanceof HumanEntity h) {
                builder.killer(h);
            }
            out.addAll(table.getLootTable().populateLoot(random, builder.build()));
        } catch (Exception e) {
            plugin.getLogger().warning("Mob loot failed for " + type.name() + ": " + e);
        }
        return out;
    }

    private void takeKit(Player player) {
        List<ItemStack> given = formKits.remove(player.getUniqueId());
        MobFormKits.removeGiven(player, given);
    }

    private void cancelFormTask(UUID id) {
        BukkitTask t = formTasks.remove(id);
        if (t != null) {
            t.cancel();
        }
    }

    /** Husk hunger: morphed-husk melee hits inflict hunger on living victims. */
    @EventHandler(ignoreCancelled = true)
    public void onHuskHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (formType(player.getUniqueId()) != EntityType.HUSK) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity victim) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER,
                    140, 0, false, true, true));
        }
    }

    /** Restores online players on shutdown; offline state resets naturally. */
    public void shutdown() {
        for (Map.Entry<UUID, ActiveForm> e : forms.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && p.isOnline()) {
                try {
                    restore(p, e.getValue());
                } catch (Exception ignored) {
                }
            }
        }
        for (BukkitTask t : formTasks.values()) {
            t.cancel();
        }
        formTasks.clear();
        formKits.clear();
        forms.clear();
        pendingRestore.clear();
    }

    /** For tests and admin: how many active forms. */
    int formCount() {
        return forms.size();
    }
}
