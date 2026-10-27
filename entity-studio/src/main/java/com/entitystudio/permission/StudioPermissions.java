package com.entitystudio.permission;

/** Permission node constants. A hidden GUI button is never a security boundary:
 * every write path re-checks these at the service layer. */
public final class StudioPermissions {
    public static final String USE = "entitystudio.use";
    public static final String INSPECT = "entitystudio.inspect";
    public static final String INV_VIEW = "entitystudio.inventory.view";
    public static final String INV_EDIT = "entitystudio.inventory.edit";
    public static final String DATA_VIEW = "entitystudio.data.view";
    public static final String DATA_EDIT = "entitystudio.data.edit";
    public static final String BEHAVIOR = "entitystudio.behavior";
    public static final String ALLEGIANCE = "entitystudio.allegiance";
    public static final String TRADES = "entitystudio.trades";
    public static final String SNAPSHOT = "entitystudio.snapshot";
    public static final String UNDO = "entitystudio.undo";
    public static final String PRESETS = "entitystudio.presets";
    public static final String PLAYER_EDIT = "entitystudio.player.edit";
    public static final String MORPH = "entitystudio.morph";
    public static final String ADMIN = "entitystudio.admin";

    private StudioPermissions() {
    }
}
