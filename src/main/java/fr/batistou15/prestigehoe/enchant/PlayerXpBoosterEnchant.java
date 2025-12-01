package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class PlayerXpBoosterEnchant implements HoeEnchant {

    public static final String ID = "player_xp_booster";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    // "ADD" ou "MULTIPLY"
    private String stackMode;
    // ex: 0.01 = +1% par niveau
    private double xpBonusPerLevel;

    public PlayerXpBoosterEnchant(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return ID;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getDefaultLevel() {
        return defaultLevel;
    }

    /**
     * Multiplicateur appliqué à l'XP vanilla du joueur.
     * Retourne toujours >= 1.0.
     */
    public double getXpMultiplier(HoeData hoe) {
        if (!enabled || hoe == null) {
            return 1.0;
        }

        int level = hoe.getEnchantLevel(ID, 0);
        if (level <= 0) {
            return 1.0;
        }

        if ("MULTIPLY".equalsIgnoreCase(stackMode)) {
            // multiplicateur = (1 + bonus)^level
            double factor = 1.0 + xpBonusPerLevel;
            return Math.max(1.0, Math.pow(factor, level));
        } else {
            // ADD (défaut) -> 1 + bonus*level
            double add = 1.0 + (xpBonusPerLevel * level);
            return Math.max(1.0, add);
        }
    }

    @Override
    public void reload(ConfigManager configManager) {
        FileConfiguration cfg = configManager.getEnchantsConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("enchants." + ID);

        if (sec == null) {
            plugin.getLogger().warning("[PlayerXpBoosterEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            stackMode = "ADD";
            xpBonusPerLevel = 0.0;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        maxLevel = sec.getInt("level.max-level", 100);
        defaultLevel = sec.getInt("level.default-level", 0);

        ConfigurationSection settings = sec.getConfigurationSection("settings");
        if (settings != null) {
            stackMode = settings.getString("stack-mode", "ADD");
            // ⚠️ correspond à ton enchants.yml: settings.xp-multiplier-per-level
            xpBonusPerLevel = settings.getDouble("xp-multiplier-per-level", 0.01);
        } else {
            stackMode = "ADD";
            xpBonusPerLevel = 0.0;
        }

        plugin.getLogger().info(String.format(
                "[PrestigeHoe] [PlayerXpBoosterEnchant] enabled=%s, maxLevel=%d, defaultLevel=%d, stackMode=%s, xpBonusPerLevel=%.4f",
                enabled, maxLevel, defaultLevel, stackMode, xpBonusPerLevel
        ));
    }
}
