package com.entitystudio.property;

import com.entitystudio.property.EntityProperty.ParseResult;
import com.entitystudio.property.EntityProperty.PropertyType;
import com.entitystudio.property.EntityProperty.RiskLevel;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/** Vitals, movement, presentation and AI properties (public-API only). */
public final class CommonProperties {

    private CommonProperties() {
    }

    public static void register(PropertyRegistry reg) {
        // ---- vitals ----
        reg.register(new BaseProperty<Double>("health", "Health",
                "Current health. 0 kills most entities — confirm before zeroing.",
                "Living entities",
                PropertyType.NUMBER, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof LivingEntity ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Double read(Entity e) {
                return ((LivingEntity) e).getHealth();
            }

            @Override
            public ValidationResult validate(Entity e, Double v) {
                LivingEntity l = (LivingEntity) e;
                return Validators.range(v, 0, Math.max(l.getMaxHealth(), 0.5), "Health");
            }

            @Override
            public void write(Entity e, Double v) {
                ((LivingEntity) e).setHealth(Math.min(v, ((LivingEntity) e).getMaxHealth()));
            }

            @Override
            public String failureBehavior() {
                return "Validated against the live max health; writes clamp to max, never throw.";
            }

            @Override
            public ParseResult<Double> parse(String input) {
                return Validators.parseDouble(input, 0, 1024, "health");
            }
        });
        reg.register(new BaseProperty<Double>("max_health", "Max health",
                "Attribute-driven maximum health. Changing it rescales the health bar.",
                "Living entities (requires the max-health attribute)",
                PropertyType.NUMBER, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                if (!(e instanceof LivingEntity l)) {
                    return Support.UNSUPPORTED;
                }
                try {
                    return l.getAttribute(Attribute.MAX_HEALTH) != null ? Support.SUPPORTED
                            : Support.UNAVAILABLE;
                } catch (LinkageError | RuntimeException ex) {
                    // Attribute API itself unavailable (headless/unit-test runtimes,
                    // exotic forks): degrade instead of breaking the whole page.
                    return Support.UNAVAILABLE;
                }
            }

            @Override
            public Double read(Entity e) {
                return ((LivingEntity) e).getAttribute(Attribute.MAX_HEALTH).getValue();
            }

            @Override
            public ValidationResult validate(Entity e, Double v) {
                return Validators.range(v, 1, 1024, "Max health");
            }

            @Override
            public void write(Entity e, Double v) {
                AttributeInstance a = ((LivingEntity) e).getAttribute(Attribute.MAX_HEALTH);
                a.setBaseValue(v);
                LivingEntity l = (LivingEntity) e;
                if (l.getHealth() > v) {
                    l.setHealth(v);
                }
            }

            @Override
            public ParseResult<Double> parse(String input) {
                return Validators.parseDouble(input, 1, 1024, "max health");
            }
        });
        reg.register(new BaseProperty<Double>("absorption", "Absorption",
                "Yellow bonus hearts (e.g. from golden apples).",
                "Living entities",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof LivingEntity ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Double read(Entity e) {
                return (double) ((LivingEntity) e).getAbsorptionAmount();
            }

            @Override
            public ValidationResult validate(Entity e, Double v) {
                return Validators.range(v, 0, 2048, "Absorption");
            }

            @Override
            public void write(Entity e, Double v) {
                ((LivingEntity) e).setAbsorptionAmount(v);
            }

            @Override
            public ParseResult<Double> parse(String input) {
                return Validators.parseDouble(input, 0, 2048, "absorption");
            }
        });
        reg.register(new BaseProperty<Integer>("fire_ticks", "Fire ticks",
                "Ticks the entity stays on fire. Negative values grant fire immunity time.",
                "All entities",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return e.getFireTicks();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, -20, 20 * 60 * 30, "Fire ticks");
            }

            @Override
            public void write(Entity e, Integer v) {
                e.setFireTicks(Math.max(v, -20));
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, -20, 20 * 60 * 30, "fire ticks");
            }
        });
        reg.register(new BaseProperty<Integer>("freeze_ticks", "Freeze ticks",
                "Powder-snow freezing progress. Max depends on the entity.",
                "All entities",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return e.getFreezeTicks();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, Math.max(e.getMaxFreezeTicks(), 1), "Freeze ticks");
            }

            @Override
            public void write(Entity e, Integer v) {
                e.setFreezeTicks(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 100000, "freeze ticks");
            }
        });
        reg.register(new BaseProperty<Integer>("air", "Remaining air",
                "Drowning timer for applicable entities.",
                "Living entities",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof LivingEntity ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((LivingEntity) e).getRemainingAir();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                LivingEntity l = (LivingEntity) e;
                return Validators.range(v, -20, Math.max(l.getMaximumAir(), 1), "Air");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((LivingEntity) e).setRemainingAir(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, -20, 100000, "air");
            }
        });
        reg.register(new BaseProperty<Boolean>("invulnerable", "Invulnerable",
                "Ignores all damage. Boss arenas love this; survival balance does not.",
                "All entities",
                PropertyType.BOOLEAN, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return e.isInvulnerable();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                e.setInvulnerable(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- movement / physical ----
        reg.register(new BaseProperty<Boolean>("gravity", "Gravity",
                "Off = floats in place (still takes knockback and teleports).",
                "All entities",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return e.hasGravity();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                e.setGravity(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("silent", "Silent",
                "Mutes all sounds this entity makes.",
                "All entities",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return e.isSilent();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                e.setSilent(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("persistent", "Persistent",
                "Off = may despawn when far away (vanilla rules still decide).",
                "All entities",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return e.isPersistent();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                e.setPersistent(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("ai", "AI enabled",
                "Off = stands still (gravity, fire, knockback still apply).",
                "Living entities",
                PropertyType.BOOLEAN, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof LivingEntity ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((LivingEntity) e).hasAI();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((LivingEntity) e).setAI(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("aware", "Aware",
                "Mob AI awareness. Unaware mobs don't notice targets or paths.",
                "Mobs",
                PropertyType.BOOLEAN, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Mob ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Mob) e).isAware();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Mob) e).setAware(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- presentation ----
        reg.register(new BaseProperty<String>("custom_name", "Custom name",
                "Nameplate text. Supports legacy & codes; visibility is separate.",
                "All entities",
                PropertyType.TEXT, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public String read(Entity e) {
                String n = e.getCustomName();
                return n == null ? "" : n;
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                return Validators.maxLength(v == null ? "" : v, 256, "Name");
            }

            @Override
            public void write(Entity e, String v) {
                e.setCustomName(v == null || v.isBlank() ? null : v);
            }

            @Override
            public ParseResult<String> parse(String input) {
                if (input == null || input.length() > 256) {
                    return ParseResult.fail("Name must be at most 256 characters.");
                }
                return ParseResult.ok(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("name_visible", "Name visible",
                "Always show the nameplate, even without looking at the entity.",
                "All entities",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return e.isCustomNameVisible();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                e.setCustomNameVisible(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("glowing", "Glowing",
                "Outline visible through walls (team colors may override the color).",
                "All entities",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.SUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return e.isGlowing();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                e.setGlowing(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- read-only identity facts (no setter exists by design) ----
        reg.register(new BaseProperty<String>("entity.uuid", "Entity UUID",
                "Stable server-assigned identity. Shown for admin/copy use.",
                "All entities",
                PropertyType.TEXT, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.READ_ONLY;
            }

            @Override
            public String read(Entity e) {
                return e.getUniqueId().toString();
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                return ValidationResult.fail("Entity UUID is read-only.");
            }

            @Override
            public void write(Entity e, String v) {
                throw new UnsupportedOperationException("Entity UUID is read-only.");
            }

            @Override
            public ParseResult<String> parse(String input) {
                return ParseResult.fail("Entity UUID is read-only.");
            }
        });
        reg.register(new BaseProperty<String>("entity.type", "Entity type",
                "Bukkit entity type name. Shown for admin/reference use.",
                "All entities",
                PropertyType.TEXT, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return Support.READ_ONLY;
            }

            @Override
            public String read(Entity e) {
                return e.getType().name().toLowerCase(java.util.Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                return ValidationResult.fail("Entity type is read-only.");
            }

            @Override
            public void write(Entity e, String v) {
                throw new UnsupportedOperationException("Entity type is read-only.");
            }

            @Override
            public ParseResult<String> parse(String input) {
                return ParseResult.fail("Entity type is read-only.");
            }
        });
    }
}
