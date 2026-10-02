package com.unbound.core.enchant.effects;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.DropMath;

import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Looting, Unbound-style.
 *
 * <p>Vanilla Looting already boosts mob drops for any killing weapon (the
 * loot table reads the held item). Unbound adds an independent bonus roll:
 * each drop stack has a level-scaled chance to be duplicated once. Rare drops
 * (enchanted gear, totems, stars...) are only boosted when
 * {@code boost-rare-drops} is enabled.</p>
 */
public final class LootingEffect implements EnchantmentEffect {

    private static final int DUPLICATE_CAP = 8;
    private static final Set<String> RARE_DROPS = Set.of(
            "enchanted_book", "totem_of_undying", "nether_star", "wither_skeleton_skull",
            "elytra", "dragon_egg", "dragon_head", "piglin_head", "player_head",
            "creeper_head", "zombie_head", "skeleton_skull", "trident", "heart_of_the_sea");

    private final Supplier<UnboundConfig> config;

    public LootingEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (context.action() != ActionType.ENTITY_DEATH || !config.get().looting().enabled()) {
            return false;
        }
        return context.event()
                .filter(EntityDeathEvent.class::isInstance)
                .isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        EntityDeathEvent event = context.event()
                .filter(EntityDeathEvent.class::isInstance)
                .map(EntityDeathEvent.class::cast)
                .orElse(null);
        if (event == null) {
            return;
        }
        UnboundConfig.LootingSettings settings = config.get().looting();
        double chance = DropMath.extraBonusChance(context.level(), settings.extraChancePerLevel());
        if (chance <= 0) {
            return;
        }
        List<ItemStack> drops = event.getDrops();
        if (drops == null || drops.isEmpty()) {
            return;
        }
        @Nullable org.bukkit.Location location = context.target()
                .map(target -> target.getLocation())
                .orElse(null);
        int duplicates = 0;
        for (ItemStack stack : drops) {
            if (duplicates >= DUPLICATE_CAP) {
                break;
            }
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!settings.boostRareDrops() && isRareDrop(stack)) {
                continue;
            }
            if (DropMath.rollDuplicate(ThreadLocalRandom.current(), chance)) {
                if (location != null
                        && DuplicateSupport.duplicateOnce(location, stack, duplicates, DUPLICATE_CAP)) {
                    duplicates++;
                }
            }
        }
    }

    static boolean isRareDrop(ItemStack stack) {
        return isRareMaterial(stack.getType())
                || stack.getEnchantments() != null && !stack.getEnchantments().isEmpty();
    }

    /** Pure, material-only rarity check (unit-testable without a server). */
    static boolean isRareMaterial(org.bukkit.Material material) {
        return RARE_DROPS.contains(material.name().toLowerCase(Locale.ROOT));
    }
}
