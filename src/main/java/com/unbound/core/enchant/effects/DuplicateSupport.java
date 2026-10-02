package com.unbound.core.enchant.effects;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

/**
 * Shared support for the additive drop bonuses (Fortune, Looting):
 * duplicates a qualifying drop stack exactly once, respecting max stack size
 * (in-place doubling when possible, an extra drop entity otherwise) and an
 * overall per-action duplicate cap.
 */
final class DuplicateSupport {

    private DuplicateSupport() {
    }

    /**
     * Attempts to duplicate {@code stack} once.
     *
     * @param duplicatesUsed duplicates already consumed for this action
     * @param duplicateCap   maximum duplicates for this action
     * @return true when a duplicate was created (caller increments its counter)
     */
    static boolean duplicateOnce(Location location, ItemStack stack, int duplicatesUsed, int duplicateCap) {
        if (duplicatesUsed >= duplicateCap || stack == null || stack.getAmount() <= 0 || stack.getType().isAir()) {
            return false;
        }
        int maxStack = Math.max(1, stack.getMaxStackSize());
        int amount = stack.getAmount();
        if (amount * 2 <= maxStack) {
            stack.setAmount(amount * 2);
            return true;
        }
        // Cannot double in place: add as much as fits, spill the rest as a
        // separate drop entity so nothing is lost or over-stacked.
        int fits = Math.min(amount, maxStack - amount);
        if (fits > 0) {
            stack.setAmount(amount + fits);
            int spill = amount - fits;
            if (spill > 0) {
                ItemStack extra = stack.clone();
                extra.setAmount(spill);
                World world = location.getWorld();
                if (world != null) {
                    world.dropItemNaturally(location, extra);
                }
            }
            return true;
        }
        return false;
    }

    /** Mutable ItemStack list from the event, defensively non-null. */
    static List<ItemStack> stacksOf(List<ItemStack> stacks) {
        return stacks == null ? List.of() : stacks;
    }
}
