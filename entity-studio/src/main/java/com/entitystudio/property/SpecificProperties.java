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
    }
}
