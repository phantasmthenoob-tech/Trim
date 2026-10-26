package com.entitystudio.permission;

import java.util.Locale;

/**
 * Activation gate for Entity Studio.
 *
 * <p>The plugin starts LOCKED: editor commands work for operators only after
 * activation via {@code /entity start}. One configured owner account
 * ({@code owner-name} in config.yml) may always use the commands, with or
 * without op — including before activation. That override is intentionally
 * declared in the config file and logged at startup, never hidden in code,
 * so every server owner can see, change, or disable it.
 *
 * <p>This class is pure logic (no Bukkit) so the rules are unit tested.
 */
public class Gatekeeper {

    /** Outcome of an access check. */
    public enum Access {
        /** Allowed: owner, or active plugin + operator/permission holder. */
        ALLOW,
        /** Denied because the plugin has not been activated yet. */
        NEED_START,
    }

    private final String ownerName; // lowercase, "" = disabled
    private boolean active;

    public Gatekeeper(String ownerName, boolean active) {
        this.ownerName = ownerName == null ? "" : ownerName.trim().toLowerCase(Locale.ROOT);
        this.active = active;
    }

    public boolean isOwner(String playerName) {
        return !ownerName.isEmpty() && playerName != null
                && ownerName.equals(playerName.toLowerCase(Locale.ROOT));
    }

    /** Only the owner can activate. Console may also activate. */
    public boolean canStart(String senderName, boolean isConsole) {
        return isConsole || isOwner(senderName);
    }

    /** Gate check. Callers still verify permission nodes / op afterwards. */
    public Access decide(String senderName, boolean isOp) {
        if (isOwner(senderName)) {
            return Access.ALLOW;
        }
        return active ? Access.ALLOW : Access.NEED_START;
    }

    public boolean ownerBypassesPermissions(String senderName) {
        return isOwner(senderName);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String ownerName() {
        return ownerName;
    }
}
