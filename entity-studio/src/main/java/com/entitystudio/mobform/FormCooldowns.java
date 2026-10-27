package com.entitystudio.mobform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-player ability cooldowns. Clock-injectable so the rules are
 * headless-testable; the live service passes real time.
 */
public final class FormCooldowns {

    private final Map<String, Long> expiry = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public FormCooldowns() {
        this(System::currentTimeMillis);
    }

    FormCooldowns(LongSupplier clock) {
        this.clock = clock;
    }

    /** Consumes the cooldown when ready; false when still cooling down. */
    public boolean tryUse(UUID player, String key, long cooldownMs) {
        if (cooldownMs <= 0) {
            return true;
        }
        long now = clock.getAsLong();
        String k = player + ":" + key;
        Long exp = expiry.get(k);
        if (exp != null && now < exp) {
            return false;
        }
        expiry.put(k, now + cooldownMs);
        return true;
    }

    /** Milliseconds left, or 0 when ready. */
    public long remainingMs(UUID player, String key) {
        Long exp = expiry.get(player + ":" + key);
        if (exp == null) {
            return 0;
        }
        return Math.max(0, exp - clock.getAsLong());
    }

    public void clear(UUID player) {
        String prefix = player + ":";
        expiry.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
