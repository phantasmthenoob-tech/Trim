package com.entitystudio.property;

import org.bukkit.entity.Entity;

/** Boilerplate base: subclasses only define scope, access, validation, parsing. */
public abstract class BaseProperty<T> implements EntityProperty<T> {

    private final String id;
    private final String displayName;
    private final String description;
    private final PropertyType type;
    private final RiskLevel risk;
    private final boolean reversible;

    protected BaseProperty(String id, String displayName, String description,
            PropertyType type, RiskLevel risk, boolean reversible) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.risk = risk;
        this.reversible = reversible;
    }

    /** Entity scope gate (also drives the honesty badge in the GUI). */
    protected abstract boolean supports(Entity entity);

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public PropertyType type() {
        return type;
    }

    @Override
    public RiskLevel risk() {
        return risk;
    }

    @Override
    public boolean reversible() {
        return reversible;
    }

    @Override
    public Support support(Entity entity) {
        return supports(entity) ? Support.SUPPORTED : Support.UNSUPPORTED;
    }

    @Override
    public ParseResult<T> parse(String input) {
        return ParseResult.fail("This property cannot be set by typing.");
    }
}
