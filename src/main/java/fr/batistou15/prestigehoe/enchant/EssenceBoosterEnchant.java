package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class EssenceBoosterEnchant implements HoeEnchant {

    public static final String ID = "essence_booster";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    private String stackMode;           // ADD / MULTIPLY
    private double essenceBonusPerLevel; // ex: 0.01 = +1% par niveau (ADD) ou x1.01 (MULTIPLY)

    public EssenceBoosterEnchant(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return ID;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getDefaultLevel() {
        return defaultLevel;
    }

    public int getLevel(HoeData hoe) {
        if (hoe == null) return 0;
        return hoe.getEnchantLevel(ID, defaultLevel);
    }

    /**
     * Retourne le multiplicateur d'Essence total (>= 1.0)
     * basé sur stack-mode et essence-bonus-per-level.
     */
    public double getMultiplier(HoeData hoe) {
        if (!enabled || hoe == null) return 1.0;

        int level = getLevel(hoe);
        if (level <= 0) return 1.0;

        if ("MULTIPLY".equalsIgnoreCase(stackMode)) {
            // multiplicateur = (1 + bonus)^level
            double factor = 1.0 + essenceBonusPerLevel;
            return Math.max(1.0, Math.pow(factor, level));
        } else {
            // ADD (défaut) -> 1 + bonus*level
            double add = 1.0 + (essenceBonusPerLevel * level);
            return Math.max(1.0, add);
        }
    }

    @Override
    public void reload(ConfigManager configManager) {
        FileConfiguration cfg = configManager.getEnchantsConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("enchants." + ID);

        if (sec == null) {
            plugin.getLogger().warning("[EssenceBoosterEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            stackMode = "ADD";
            essenceBonusPerLevel = 0.0;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        maxLevel = sec.getInt("level.max-level", 100);
        defaultLevel = sec.getInt("level.default-level", 0);

        ConfigurationSection settings = sec.getConfigurationSection("settings");
        if (settings != null) {
            stackMode = settings.getString("stack-mode", "ADD");
            essenceBonusPerLevel = settings.getDouble("essence-bonus-per-level", 0.0);
        } else {
            stackMode = "ADD";
            essenceBonusPerLevel = 0.0;
        }

        plugin.getLogger().info(String.format(
                "[PrestigeHoe] [EssenceBoosterEnchant] enabled=%s, maxLevel=%d, defaultLevel=%d, stackMode=%s, bonusPerLevel=%.4f",
                enabled, maxLevel, defaultLevel, stackMode, essenceBonusPerLevel
        ));
    }
}
