package com.entitystudio.behavior;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static com.entitystudio.behavior.AllegianceRules.Decision.ATTACK;
import static com.entitystudio.behavior.AllegianceRules.Decision.IGNORE;
import static org.junit.jupiter.api.Assertions.*;

class AllegianceRulesTest {

    private AllegianceRules.TargetContext ctx(boolean owner, boolean trusted, boolean enemy,
            boolean protectedPlayer, String type, Set<String> allowed, Set<String> excluded) {
        return new AllegianceRules.TargetContext(true, owner, trusted, enemy, protectedPlayer,
                type, allowed, excluded);
    }

    @Test
    void invalidTargetsAlwaysIgnored() {
        var ctx = new AllegianceRules.TargetContext(false, false, false, true, false,
                "PLAYER", Set.of(), Set.of());
        assertEquals(IGNORE, AllegianceRules.evaluate(ctx));
    }

    @Test
    void ownerAndTrustedNeverAttacked() {
        assertEquals(IGNORE, AllegianceRules.evaluate(
                ctx(true, false, true, false, "PLAYER", Set.of(), Set.of())));
        assertEquals(IGNORE, AllegianceRules.evaluate(
                ctx(false, true, true, false, "PLAYER", Set.of(), Set.of())));
    }

    @Test
    void protectedPlayerBeatsEnemyRule() {
        assertEquals(IGNORE, AllegianceRules.evaluate(
                ctx(false, false, true, true, "PLAYER", Set.of(), Set.of())));
    }

    @Test
    void explicitEnemyAttacked() {
        assertEquals(ATTACK, AllegianceRules.evaluate(
                ctx(false, false, true, false, "ZOMBIE", Set.of(), Set.of())));
    }

    @Test
    void bystanderIgnored() {
        assertEquals(IGNORE, AllegianceRules.evaluate(
                ctx(false, false, false, false, "COW", Set.of(), Set.of())));
    }

    @Test
    void typeFiltersApply() {
        assertEquals(IGNORE, AllegianceRules.evaluate(
                ctx(false, false, true, false, "VILLAGER", Set.of(), Set.of("VILLAGER"))));
        assertEquals(ATTACK, AllegianceRules.evaluate(
                ctx(false, false, true, false, "ZOMBIE", Set.of("ZOMBIE"), Set.of())));
        assertEquals(IGNORE, AllegianceRules.evaluate(
                ctx(false, false, true, false, "SKELETON", Set.of("ZOMBIE"), Set.of())));
    }

    @Test
    void defendRulesExcludeTrustedAndProtected() {
        UUID attacker = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        assertTrue(AllegianceRules.shouldDefend(attacker, true, Set.of(), Set.of()));
        assertFalse(AllegianceRules.shouldDefend(trusted, true, Set.of(trusted), Set.of()));
        assertFalse(AllegianceRules.shouldDefend(attacker, false, Set.of(), Set.of()));
        assertFalse(AllegianceRules.shouldDefend(null, true, Set.of(), Set.of()));
    }
}
