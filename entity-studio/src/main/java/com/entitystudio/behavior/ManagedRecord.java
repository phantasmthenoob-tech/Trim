package com.entitystudio.behavior;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Plugin-managed allegiance + behavior settings for one entity (by UUID).
 * Distinct from native taming: a guarded zombie is NOT a tamed wolf. */
public class ManagedRecord {

    public enum Profile {
        PASSIVE,
        NEUTRAL,
        GUARD,
        AGGRESSIVE,
        STATIONARY,
        CUSTOM,
    }

    private final UUID entityUuid;
    private String entityType = "UNKNOWN";
    private UUID owner;
    private final Set<UUID> trusted = new HashSet<>();
    private final Set<UUID> enemies = new HashSet<>();
    private final Set<String> allowedTypes = new HashSet<>();
    private final Set<String> excludedTypes = new HashSet<>();
    private boolean defendOwner = true;
    private boolean defendAllies = false;
    private boolean retaliate = false;
    private boolean attackOwnerTarget = false;
    private boolean pursueOutsideRange = true;
    private boolean clearInvalidTarget = true;
    private boolean friendlyFireProtect = true;
    /** Plugin-driven aggression (AGGRESSIVE profile): hunt anything nearby
     * that isn't family. Distinct from vanilla hostility — works for cows
     * and creepers alike. Persisted like any other config flag. */
    private boolean aggressive;
    /** Last target this record's rules assigned (owner command, defense,
     * retaliation). Transient on purpose: targets never survive restarts,
     * so this is deliberately NOT persisted by the codec. */
    private UUID commandedTarget;
    private long commandedAt;
    private Profile profile = Profile.CUSTOM;
    private boolean aiEnabled = true;
    private boolean anchored = false;
    private boolean hardAnchor = false;
    private double anchorX, anchorY, anchorZ;
    private float anchorYaw, anchorPitch;
    private String anchorWorld;

    public ManagedRecord(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public UUID entityUuid() {
        return entityUuid;
    }

    public String entityType() {
        return entityType;
    }

    public void entityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID owner() {
        return owner;
    }

    public void owner(UUID owner) {
        this.owner = owner;
    }

    public Set<UUID> trusted() {
        return trusted;
    }

    public Set<UUID> enemies() {
        return enemies;
    }

    public Set<String> allowedTypes() {
        return allowedTypes;
    }

    public Set<String> excludedTypes() {
        return excludedTypes;
    }

    public boolean defendOwner() {
        return defendOwner;
    }

    public void defendOwner(boolean v) {
        defendOwner = v;
    }

    public boolean defendAllies() {
        return defendAllies;
    }

    public void defendAllies(boolean v) {
        defendAllies = v;
    }

    public boolean retaliate() {
        return retaliate;
    }

    public void retaliate(boolean v) {
        retaliate = v;
    }

    public boolean attackOwnerTarget() {
        return attackOwnerTarget;
    }

    public void attackOwnerTarget(boolean v) {
        attackOwnerTarget = v;
    }

    public boolean pursueOutsideRange() {
        return pursueOutsideRange;
    }

    public void pursueOutsideRange(boolean v) {
        pursueOutsideRange = v;
    }

    public boolean clearInvalidTarget() {
        return clearInvalidTarget;
    }

    public void clearInvalidTarget(boolean v) {
        clearInvalidTarget = v;
    }

    public boolean friendlyFireProtect() {
        return friendlyFireProtect;
    }

    public UUID commandedTarget() {
        return commandedTarget;
    }

    public long commandedAt() {
        return commandedAt;
    }

    public void command(UUID target, long now) {
        commandedTarget = target;
        commandedAt = now;
    }

    public void clearCommand() {
        commandedTarget = null;
        commandedAt = 0;
    }

    public void friendlyFireProtect(boolean v) {
        friendlyFireProtect = v;
    }

    public boolean aggressive() {
        return aggressive;
    }

    public void aggressive(boolean v) {
        aggressive = v;
    }

    /** The given UUID is this record's owner or a trusted ally. */
    public boolean isAlly(UUID id) {
        return id != null && (id.equals(owner) || trusted.contains(id));
    }

    public Profile profile() {
        return profile;
    }

    public void profile(Profile v) {
        profile = v;
    }

    public boolean aiEnabled() {
        return aiEnabled;
    }

    public void aiEnabled(boolean v) {
        aiEnabled = v;
    }

    public boolean anchored() {
        return anchored;
    }

    public void setAnchor(boolean anchored, boolean hard, String world,
            double x, double y, double z, float yaw, float pitch) {
        this.anchored = anchored;
        this.hardAnchor = hard;
        this.anchorWorld = world;
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
        this.anchorYaw = yaw;
        this.anchorPitch = pitch;
    }

    public boolean hardAnchor() {
        return hardAnchor;
    }

    public String anchorWorld() {
        return anchorWorld;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double anchorZ() {
        return anchorZ;
    }

    public float anchorYaw() {
        return anchorYaw;
    }

    public float anchorPitch() {
        return anchorPitch;
    }
}
