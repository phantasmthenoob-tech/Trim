package com.entitystudio.property;

import com.entitystudio.property.EntityProperty.ParseResult;
import com.entitystudio.property.EntityProperty.PropertyType;
import com.entitystudio.property.EntityProperty.RiskLevel;
import org.bukkit.DyeColor;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Shearable;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.material.Colorable;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Age, taming, villager, creeper, sheep, slime, horse and wolf properties. */
public final class SpecificProperties {

    private SpecificProperties() {
    }

    public static void register(PropertyRegistry reg) {
        reg.register(new BaseProperty<Integer>("age", "Age ticks",
                "Negative = baby growing up; 0+ = adult. Lock it with age lock.",
                "Ageable mobs",
                PropertyType.NUMBER, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Ageable ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((Ageable) e).getAge();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, -24000, 6000, "Age");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((Ageable) e).setAge(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, -24000, 6000, "age");
            }
        });
        reg.register(new BaseProperty<Boolean>("age_lock", "Age lock",
                "Freezes aging at the current stage.",
                "Ageable mobs",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Ageable ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Ageable) e).getAgeLock();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Ageable) e).setAgeLock(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("baby", "Baby",
                "Shortcut for age. Restoring 'adult' uses age 0 (approximate).",
                "Ageable mobs",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Ageable ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return !((Ageable) e).isAdult();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                if (v) {
                    ((Ageable) e).setBaby();
                } else {
                    ((Ageable) e).setAdult();
                }
            }

            @Override
            public String failureBehavior() {
                return "Restoring adult approximates with age 0; the exact prior age is not kept.";
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("tamed", "Tamed (native)",
                "Native taming flag. A plugin-managed guard is NOT native taming.",
                "Tameable animals",
                PropertyType.BOOLEAN, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Tameable ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Tameable) e).isTamed();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Tameable) e).setTamed(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("sitting", "Sitting",
                "Native sit pose for sittable mobs.",
                "Sittable mobs",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Sittable ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Sittable) e).isSitting();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Sittable) e).setSitting(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- villagers ----
        reg.register(new BaseProperty<String>("villager.profession", "Profession",
                // NOTE: computed without touching Villager.Profession so registry
                // construction stays server-free (unit-testable); the full list
                // is validated against the live registry on write.
                "Villager profession (farmer, librarian, ...).",
                "Villagers",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Villager ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                return ((Villager) e).getProfession().name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    Villager.Profession.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown profession '" + v + "'.");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((Villager) e).setProfession(Villager.Profession.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    Villager.Profession.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown profession. Use one of: "
                            + Arrays.stream(Villager.Profession.values())
                                    .map(p -> p.name().toLowerCase(Locale.ROOT))
                                    .collect(Collectors.joining(", ")));
                }
            }
        });
        reg.register(new BaseProperty<Integer>("villager.level", "Villager level",
                "1 (novice) to 5 (master).",
                "Villagers",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Villager ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((Villager) e).getVillagerLevel();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 1, 5, "Level");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((Villager) e).setVillagerLevel(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 1, 5, "level");
            }
        });
        reg.register(new BaseProperty<Integer>("villager.experience", "Villager XP",
                "Trade experience towards the next level.",
                "Villagers",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Villager ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((Villager) e).getVillagerExperience();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, 100000, "Experience");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((Villager) e).setVillagerExperience(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 100000, "experience");
            }
        });
        // ---- creepers ----
        reg.register(new BaseProperty<Boolean>("creeper.powered", "Charged",
                "Lightning-struck creeper (much bigger boom).",
                "Creepers",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Creeper ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Creeper) e).isPowered();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Creeper) e).setPowered(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Integer>("creeper.fuse", "Fuse ticks",
                "Countdown once ignited. Max fuse caps it.",
                "Creepers",
                PropertyType.NUMBER, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Creeper ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((Creeper) e).getFuseTicks();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, ((Creeper) e).getMaxFuseTicks(), "Fuse");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((Creeper) e).setFuseTicks(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 10000, "fuse");
            }
        });
        // ---- sheep / colorable ----
        reg.register(new BaseProperty<String>("color", "Color",
                "Wool/collar color where the entity supports dye colors.",
                "Colorable entities",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Colorable ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                DyeColor c = ((Colorable) e).getColor();
                return c == null ? "white" : c.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    DyeColor.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown color '" + v + "'.");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((Colorable) e).setColor(DyeColor.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    DyeColor.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown color. One of: "
                            + Arrays.stream(DyeColor.values())
                                    .map(c -> c.name().toLowerCase(Locale.ROOT))
                                    .collect(Collectors.joining(", ")));
                }
            }
        });
        reg.register(new BaseProperty<Boolean>("sheared", "Sheared",
                "Whether a shearable entity has been sheared.",
                "Shearable entities",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Shearable ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Shearable) e).isSheared();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Shearable) e).setSheared(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- slime ----
        reg.register(new BaseProperty<Integer>("slime.size", "Slime size",
                "1 = tiny, bigger = splits on death. Very large values lag.",
                "Slimes",
                PropertyType.NUMBER, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Slime ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((Slime) e).getSize();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 1, 100, "Size");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((Slime) e).setSize(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 1, 100, "size");
            }
        });
        // ---- horses ----
        reg.register(new BaseProperty<Double>("horse.jump", "Jump strength",
                "Horse jump power, roughly 0 (none) to 2 (max legit ~1).",
                "Horses",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof AbstractHorse ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Double read(Entity e) {
                return ((AbstractHorse) e).getJumpStrength();
            }

            @Override
            public ValidationResult validate(Entity e, Double v) {
                return Validators.range(v, 0, 2, "Jump strength");
            }

            @Override
            public void write(Entity e, Double v) {
                ((AbstractHorse) e).setJumpStrength(v);
            }

            @Override
            public ParseResult<Double> parse(String input) {
                return Validators.parseDouble(input, 0, 2, "jump strength");
            }
        });
        reg.register(new BaseProperty<Boolean>("horse.chest", "Carrying chest",
                "Chest storage for donkeys, mules and llamas.",
                "Chested horses (donkeys, mules, llamas)",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                // Removing a chest can destroy stored items, so this never
                // toggles silently: the GUI routes it through the container.
                return e instanceof ChestedHorse ? Support.SPECIAL : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((ChestedHorse) e).isCarryingChest();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((ChestedHorse) e).setCarryingChest(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- tameable ownership (read-only: AnimalTamer needs a live object) ----
        reg.register(new BaseProperty<String>("tame.owner_uuid", "Owner UUID",
                "Native owner identity. Assign via taming in-game; shown here for audit.",
                "Tameable animals",
                PropertyType.TEXT, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Tameable ? Support.READ_ONLY : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                java.util.UUID id = ((Tameable) e).getOwnerUniqueId();
                return id == null ? "none" : id.toString();
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                return ValidationResult.fail("Owner is assigned by taming, not typed here.");
            }

            @Override
            public void write(Entity e, String v) {
                throw new UnsupportedOperationException("Owner is assigned by taming, not typed here.");
            }

            @Override
            public ParseResult<String> parse(String input) {
                return ParseResult.fail("Owner is assigned by taming, not typed here.");
            }
        });
        // ---- breeding (love mode is consumable: honest irreversibility) ----
        reg.register(new BaseProperty<Boolean>("breed", "In love (breed)",
                "Puts an adult animal in love mode (hearts, then breeding). Consumes the mode.",
                "Ageable animals",
                PropertyType.BOOLEAN, RiskLevel.ADVANCED, false) {
            @Override
            public Support support(Entity e) {
                if (!(e instanceof Ageable)) {
                    return Support.UNSUPPORTED;
                }
                return e instanceof org.bukkit.entity.Animals ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Ageable) e).canBreed();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                if (Boolean.TRUE.equals(v) && e instanceof Ageable a && !a.isAdult()) {
                    return ValidationResult.fail("Only adults can enter love mode.");
                }
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Ageable) e).setBreed(v);
            }

            @Override
            public String failureBehavior() {
                return "Love mode is consumable: undo clears the flag but cannot un-consume breeding.";
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- armor stands (display workhorses: all plain booleans) ----
        reg.register(new BaseProperty<Boolean>("armorstand.visible", "Stand visible",
                "Off = invisible stand (armor still shows).",
                "Armor stands",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.ArmorStand ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.ArmorStand) e).isVisible();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.ArmorStand) e).setVisible(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("armorstand.small", "Small stand",
                "Mini armor stand.",
                "Armor stands",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.ArmorStand ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.ArmorStand) e).isSmall();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.ArmorStand) e).setSmall(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("armorstand.arms", "Stand arms",
                "Shows arms (needed to hold items in hand).",
                "Armor stands",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.ArmorStand ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.ArmorStand) e).hasArms();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.ArmorStand) e).setArms(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("armorstand.base_plate", "Base plate",
                "Stone plate under the stand.",
                "Armor stands",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.ArmorStand ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.ArmorStand) e).hasBasePlate();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.ArmorStand) e).setBasePlate(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("armorstand.marker", "Marker",
                "Tiny non-interactive hitbox. Display use; players cannot click it.",
                "Armor stands",
                PropertyType.BOOLEAN, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.ArmorStand ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.ArmorStand) e).isMarker();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.ArmorStand) e).setMarker(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("armorstand.can_move", "Stand can move",
                "Off = locked in place (still takes damage).",
                "Armor stands",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.ArmorStand ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.ArmorStand) e).canMove();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.ArmorStand) e).setCanMove(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("armorstand.can_tick", "Stand can tick",
                "Off = frozen pose logic (display stands).",
                "Armor stands",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.ArmorStand ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.ArmorStand) e).canTick();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.ArmorStand) e).setCanTick(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- creepers (extended) ----
        reg.register(new BaseProperty<Integer>("creeper.explosion_radius", "Explosion radius",
                "Blast power in blocks (vanilla 3). Large values grief — confirm.",
                "Creepers",
                PropertyType.NUMBER, RiskLevel.DESTRUCTIVE, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Creeper ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((Creeper) e).getExplosionRadius();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, 127, "Explosion radius");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((Creeper) e).setExplosionRadius(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 127, "explosion radius");
            }
        });
        reg.register(new BaseProperty<Integer>("creeper.max_fuse", "Max fuse ticks",
                "Upper cap for the fuse countdown.",
                "Creepers",
                PropertyType.NUMBER, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Creeper ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((Creeper) e).getMaxFuseTicks();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 1, 32767, "Max fuse");
            }

            @Override
            public void write(Entity e, Integer v) {
                Creeper c = (Creeper) e;
                c.setMaxFuseTicks(v);
                if (c.getFuseTicks() > v) {
                    c.setFuseTicks(v);
                }
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 1, 32767, "max fuse");
            }
        });
        reg.register(new BaseProperty<Boolean>("creeper.ignited", "Ignited",
                "Fuse is lit and counting down.",
                "Creepers",
                PropertyType.BOOLEAN, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Creeper ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Creeper) e).isIgnited();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Creeper) e).setIgnited(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- slime / phantom ----
        reg.register(new BaseProperty<Boolean>("slime.wander", "Can wander",
                "Off = slime stays put unless pushed.",
                "Slimes",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Slime ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Slime) e).canWander();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Slime) e).setWander(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Integer>("phantom.size", "Phantom size",
                "Phantom scale (0 = normal). Larger phantoms hit harder.",
                "Phantoms",
                PropertyType.NUMBER, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Phantom ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((org.bukkit.entity.Phantom) e).getSize();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, 64, "Phantom size");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((org.bukkit.entity.Phantom) e).setSize(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 64, "phantom size");
            }
        });
        reg.register(new BaseProperty<Boolean>("phantom.burn_in_day", "Burns in daylight",
                "Off = daylight-safe phantom (boss use).",
                "Phantoms",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Phantom ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Phantom) e).shouldBurnInDay();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Phantom) e).setShouldBurnInDay(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- wolves ----
        reg.register(new BaseProperty<Boolean>("wolf.angry", "Angry",
                "Permanently angry wolf (red eyes, attacks).",
                "Wolves",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Wolf ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((Wolf) e).isAngry();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((Wolf) e).setAngry(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<String>("wolf.collar", "Collar color",
                "Wolf collar dye color.",
                "Wolves",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof Wolf ? Support.SUPPORTED : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                DyeColor c = ((Wolf) e).getCollarColor();
                return c == null ? "red" : c.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    DyeColor.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown color '" + v + "'.");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((Wolf) e).setCollarColor(DyeColor.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    DyeColor.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown color. One of: "
                            + Arrays.stream(DyeColor.values())
                                    .map(c -> c.name().toLowerCase(Locale.ROOT))
                                    .collect(Collectors.joining(", ")));
                }
            }
        });
        // ---- cats ----
        reg.register(new BaseProperty<String>("cat.collar", "Collar color",
                "Cat collar dye color.",
                "Cats",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Cat ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                DyeColor c = ((org.bukkit.entity.Cat) e).getCollarColor();
                return c == null ? "red" : c.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    DyeColor.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown color '" + v + "'.");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((org.bukkit.entity.Cat) e).setCollarColor(DyeColor.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    DyeColor.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown color.");
                }
            }
        });
        reg.register(new BaseProperty<Boolean>("cat.lying_down", "Lying down",
                "Cat rest pose.",
                "Cats",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Cat ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Cat) e).isLyingDown();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Cat) e).setLyingDown(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- foxes (plain enum: safe without a server) ----
        reg.register(new BaseProperty<Boolean>("fox.crouching", "Crouching",
                "Fox sneaking pose.",
                "Foxes",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Fox ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Fox) e).isCrouching();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Fox) e).setCrouching(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("fox.sleeping", "Sleeping",
                "Fox sleep state.",
                "Foxes",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Fox ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                // isSleeping lives on LivingEntity; the fox setter drives it.
                return ((org.bukkit.entity.Fox) e).isSleeping();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Fox) e).setSleeping(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<String>("fox.type", "Fox type",
                "Red or snow fox.",
                "Foxes",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Fox ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                org.bukkit.entity.Fox.Type t = ((org.bukkit.entity.Fox) e).getFoxType();
                return t == null ? "red" : t.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    org.bukkit.entity.Fox.Type.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown fox type '" + v + "' (red, snow).");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((org.bukkit.entity.Fox) e).setFoxType(
                        org.bukkit.entity.Fox.Type.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    org.bukkit.entity.Fox.Type.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown fox type. One of: red, snow.");
                }
            }
        });
        // ---- parrots / axolotls / mooshrooms (plain enums) ----
        reg.register(new BaseProperty<String>("parrot.variant", "Parrot color",
                "Parrot feather color.",
                "Parrots",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Parrot ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                org.bukkit.entity.Parrot.Variant v = ((org.bukkit.entity.Parrot) e).getVariant();
                return v == null ? "red" : v.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    org.bukkit.entity.Parrot.Variant.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown parrot variant '" + v + "'.");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((org.bukkit.entity.Parrot) e).setVariant(
                        org.bukkit.entity.Parrot.Variant.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    org.bukkit.entity.Parrot.Variant.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown parrot variant. One of: red, blue, green, cyan, gray.");
                }
            }
        });
        reg.register(new BaseProperty<Boolean>("axolotl.playing_dead", "Playing dead",
                "Axolotl play-dead pose (regenerates health).",
                "Axolotls",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Axolotl ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Axolotl) e).isPlayingDead();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Axolotl) e).setPlayingDead(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<String>("axolotl.variant", "Axolotl variant",
                "Axolotl color (blue is the rare one).",
                "Axolotls",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Axolotl ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                org.bukkit.entity.Axolotl.Variant v = ((org.bukkit.entity.Axolotl) e).getVariant();
                return v == null ? "lucy" : v.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    org.bukkit.entity.Axolotl.Variant.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown axolotl variant '" + v + "'.");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((org.bukkit.entity.Axolotl) e).setVariant(
                        org.bukkit.entity.Axolotl.Variant.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    org.bukkit.entity.Axolotl.Variant.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown axolotl variant. One of: lucy, wild, gold, cyan, blue.");
                }
            }
        });
        reg.register(new BaseProperty<String>("mushroomcow.variant", "Mooshroom variant",
                "Red or brown mooshroom.",
                "Mooshrooms",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.MushroomCow ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                org.bukkit.entity.MushroomCow.Variant v =
                        ((org.bukkit.entity.MushroomCow) e).getVariant();
                return v == null ? "red" : v.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    org.bukkit.entity.MushroomCow.Variant.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown mooshroom variant '" + v + "' (red, brown).");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((org.bukkit.entity.MushroomCow) e).setVariant(
                        org.bukkit.entity.MushroomCow.Variant.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    org.bukkit.entity.MushroomCow.Variant.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown mooshroom variant. One of: red, brown.");
                }
            }
        });
        // ---- goats ----
        reg.register(new BaseProperty<Boolean>("goat.left_horn", "Left horn",
                "Whether the left horn is intact (ramming can remove it).",
                "Goats",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Goat ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Goat) e).hasLeftHorn();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Goat) e).setLeftHorn(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("goat.right_horn", "Right horn",
                "Whether the right horn is intact.",
                "Goats",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Goat ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Goat) e).hasRightHorn();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Goat) e).setRightHorn(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("goat.screaming", "Screaming goat",
                "Screaming-goat sounds.",
                "Goats",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Goat ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Goat) e).isScreaming();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Goat) e).setScreaming(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- bees ----
        reg.register(new BaseProperty<Boolean>("bee.nectar", "Has nectar",
                "Whether the bee carries nectar (pollination state).",
                "Bees",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Bee ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Bee) e).hasNectar();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Bee) e).setHasNectar(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Integer>("bee.anger", "Anger ticks",
                "How long the bee stays angry. 0 = calm.",
                "Bees",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Bee ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((org.bukkit.entity.Bee) e).getAnger();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, 100000, "Anger");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((org.bukkit.entity.Bee) e).setAnger(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 100000, "anger");
            }
        });
        // ---- endermen ----
        reg.register(new BaseProperty<Boolean>("enderman.screaming", "Screaming",
                "Enderman scream state (aggro display).",
                "Endermen",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Enderman ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Enderman) e).isScreaming();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Enderman) e).setScreaming(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- steerable mounts (pigs, striders) ----
        reg.register(new BaseProperty<Boolean>("steerable.saddle", "Saddled",
                "Saddle for steerable mounts (pigs, striders).",
                "Steerable mounts",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Steerable ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Steerable) e).hasSaddle();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Steerable) e).setSaddle(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- dropped items ----
        reg.register(new BaseProperty<Integer>("item.pickup_delay", "Pickup delay",
                "Ticks before the stack can be picked up. 32767 = never (until changed).",
                "Dropped items",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Item ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((org.bukkit.entity.Item) e).getPickupDelay();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, 32767, "Pickup delay");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((org.bukkit.entity.Item) e).setPickupDelay(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 32767, "pickup delay");
            }
        });
        reg.register(new BaseProperty<Boolean>("item.unlimited_lifetime", "Never despawns",
                "Off = normal 5-minute despawn.",
                "Dropped items",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Item ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Item) e).isUnlimitedLifetime();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Item) e).setUnlimitedLifetime(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("item.mob_pickup", "Mob pickup",
                "Whether mobs can pick this stack up.",
                "Dropped items",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Item ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Item) e).canMobPickup();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Item) e).setCanMobPickup(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("item.player_pickup", "Player pickup",
                "Whether players can pick this stack up.",
                "Dropped items",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Item ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Item) e).canPlayerPickup();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Item) e).setCanPlayerPickup(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("item.will_age", "Will age (despawn)",
                "Off = frozen age timer (pairs with pickup delay for displays).",
                "Dropped items",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Item ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Item) e).willAge();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Item) e).setWillAge(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- boats ----
        reg.register(new BaseProperty<Double>("boat.max_speed", "Max speed",
                "Boat top speed in blocks/tick-ish units (vanilla ~0.4).",
                "Boats",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Boat ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Double read(Entity e) {
                return ((org.bukkit.entity.Boat) e).getMaxSpeed();
            }

            @Override
            public ValidationResult validate(Entity e, Double v) {
                return Validators.range(v, 0, 10, "Max speed");
            }

            @Override
            public void write(Entity e, Double v) {
                ((org.bukkit.entity.Boat) e).setMaxSpeed(v);
            }

            @Override
            public ParseResult<Double> parse(String input) {
                return Validators.parseDouble(input, 0, 10, "max speed");
            }
        });
        reg.register(new BaseProperty<Boolean>("boat.work_on_land", "Works on land",
                "Boats can drive on land (normally they stall).",
                "Boats",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Boat ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Boat) e).getWorkOnLand();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Boat) e).setWorkOnLand(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<String>("boat.type", "Boat wood",
                "Boat wood type.",
                "Boats",
                PropertyType.ENUM, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Boat ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                org.bukkit.entity.Boat.Type t = ((org.bukkit.entity.Boat) e).getBoatType();
                return t == null ? "oak" : t.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    org.bukkit.entity.Boat.Type.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown boat type '" + v + "'.");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((org.bukkit.entity.Boat) e).setBoatType(
                        org.bukkit.entity.Boat.Type.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    org.bukkit.entity.Boat.Type.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown boat type. One of: oak, spruce, birch, jungle, acacia, cherry, dark_oak, mangrove, bamboo.");
                }
            }
        });
        // ---- primed TNT ----
        reg.register(new BaseProperty<Integer>("tnt.fuse", "Fuse ticks",
                "Countdown to detonation (20 ticks = 1 second).",
                "Primed TNT",
                PropertyType.NUMBER, RiskLevel.DESTRUCTIVE, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.TNTPrimed ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((org.bukkit.entity.TNTPrimed) e).getFuseTicks();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, 32767, "Fuse");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((org.bukkit.entity.TNTPrimed) e).setFuseTicks(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 32767, "fuse");
            }
        });
        // ---- falling blocks ----
        reg.register(new BaseProperty<Boolean>("falling.drop_item", "Drops item",
                "Whether landing (or breaking) drops the block item.",
                "Falling blocks",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.FallingBlock ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.FallingBlock) e).getDropItem();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.FallingBlock) e).setDropItem(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Boolean>("falling.hurt_entities", "Hurts entities",
                "Anvil-style falling damage.",
                "Falling blocks",
                PropertyType.BOOLEAN, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.FallingBlock ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.FallingBlock) e).canHurtEntities();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.FallingBlock) e).setHurtEntities(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        reg.register(new BaseProperty<Integer>("falling.max_damage", "Max fall damage",
                "Damage cap for hurting falling blocks.",
                "Falling blocks",
                PropertyType.NUMBER, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.FallingBlock ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((org.bukkit.entity.FallingBlock) e).getMaxDamage();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, 1000, "Max damage");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((org.bukkit.entity.FallingBlock) e).setMaxDamage(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 1000, "max damage");
            }
        });
        // ---- explosives (fireballs, wither skulls, TNT) ----
        reg.register(new BaseProperty<Double>("explosive.yield", "Explosion yield",
                "Blast power (TNT ~4). Large values grief — confirm.",
                "Explosives",
                PropertyType.NUMBER, RiskLevel.DESTRUCTIVE, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Explosive ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Double read(Entity e) {
                return (double) ((org.bukkit.entity.Explosive) e).getYield();
            }

            @Override
            public ValidationResult validate(Entity e, Double v) {
                return Validators.range(v, 0, 127, "Yield");
            }

            @Override
            public void write(Entity e, Double v) {
                ((org.bukkit.entity.Explosive) e).setYield(v.floatValue());
            }

            @Override
            public ParseResult<Double> parse(String input) {
                return Validators.parseDouble(input, 0, 127, "yield");
            }
        });
        reg.register(new BaseProperty<Boolean>("explosive.incendiary", "Incendiary",
                "Explosion starts fires.",
                "Explosives",
                PropertyType.BOOLEAN, RiskLevel.DESTRUCTIVE, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Explosive ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Explosive) e).isIncendiary();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Explosive) e).setIsIncendiary(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- projectiles ----
        reg.register(new BaseProperty<Boolean>("projectile.bounce", "Bounces",
                "Whether the projectile bounces off blocks.",
                "Projectiles",
                PropertyType.BOOLEAN, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Projectile ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Boolean read(Entity e) {
                return ((org.bukkit.entity.Projectile) e).doesBounce();
            }

            @Override
            public ValidationResult validate(Entity e, Boolean v) {
                return ValidationResult.valid();
            }

            @Override
            public void write(Entity e, Boolean v) {
                ((org.bukkit.entity.Projectile) e).setBounce(v);
            }

            @Override
            public ParseResult<Boolean> parse(String input) {
                return Validators.parseBoolean(input);
            }
        });
        // ---- bosses (per-entity gates: dragons get dragon controls alongside
        // every generic living-entity property, never an empty page) ----
        reg.register(new BaseProperty<String>("dragon.phase", "Dragon phase",
                "Flight/attack phase of the fight (circling, strafing, charging, ...).",
                "Ender dragons",
                PropertyType.ENUM, RiskLevel.ADVANCED, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.EnderDragon ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public String read(Entity e) {
                org.bukkit.entity.EnderDragon.Phase phase =
                        ((org.bukkit.entity.EnderDragon) e).getPhase();
                return phase == null ? "?" : phase.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public ValidationResult validate(Entity e, String v) {
                try {
                    org.bukkit.entity.EnderDragon.Phase.valueOf(v.toUpperCase(Locale.ROOT));
                    return ValidationResult.valid();
                } catch (IllegalArgumentException ex) {
                    return ValidationResult.fail("Unknown phase '" + v + "'.");
                }
            }

            @Override
            public void write(Entity e, String v) {
                ((org.bukkit.entity.EnderDragon) e).setPhase(
                        org.bukkit.entity.EnderDragon.Phase.valueOf(v.toUpperCase(Locale.ROOT)));
            }

            @Override
            public ParseResult<String> parse(String input) {
                String s = input.trim().toLowerCase(Locale.ROOT);
                try {
                    org.bukkit.entity.EnderDragon.Phase.valueOf(s.toUpperCase(Locale.ROOT));
                    return ParseResult.ok(s);
                } catch (IllegalArgumentException ex) {
                    return ParseResult.fail("Unknown phase. Use one of: "
                            + Arrays.stream(org.bukkit.entity.EnderDragon.Phase.values())
                                    .map(p -> p.name().toLowerCase(Locale.ROOT))
                                    .collect(Collectors.joining(", ")));
                }
            }
        });
        reg.register(new BaseProperty<Integer>("wither.invulnerable_ticks", "Wither armor ticks",
                "Spawn-armor timer. 0 = vulnerable right now.",
                "Withers",
                PropertyType.NUMBER, RiskLevel.NORMAL, true) {
            @Override
            public Support support(Entity e) {
                return e instanceof org.bukkit.entity.Wither ? Support.SUPPORTED
                        : Support.UNSUPPORTED;
            }

            @Override
            public Integer read(Entity e) {
                return ((org.bukkit.entity.Wither) e).getInvulnerableTicks();
            }

            @Override
            public ValidationResult validate(Entity e, Integer v) {
                return Validators.range(v, 0, 72000, "Invulnerable ticks");
            }

            @Override
            public void write(Entity e, Integer v) {
                ((org.bukkit.entity.Wither) e).setInvulnerableTicks(v);
            }

            @Override
            public ParseResult<Integer> parse(String input) {
                return Validators.parseInt(input, 0, 72000, "invulnerable ticks");
            }
        });
    }
}
