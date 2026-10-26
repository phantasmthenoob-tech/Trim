package com.entitystudio.behavior;

import java.util.Set;
import java.util.UUID;

/**
 * Deterministic targeting evaluation order (pure logic, unit tested):
 * 1. target validity → 2. exclusions (owner/allies/protected) →
 * 3. target rules (enemies, defense filters) → 4. set or clear.
 * An explicit protected-player rule always wins over an enemy rule.
 */
public final class AllegianceRules {

    public enum Decision {
        ATTACK,
        IGNORE,
    }

    /** Pure input snapshot for one candidate target. */
    public record TargetContext(boolean targetValid, boolean targetIsOwner,
            boolean targetIsTrusted, boolean targetIsExplicitEnemy,
            boolean targetIsProtectedPlayer, String targetType,
            Set<String> allowedTypes, Set<String> excludedTypes) {
    }

    private AllegianceRules() {
    }

    public static Decision evaluate(TargetContext ctx) {
        // 1. validity
        if (!ctx.targetValid()) {
            return Decision.IGNORE;
        }
        // 2. exclusions — protected players, owner, trusted allies always win
        if (ctx.targetIsProtectedPlayer() || ctx.targetIsOwner() || ctx.targetIsTrusted()) {
            return Decision.IGNORE;
        }
        String type = ctx.targetType() == null ? "" : ctx.targetType().toUpperCase();
        if (ctx.excludedTypes().contains(type)) {
            return Decision.IGNORE;
        }
        if (!ctx.allowedTypes().isEmpty() && !ctx.allowedTypes().contains(type)) {
            return Decision.IGNORE;
        }
        // 3. explicit enemies
        if (ctx.targetIsExplicitEnemy()) {
            return Decision.ATTACK;
        }
        return Decision.IGNORE;
    }

    /** Should the guard engage the attacker of its owner? */
    public static boolean shouldDefend(UUID attackerUuid, boolean defendEnabled,
            Set<UUID> trusted, Set<UUID> protectedPlayers) {
        if (!defendEnabled || attackerUuid == null) {
            return false;
        }
        if (protectedPlayers.contains(attackerUuid) || trusted.contains(attackerUuid)) {
            return false;
        }
        return true;
    }
}
