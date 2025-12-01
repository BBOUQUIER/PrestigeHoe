package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class MoneyBoosterEnchant implements HoeEnchant {

    public static final String ID = "money_booster";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    private String stackMode;           // ADD / MULTIPLY
    private double moneyBonusPerLevel;  // 0.01 = 1% par niveau

    public MoneyBoosterEnchant(PrestigeHoePlugin plugin) {
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
     * Retourne le multiplicateur d'argent (>= 1.0) appliqué dans FarmService.
     */
    public double getMoneyMultiplier(HoeData hoe) {
        if (!enabled || hoe == null) return 1.0;

        int level = getLevel(hoe);
        if (level <= 0) return 1.0;

        if ("MULTIPLY".equalsIgnoreCase(stackMode)) {
            double factor = 1.0 + moneyBonusPerLevel;
            return Math.max(1.0, Math.pow(factor, level));
        } else {
            double add = 1.0 + (moneyBonusPerLevel * level);
            return Math.max(1.0, add);
        }
    }

    @Override
    public void reload(ConfigManager configManager) {
        FileConfiguration cfg = configManager.getEnchantsConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("enchants." + ID);

        if (sec == null) {
            plugin.getLogger().warning("[MoneyBoosterEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            stackMode = "ADD";
            moneyBonusPerLevel = 0.0;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        maxLevel = sec.getInt("level.max-level", 100);
        defaultLevel = sec.getInt("level.default-level", 0);

        ConfigurationSection settings = sec.getConfigurationSection("settings");
        if (settings != null) {
            stackMode = settings.getString("stack-mode", "ADD");
            moneyBonusPerLevel = settings.getDouble("money-bonus-per-level", 0.0);
        } else {
            stackMode = "ADD";
            moneyBonusPerLevel = 0.0;
        }

        plugin.getLogger().info(String.format(
                "[PrestigeHoe] [MoneyBoosterEnchant] enabled=%s, maxLevel=%d, defaultLevel=%d, stackMode=%s, moneyBonusPerLevel=%.4f",
                enabled, maxLevel, defaultLevel, stackMode, moneyBonusPerLevel
        ));
    }
}
