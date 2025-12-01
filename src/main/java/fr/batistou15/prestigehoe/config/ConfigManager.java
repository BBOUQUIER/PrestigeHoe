package fr.batistou15.prestigehoe.config;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ConfigManager {

    private final PrestigeHoePlugin plugin;

    private FileConfiguration mainConfig;
    private FileConfiguration messagesConfig;
    private FileConfiguration enchantsConfig;
    private FileConfiguration cropsConfig;
    private FileConfiguration skinsConfig;
    private FileConfiguration prestigeConfig;
    private FileConfiguration prestigeShopConfig;
    private FileConfiguration gradesConfig;
    private FileConfiguration boostsConfig;
    private FileConfiguration leaderboardsConfig;
    private FileConfiguration eventsConfig;
    private FileConfiguration guisConfig;

    public ConfigManager(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    public void reloadAll() {
        this.mainConfig = load("config.yml");
        this.messagesConfig = load("messages.yml");
        this.enchantsConfig = load("enchants.yml");
        this.cropsConfig = load("crops.yml");
        this.skinsConfig = load("skins.yml");
        this.prestigeConfig = load("prestige.yml");
        this.prestigeShopConfig = load("prestige_shop.yml");
        this.gradesConfig = load("grades.yml");
        this.boostsConfig = load("boosts.yml");
        this.leaderboardsConfig = load("leaderboards.yml");
        this.eventsConfig = load("events.yml");
        this.guisConfig = load("guis.yml");
    }

    private FileConfiguration load(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            // Les fichiers par défaut sont déjà copiés dans onEnable via saveDefaultResourceIfNotExists
            plugin.getLogger().warning("Fichier de config manquant: " + fileName + " (un fichier par défaut aurait dû être copié)");
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        return cfg;
    }

    // ========= GETTERS =========

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getEnchantsConfig() {
        return enchantsConfig;
    }

    public FileConfiguration getCropsConfig() {
        return cropsConfig;
    }

    public FileConfiguration getSkinsConfig() {
        return skinsConfig;
    }

    public FileConfiguration getPrestigeConfig() {
        return prestigeConfig;
    }

    public FileConfiguration getPrestigeShopConfig() {
        return prestigeShopConfig;
    }

    public FileConfiguration getGradesConfig() {
        return gradesConfig;
    }

    public FileConfiguration getBoostsConfig() {
        return boostsConfig;
    }

    public FileConfiguration getLeaderboardsConfig() {
        return leaderboardsConfig;
    }

    public FileConfiguration getEventsConfig() {
        return eventsConfig;
    }

    public FileConfiguration getGuisConfig() {
        return guisConfig;
    }

    // (Optionnel) si plus tard tu veux sauvegarder des changements en live :
    public void save(FileConfiguration cfg, String fileName) {
        if (cfg == null) return;

        File file = new File(plugin.getDataFolder(), fileName);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder " + fileName, e);
        }
    }
}
