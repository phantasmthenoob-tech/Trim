package com.entitystudio.mobform;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MobFormMenuTest {

    @Test
    void detailedTypesAreSortedAndRealEntities() {
        List<String> types = FormStats.detailedTypes();
        assertFalse(types.isEmpty());
        List<String> sorted = types.stream().sorted().toList();
        assertEquals(sorted, types);
        for (String type : types) {
            // Menu clicks map straight back to EntityType; must never throw.
            assertNotNull(EntityType.valueOf(type), type);
        }
    }

    @Test
    void iconsAreSpawnEggsWithIconicFallbacks() {
        assertEquals(Material.SKELETON_SPAWN_EGG, MorphMenu.iconFor("SKELETON"));
        assertEquals(Material.EVOKER_SPAWN_EGG, MorphMenu.iconFor("EVOKER"));
        assertEquals(Material.NETHER_STAR, MorphMenu.iconFor("WITHER"));
        assertEquals(Material.DRAGON_EGG, MorphMenu.iconFor("ENDER_DRAGON"));
        assertEquals(Material.EGG, MorphMenu.iconFor("NOT_A_MOB"));
    }

    @Test
    void mainCastHasCostumesWithSanePieces() {
        Set<Material> heads = Set.of(Material.CREEPER_HEAD, Material.DRAGON_HEAD,
                Material.ZOMBIE_HEAD, Material.SKELETON_SKULL,
                Material.WITHER_SKELETON_SKULL, Material.PIGLIN_HEAD,
                Material.LEATHER_HELMET, Material.GOLDEN_HELMET, Material.IRON_HELMET);
        for (String type : new String[] { "EVOKER", "BLAZE", "SHEEP", "CREEPER",
                "ENDERMAN", "SKELETON", "ZOMBIE", "SPIDER", "SLIME", "GHAST",
                "WITCH", "WITHER", "ENDER_DRAGON", "PIG", "COW", "CHICKEN",
                "WOLF", "HORSE", "PHANTOM", "VEX", "DROWNED" }) {
            MobFormCostumes.Spec spec = MobFormCostumes.specFor(type);
            assertNotNull(spec, type);
            if (spec.helmet() != null) {
                assertTrue(heads.contains(spec.helmet()), type);
            }
            assertNotNull(spec.chest(), type);
            assertNotNull(spec.legs(), type);
            assertNotNull(spec.boots(), type);
        }
    }

    @Test
    void markedCheckIsNullSafe() {
        assertFalse(MobFormCostumes.isMarked(null, null));
        assertFalse(MobFormCostumes.specFor(null) != null
                && MobFormCostumes.isMarked(new org.bukkit.inventory.ItemStack(Material.STONE), null));
    }

    @Test
    void noDuplicateFormKeys() {
        List<String> types = FormStats.detailedTypes();
        assertEquals(new HashSet<>(types).size(), types.size());
    }
}
