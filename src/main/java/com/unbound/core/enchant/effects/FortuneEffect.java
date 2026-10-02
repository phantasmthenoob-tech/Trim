package com.unbound.core.enchant.effects;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.unbound.core.config.UnboundConfig;
import com.unbound.core.enchant.ActionType;
import com.unbound.core.enchant.EnchantmentContext;
import com.unbound.core.enchant.EnchantmentEffect;
import com.unbound.core.math.DropMath;

import org.bukkit.event.block.BlockDropItemEvent;

/**
 * Fortune, Unbound-style.
 *
 * <p>Vanilla Fortune already multiplies ore drops for any breaking tool (the
 * loot table reads the held item). Unbound adds an independent bonus roll on
 * top: each qualifying drop stack (raw resources, gems, configured drops) has
 * a level-scaled chance to be duplicated once. Because it is purely additive,
 * vanilla drops are never duplicated twice, and items that would not qualify
 * (e.g. silk-touched ore blocks) are ignored.</p>
 */
public final class FortuneEffect implements EnchantmentEffect {

    /** Hard cap of duplicates per action, independent of level. */
    private static final int DUPLICATE_CAP = 8;

    private final Supplier<UnboundConfig> config;

    public FortuneEffect(Supplier<UnboundConfig> config) {
        this.config = config;
    }

    @Override
    public boolean applies(EnchantmentContext context) {
        if (context.action() != ActionType.BLOCK_BREAK || !config.get().fortune().enabled()) {
            return false;
        }
        return context.event()
                .filter(BlockDropItemEvent.class::isInstance)
                .isPresent();
    }

    @Override
    public void execute(EnchantmentContext context) {
        BlockDropItemEvent event = context.event()
                .filter(BlockDropItemEvent.class::isInstance)
                .map(BlockDropItemEvent.class::cast)
                .orElse(null);
        if (event == null || event.isCancelled() || event.getItems().isEmpty()) {
            return;
        }
        UnboundConfig.FortuneSettings settings = config.get().fortune();
        if (!DropMath.isFortuneBlock(event.getBlockState().getType(), settings.extraBlocks())) {
            return;
        }
        double chance = DropMath.extraBonusChance(context.level(), settings.extraChancePerLevel());
        if (chance <= 0) {
            return;
        }
        int duplicates = 0;
        for (org.bukkit.entity.Item dropEntity : event.getItems()) {
            if (duplicates >= DUPLICATE_CAP) {
                break;
            }
            org.bukkit.inventory.ItemStack stack = dropEntity.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!DropMath.isFortuneDrop(stack.getType(), settings.extraDrops())) {
                continue;
            }
            if (DropMath.rollDuplicate(ThreadLocalRandom.current(), chance)) {
                if (DuplicateSupport.duplicateOnce(dropEntity.getLocation(), stack, duplicates, DUPLICATE_CAP)) {
                    dropEntity.setItemStack(stack);
                    duplicates++;
                }
            }
        }
    }
}
