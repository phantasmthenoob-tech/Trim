package com.minetranslate.data;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;

/** Persists per-player language + auto-translate flag in playerdata.yml. */
public class PlayerDataStore {

    private final File file;
    private YamlConfiguration yaml;
    private final Map<UUID, String> langCache = new HashMap<>();
    private final Map<UUID, Boolean> autoCache = new HashMap<>();

    public PlayerDataStore(File file) {
        this.file = file;
        reload();
    }

    public synchronized void reload() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        langCache.clear();
        autoCache.clear();
    }

    public synchronized String language(UUID id) {
        if (langCache.containsKey(id)) {
            return langCache.get(id);
        }
        String v = yaml.getString("players." + id + ".lang", null);
        langCache.put(id, v);
        return v;
    }

    public synchronized boolean autoTranslate(UUID id) {
        if (autoCache.containsKey(id)) {
            return autoCache.get(id);
        }
        boolean v = yaml.getBoolean("players." + id + ".auto", false);
        autoCache.put(id, v);
        return v;
    }

    public synchronized void setLanguage(UUID id, String lang) {
        langCache.put(id, lang);
        yaml.set("players." + id + ".lang", lang);
        save();
    }

    public synchronized void setAuto(UUID id, boolean auto) {
        autoCache.put(id, auto);
        yaml.set("players." + id + ".auto", auto);
        save();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }
}
