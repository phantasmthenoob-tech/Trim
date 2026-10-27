package com.entitystudio.mobform;

import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Loadout table coverage: every kitted form has stats, and the witch
 * splash set stays exactly four self-buffs plus four throwable hexes.
 * (ItemStack assembly itself needs a live server; see docs/morph.md.) */
class FormLoadoutTest {

    @Test
    void kittedFormsHaveDetailedEntries() {
        for (String type : new String[] { "SKELETON", "STRAY", "PILLAGER", "PIGLIN",
                "PIGLIN_BRUTE", "HUSK", "DROWNED", "WITHER_SKELETON", "VINDICATOR",
                "WITCH" }) {
            assertTrue(FormStats.isDetailed(type), type);
            assertTrue(FormStats.forType(type).primaryCooldownMs() > 0, type);
            assertNotNull(FormStats.forType(type).primaryDesc(), type);
        }
    }

    @Test
    void formerlyDuplicatedKeysResolveToFirstEntry() {
        // Regression: duplicate Map.ofEntries keys used to crash class init.
        assertTrue(FormStats.forType("PIGLIN").primaryDesc().contains("arrow"));
        assertEquals(20.0, FormStats.forType("WITHER_SKELETON").health());
    }

    @Test
    void witherSkullsAreNormalThenBlue() {
        FormStats.Stats wither = FormStats.forType("WITHER");
        assertEquals(2000L, wither.primaryCooldownMs());
        assertEquals(3000L, wither.secondaryCooldownMs());
        assertNotNull(wither.secondaryDesc());
        assertTrue(wither.secondaryDesc().contains("BLUE"));
    }

    @Test
    void witchSplashSetIsFourBuffsPlusFourHexes() {
        List<PotionType> splash = MobFormKits.WITCH_SPLASH;
        assertEquals(8, splash.size());
        assertEquals(List.of(PotionType.HEALING, PotionType.REGENERATION,
                PotionType.SWIFTNESS, PotionType.STRENGTH), splash.subList(0, 4));
        assertEquals(List.of(PotionType.STRONG_HARMING, PotionType.POISON,
                PotionType.SLOWNESS, PotionType.WEAKNESS), splash.subList(4, 8));
    }
}
