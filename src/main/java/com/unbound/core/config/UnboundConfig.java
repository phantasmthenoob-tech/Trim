package com.unbound.core.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.unbound.core.enchant.ProcessingGuard;

/**
 * Fully typed, immutable view of config.yml. Produced by {@link ConfigParser}
 * from a plain nested map, so it can be built in tests without a Bukkit
 * server.
 */
public final class UnboundConfig {

    /** Per-enchantment on/off switch and level cap. */
    public record EnchToggle(boolean enabled, int maxLevel) {
        public static final EnchToggle DEFAULT = new EnchToggle(true, 1);
    }

    public record InfinitySettings(boolean enabled, boolean consumablesEnabled, boolean preventConsumption,
                                   Set<String> excludedMaterials, boolean durabilityEnabled,
                                   boolean preventDurabilityLoss) {
    }

    public record UnbreakingSettings(boolean enabled, double extraSkipChancePerLevel) {
    }

    public record MendingSettings(boolean enabled, double durabilityPerXp, boolean scanHotbar) {
    }

    public record EfficiencySettings(boolean enabled, double scaling, double attackSpeedBonusPerLevel) {
    }

    public record QuickChargeSettings(boolean enabled, double useTimeReductionPerLevel,
                                      double maxCooldownReduction, int minimumCooldownTicks) {
    }

    public record MultishotSwordSettings(boolean enabled, double baseRadius, double radiusPerLevel,
                                         int maxTargets, double damageFraction, boolean knockback,
                                         double knockbackStrength, boolean skipOwnPets, boolean skipArmorStands) {
    }

    public record MultishotBowSettings(boolean enabled, int maxExtraArrowsPerLevel, int maxTotalExtraArrows,
                                       double spreadDegrees) {
    }

    public record MultishotSettings(boolean enabled, MultishotSwordSettings sword, MultishotBowSettings bow) {
    }

    public record SharpnessSettings(boolean enabled, double baseBonus, double bonusPerLevel) {
    }

    public record PowerSettings(boolean enabled, double projectileDamagePerLevel, Set<String> projectiles) {
    }

    public record PunchSettings(boolean enabled, boolean meleeEnabled, double meleeVelocityPerLevel,
                                Set<String> projectiles, double projectileVelocityPerLevel) {
    }

    public record FortuneSettings(boolean enabled, double extraChancePerLevel, Set<String> extraBlocks,
                                  Set<String> extraDrops) {
    }

    public record LootingSettings(boolean enabled, double extraChancePerLevel, boolean boostRareDrops) {
    }

    private final boolean enchantingTableEnabled;
    private final String enchantingDenyMessage;
    private final boolean debugEnabled;
    private final boolean debugAllowPlayerToggle;
    private final boolean debugShowTiming;
    private final ProcessingGuard.Limits limits;
    private final Map<String, EnchToggle> enchantToggles;
    private final InfinitySettings infinity;
    private final UnbreakingSettings unbreaking;
    private final MendingSettings mending;
    private final EfficiencySettings efficiency;
    private final QuickChargeSettings quickCharge;
    private final MultishotSettings multishot;
    private final SharpnessSettings sharpness;
    private final PowerSettings power;
    private final PunchSettings punch;
    private final FortuneSettings fortune;
    private final LootingSettings looting;

    UnboundConfig(boolean enchantingTableEnabled, String enchantingDenyMessage,
                  boolean debugEnabled, boolean debugAllowPlayerToggle, boolean debugShowTiming,
                  ProcessingGuard.Limits limits, Map<String, EnchToggle> enchantToggles,
                  InfinitySettings infinity, UnbreakingSettings unbreaking, MendingSettings mending,
                  EfficiencySettings efficiency, QuickChargeSettings quickCharge, MultishotSettings multishot,
                  SharpnessSettings sharpness, PowerSettings power, PunchSettings punch,
                  FortuneSettings fortune, LootingSettings looting) {
        this.enchantingTableEnabled = enchantingTableEnabled;
        this.enchantingDenyMessage = enchantingDenyMessage;
        this.debugEnabled = debugEnabled;
        this.debugAllowPlayerToggle = debugAllowPlayerToggle;
        this.debugShowTiming = debugShowTiming;
        this.limits = limits;
        this.enchantToggles = Map.copyOf(enchantToggles);
        this.infinity = infinity;
        this.unbreaking = unbreaking;
        this.mending = mending;
        this.efficiency = efficiency;
        this.quickCharge = quickCharge;
        this.multishot = multishot;
        this.sharpness = sharpness;
        this.power = power;
        this.punch = punch;
        this.fortune = fortune;
        this.looting = looting;
    }

    public boolean enchantingTableEnabled() {
        return enchantingTableEnabled;
    }

    public String enchantingDenyMessage() {
        return enchantingDenyMessage;
    }

    public boolean debugEnabled() {
        return debugEnabled;
    }

    public boolean debugAllowPlayerToggle() {
        return debugAllowPlayerToggle;
    }

    public boolean debugShowTiming() {
        return debugShowTiming;
    }

    public ProcessingGuard.Limits limits() {
        return limits;
    }

    public EnchToggle toggleFor(String enchantmentKey) {
        return enchantToggles.getOrDefault(enchantmentKey.toLowerCase(Locale.ROOT), EnchToggle.DEFAULT);
    }

    public Map<String, EnchToggle> enchantToggles() {
        return enchantToggles;
    }

    public InfinitySettings infinity() {
        return infinity;
    }

    public UnbreakingSettings unbreaking() {
        return unbreaking;
    }

    public MendingSettings mending() {
        return mending;
    }

    public EfficiencySettings efficiency() {
        return efficiency;
    }

    public QuickChargeSettings quickCharge() {
        return quickCharge;
    }

    public MultishotSettings multishot() {
        return multishot;
    }

    public SharpnessSettings sharpness() {
        return sharpness;
    }

    public PowerSettings power() {
        return power;
    }

    public PunchSettings punch() {
        return punch;
    }

    public FortuneSettings fortune() {
        return fortune;
    }

    public LootingSettings looting() {
        return looting;
    }

    public List<String> debugSummaryLines() {
        return List.of(
                "enchanting-table=" + (enchantingTableEnabled ? "enabled" : "blocked"),
                "enchantments=" + enchantToggles.size(),
                "limits=" + limits
        );
    }
}
