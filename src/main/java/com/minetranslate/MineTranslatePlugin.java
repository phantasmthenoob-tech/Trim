package com.minetranslate;

import com.minetranslate.command.LangCommands;
import com.minetranslate.command.TranslateCommand;
import com.minetranslate.command.UtilityCommands;
import com.minetranslate.data.PlayerDataStore;
import com.minetranslate.gui.TranslateGUI;
import com.minetranslate.listener.ChatListener;
import com.minetranslate.translate.TranslateService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** MineTranslate - Google Translate for Minecraft (Paper 1.21.x). */
public class MineTranslatePlugin extends JavaPlugin {

    private TranslateService translateService;
    private PlayerDataStore store;
    private TranslateGUI gui;
    private final MiniMessage mm = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadServices();
        store = new PlayerDataStore(new File(getDataFolder(), "playerdata.yml"));

        gui = new TranslateGUI(this);
        getServer().getPluginManager().registerEvents(gui, this);

        TranslateCommand tr = new TranslateCommand(this);
        getCommand("translate").setExecutor(tr);
        getCommand("translate").setTabCompleter(tr);
        getCommand("trgui").setExecutor(tr);

        LangCommands langs = new LangCommands(this);
        getCommand("mylang").setExecutor(langs);
        getCommand("mylang").setTabCompleter(langs);
        getCommand("trchat").setExecutor(langs);
        getCommand("trchat").setTabCompleter(langs);
        getCommand("languages").setExecutor(langs);
        getCommand("languages").setTabCompleter(langs);
        getCommand("translate-reload").setExecutor(langs);

        UtilityCommands util = new UtilityCommands(this);
        getCommand("trsign").setExecutor(util);
        getCommand("trsign").setTabCompleter(util);
        getCommand("trbook").setExecutor(util);
        getCommand("trbook").setTabCompleter(util);

        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getLogger().info("MineTranslate enabled. Default language: " + getConfig().getString("default-language", "en"));
    }

    public void reloadServices() {
        reloadConfig();
        int timeout = getConfig().getInt("request-timeout-seconds", 10);
        int maxLen = getConfig().getInt("max-text-length", 1500);
        int cacheSize = getConfig().getInt("cache-size", 500);
        long expire = getConfig().getLong("cache-expire-minutes", 60);
        translateService = new TranslateService(timeout, maxLen, cacheSize, expire);
    }

    @Override
    public void onDisable() {
        getLogger().info("MineTranslate disabled.");
    }

    public TranslateService service() {
        return translateService;
    }

    public PlayerDataStore store() {
        return store;
    }

    public TranslateGUI gui() {
        return gui;
    }

    public MiniMessage mm() {
        return mm;
    }

    /** Sends a prefixed MiniMessage string to any command sender. */
    public void send(org.bukkit.command.CommandSender to, String mini) {
        String prefix = getConfig().getString("messages.prefix",
                "<dark_gray>[MineTranslate]</dark_gray> ");
        to.sendMessage(mm.deserialize(prefix + mini));
    }
}
