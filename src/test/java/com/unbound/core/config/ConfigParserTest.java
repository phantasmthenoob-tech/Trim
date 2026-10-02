package com.unbound.core.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigParserTest {

    @Test
    void emptyInputFallsBackToDocumentedDefaults() {
        UnboundConfig config = ConfigParser.parse(Map.of());
        assertFalse(config.enchantingTableEnabled());
        assertEquals(DefaultConfig.LIMITS, config.limits());
        assertTrue(config.infinity().preventConsumption());
        assertTrue(config.infinity().preventDurabilityLoss());
        assertTrue(config.infinity().restoreTotems());
        assertTrue(config.infinity().restoreBlocks());
        assertFalse(config.infinity().excludedBlocks().contains("cobweb"));
        assertTrue(config.infinity().excludedBlocks().contains("diamond_block"));
        assertEquals(0.0, config.unbreaking().extraSkipChancePerLevel());
        assertEquals(2.0, config.mending().durabilityPerXp());
        assertEquals(1.0, config.efficiency().scaling());
        assertEquals(0.15, config.quickCharge().useTimeReductionPerLevel());
        assertEquals(2.5, config.multishot().sword().baseRadius());
        assertEquals(1.0, config.sharpness().baseBonus()); // exactly vanilla
        assertTrue(config.power().projectiles().contains("wind_charge"));
        assertTrue(config.fortune().extraDrops().contains("diamond"));
        assertFalse(config.looting().boostRareDrops());
    }

    @Test
    void parsesTheShippedConfigShape() {
        String yaml = """
                progression:
                  enchanting-table:
                    enabled: false
                    deny-message: "&cNope."
                limits:
                  max-entities-affected-per-action: 48
                  max-blocks-affected-per-action: 32
                  max-projectiles-created-per-action: 8
                  max-effect-chain-depth: 3
                enchantments:
                  infinity:
                    enabled: true
                    max-level: 1
                    consumables:
                      enabled: true
                      prevent-consumption: true
                      excluded-materials: [milk_bucket, glass_bottle]
                    durability:
                      enabled: false
                      prevent-durability-loss: false
                  unbreaking:
                    enabled: false
                    max-level: 10
                  multishot:
                    enabled: true
                    max-level: 5
                    sword:
                      enabled: true
                      radius-per-level: 2.0
                      max-targets: 8
                      damage-fraction: 0.5
                """;
        UnboundConfig config = ConfigParser.parse(new Yaml().load(yaml));

        assertFalse(config.enchantingTableEnabled());
        assertEquals("&cNope.", config.enchantingDenyMessage());
        assertEquals(new com.unbound.core.enchant.ProcessingGuard.Limits(48, 32, 8, 3), config.limits());

        assertEquals(new UnboundConfig.EnchToggle(true, 1), config.toggleFor("infinity"));
        assertEquals(new UnboundConfig.EnchToggle(false, 10), config.toggleFor("unbreaking"));
        assertEquals(new UnboundConfig.EnchToggle(true, 5), config.toggleFor("multishot"));
        // Missing enchantment nodes still default sensibly:
        assertEquals(new UnboundConfig.EnchToggle(true, 1), config.toggleFor("sharpness"));

        assertTrue(config.infinity().durabilityEnabled() == false);
        assertTrue(config.infinity().excludedMaterials().contains("glass_bottle"));
        assertEquals(2.0, config.multishot().sword().radiusPerLevel());
        assertEquals(8, config.multishot().sword().maxTargets());
        assertEquals(0.5, config.multishot().sword().damageFraction());
    }

    @Test
    void invalidValuesAreIgnoredInFavorOfDefaults() {
        UnboundConfig config = ConfigParser.parse(new Yaml().load("""
                limits:
                  max-entities-affected-per-action: "not-a-number"
                  max-effect-chain-depth: -5
                enchantments:
                  quick-charge:
                    enabled: "yes"
                    max-level: 0
                    use-time-reduction-per-level: 5
                """));
        assertEquals(32, config.limits().maxEntities());
        assertEquals(8, config.limits().maxDepth()); // negative clamped to default? -> falls back
        assertTrue(config.quickCharge().useTimeReductionPerLevel() <= 1.0); // clamped to 0..1
        assertEquals(new UnboundConfig.EnchToggle(true, 1), config.toggleFor("quick-charge"));
    }

    @Test
    void defaultsMapParses() {
        UnboundConfig config = ConfigParser.parse(DefaultConfig.asMap());
        assertFalse(config.enchantingTableEnabled());
        assertEquals(DefaultConfig.LIMITS, config.limits());
    }

    @Test
    void shippedConfigResourceParsesAndMatchesCodeDefaults() throws Exception {
        try (java.io.InputStream in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml must be on the test classpath (processed resources)");
            String yaml = java.nio.charset.StandardCharsets.UTF_8.decode(
                    java.nio.ByteBuffer.wrap(in.readAllBytes())).toString();
            UnboundConfig parsed = ConfigParser.parse(new Yaml().load(yaml));
            UnboundConfig defaults = ConfigTestSupport.defaults();
            // The shipped file must agree with the code defaults where it
            // does not deliberately diverge (sharpness, debug, progression).
            assertEquals(defaults.limits(), parsed.limits());
            assertEquals(defaults.enchantToggles(), parsed.enchantToggles());
            assertEquals(defaults.infinity(), parsed.infinity());
            assertEquals(defaults.unbreaking(), parsed.unbreaking());
            assertEquals(defaults.mending(), parsed.mending());
            assertEquals(defaults.efficiency(), parsed.efficiency());
            assertEquals(defaults.quickCharge(), parsed.quickCharge());
            assertEquals(defaults.multishot(), parsed.multishot());
            assertEquals(defaults.sharpness(), parsed.sharpness());
            assertEquals(defaults.power(), parsed.power());
            assertEquals(defaults.punch(), parsed.punch());
            assertEquals(defaults.fortune(), parsed.fortune());
            assertEquals(defaults.looting(), parsed.looting());
        }
    }

    @Test
    void debugAndProgressionFlagsParse() {
        UnboundConfig config = ConfigParser.parse(new Yaml().load("""
                progression:
                  enchanting-table:
                    enabled: true
                debug:
                  enabled: true
                  allow-player-toggle: false
                  show-timing: false
                """));
        assertTrue(config.enchantingTableEnabled());
        assertTrue(config.debugEnabled());
        assertFalse(config.debugAllowPlayerToggle());
        assertFalse(config.debugShowTiming());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sectionValuesAreTypedMaps() {
        Object parsed = new Yaml().load("a:\n  b:\n    c: 1\n");
        Map<String, Object> root = (Map<String, Object>) assertInstanceOf(Map.class, parsed);
        ConfigParser.parse(root); // must not throw
    }
}
