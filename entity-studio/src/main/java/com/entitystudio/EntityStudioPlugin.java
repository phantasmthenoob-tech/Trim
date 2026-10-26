package com.entitystudio;

import com.entitystudio.behavior.AllegianceService;
import com.entitystudio.command.StudioCommand;
import com.entitystudio.gui.StudioGUI;
import com.entitystudio.history.HistoryService;
import com.entitystudio.permission.Gatekeeper;
import com.entitystudio.permission.StudioPermissions;
import com.entitystudio.persistence.ManagedStore;
import com.entitystudio.property.PropertyRegistry;
import com.entitystudio.selection.EntitySelector;
import com.entitystudio.session.SessionManager;
import com.entitystudio.snapshot.SnapshotService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Entity Studio — visual entity editor. Lifecycle + service registry. */
public class EntityStudioPlugin extends JavaPlugin implements SessionManager.EntityStudioAccess {

    private Gatekeeper gate;
    private SessionManager sessions;
    private PropertyRegistry properties;
    private HistoryService history;
    private AllegianceService allegiance;
    private SnapshotService snapshots;
    private ManagedStore managedStore;
    private StudioGUI gui;
    private EntitySelector selector;
    private YamlConfiguration messages;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> protectedEntities = new HashSet<>();
    private File activationFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfiguration();

        boolean active = loadActivation();
        gate = new Gatekeeper(getConfig().getString("settings.owner-name", ""), active);

        sessions = new SessionManager();
        properties = new PropertyRegistry();
        history = new HistoryService();
        history.setMaxEntries(getConfig().getInt("settings.history.max-entries-per-player", 500));
        allegiance = new AllegianceService(this);
        snapshots = new SnapshotService(this);
        managedStore = new ManagedStore(this);
        gui = new StudioGUI(this);
        selector = new EntitySelector(this);

        getServer().getPluginManager().registerEvents(selector, this);
        getServer().getPluginManager().registerEvents(gui, this);
        allegiance.start();

        StudioCommand cmd = new StudioCommand(this);
        getCommand("entitystudio").setExecutor(cmd);
        getCommand("entitystudio").setTabCompleter(cmd);
        getCommand("entity").setExecutor(cmd);
        getCommand("entity").setTabCompleter(cmd);

        if (getConfig().getBoolean("settings.behavior.persist-managed-settings", true)) {
            managedStore.load();
        }

        String owner = gate.ownerName();
        if (!owner.isEmpty()) {
            getLogger().info("Owner override active for '" + gate.ownerName()
                    + "' (settings.owner-name in config.yml — visible, change or clear to disable)."
                    + " Activation is " + (gate.isActive() ? "ON" : "OFF") + ".");
        } else {
            getLogger().info("No owner override configured. Activation is "
                    + (gate.isActive() ? "ON" : "OFF") + ".");
        }
        getLogger().info("EntityStudio enabled.");
    }

    @Override
    public void onDisable() {
        if (managedStore != null
                && getConfig().getBoolean("settings.behavior.persist-managed-settings", true)) {
            managedStore.save();
        }
        saveActivation();
        getLogger().info("EntityStudio disabled.");
    }

    public void reloadConfiguration() {
        reloadConfig();
        saveDefaultConfig();
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
            messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        }
        activationFile = new File(getDataFolder(), "activation.yml");
        protectedEntities.clear();
        for (String s : getConfig().getStringList("settings.security.protected-entities")) {
            try {
                protectedEntities.add(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                getLogger().warning("Ignoring bad protected-entities UUID: " + s);
            }
        }
        if (history != null) {
            history.setMaxEntries(getConfig().getInt("settings.history.max-entries-per-player", 500));
        }
    }

    private boolean loadActivation() {
        File f = new File(getDataFolder(), "activation.yml");
        if (!f.exists()) {
            return false;
        }
        return YamlConfiguration.loadConfiguration(f).getBoolean("active", false);
    }

    private void saveActivation() {
        if (gate == null || activationFile == null) {
            return;
        }
        YamlConfiguration yaml = activationFile.exists()
                ? YamlConfiguration.loadConfiguration(activationFile)
                : new YamlConfiguration();
        yaml.set("active", gate.isActive());
        try {
            yaml.save(activationFile);
        } catch (Exception e) {
            getLogger().warning("Could not save activation.yml: " + e.getMessage());
        }
    }

    /** Gate check shared by commands, GUI entry points and selection. */
    public boolean canUse(Player player) {
        if (player == null) {
            return false;
        }
        Gatekeeper.Access access = gate.decide(player.getName(), player.isOp());
        if (access != Gatekeeper.Access.ALLOW) {
            send(player, msg("locked"));
            return false;
        }
        return true;
    }

    /** Permission check with transparent owner bypass. */
    public boolean permitted(Player player, String node) {
        return gate.ownerBypassesPermissions(player.getName()) || player.hasPermission(node);
    }

    /** Target-side protections: protected list + other-player rule. */
    public boolean checkTargetAccess(Player player, Entity target) {
        if (protectedEntities.contains(target.getUniqueId())) {
            send(player, msg("entity-protected"));
            return false;
        }
        if (target instanceof Player other && !other.getUniqueId().equals(player.getUniqueId())
                && getConfig().getBoolean("settings.security.protect-other-players", true)
                && !gate.ownerBypassesPermissions(player.getName())
                && !player.hasPermission(StudioPermissions.PLAYER_EDIT)) {
            send(player, msg("player-protected"));
            return false;
        }
        return true;
    }

    public void send(Player player, String mini) {
        player.sendMessage(mm.deserialize(msg("prefix") + mini));
    }

    public String msg(String key) {
        String v = messages == null ? null : messages.getString(key);
        return v == null ? key : v;
    }

    public String msg(String key, String placeholder, String value) {
        return msg(key).replace("<" + placeholder + ">", value);
    }

    // ---- SessionManager.EntityStudioAccess ----
    @Override
    public Gatekeeper gate() {
        return gate;
    }

    @Override
    public Set<UUID> protectedEntities() {
        return protectedEntities;
    }

    @Override
    public boolean preventConcurrentEditors() {
        return getConfig().getBoolean("settings.editor.prevent-concurrent-editors", true);
    }

    // ---- services ----
    public SessionManager sessions() {
        return sessions;
    }

    public PropertyRegistry properties() {
        return properties;
    }

    public HistoryService history() {
        return history;
    }

    public AllegianceService allegiance() {
        return allegiance;
    }

    public SnapshotService snapshots() {
        return snapshots;
    }

    public ManagedStore managedStore() {
        return managedStore;
    }

    public StudioGUI gui() {
        return gui;
    }

    public EntitySelector selector() {
        return selector;
    }

    public MiniMessage mm() {
        return mm;
    }

    public void persistActivation() {
        saveActivation();
    }
}
