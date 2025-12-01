package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public class SpeedEnchant implements HoeEnchant {

    public static final String ID = "speed";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    // settings
    private String stackMode;            // "ADD" / "MULTIPLY"
    private double speedBonusPerLevel;   // ex: 0.01 = +1% par niveau
    private double maxMultiplier;        // pour éviter les abus (ex: x2 max)

    public SpeedEnchant(PrestigeHoePlugin plugin) {
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

    @Override
    public void reload(ConfigManager configManager) {
        FileConfiguration cfg = configManager.getEnchantsConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("enchants." + ID);

        if (sec == null) {
            plugin.getLogger().warning("[SpeedEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            stackMode = "ADD";
            speedBonusPerLevel = 0.0D;
            maxMultiplier = 2.0D;
            return;
        }

        enabled = sec.getBoolean("enabled", true);

        ConfigurationSection levelSec = sec.getConfigurationSection("level");
        if (levelSec != null) {
            maxLevel = levelSec.getInt("max-level", 100);
            defaultLevel = levelSec.getInt("default-level", 0);
        } else {
            maxLevel = 100;
            defaultLevel = 0;
        }

        ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
        if (settingsSec != null) {
            // On garde la compatibilité avec l’ancien config (mode, ticks, etc.)
            // mais on ajoute nos propres clefs.
            stackMode = settingsSec.getString("stack-mode", "ADD");
            speedBonusPerLevel = settingsSec.getDouble("speed-bonus-per-level", 0.01D); // +1% / lvl par défaut
            maxMultiplier = settingsSec.getDouble("max-multiplier", 2.0D);
            if (maxMultiplier < 1.0D) {
                maxMultiplier = 1.0D;
            }
        } else {
            stackMode = "ADD";
            speedBonusPerLevel = 0.01D;
            maxMultiplier = 2.0D;
        }

        plugin.getLogger().info(String.format(
                Locale.US,
                "[PrestigeHoe] [SpeedEnchant] enabled=%s, maxLevel=%d, bonusPerLevel=%.4f, maxMultiplier=%.2f",
                enabled, maxLevel, speedBonusPerLevel, maxMultiplier
        ));
    }

    /**
     * Retourne un multiplicateur de vitesse à appliquer à la walkSpeed du joueur.
     *
     * Exemple :
     *  - ADD, speedBonusPerLevel=0.01, level=10  ->  1 + 0.01*10 = 1.10  => +10%
     *  - MULTIPLY, speedBonusPerLevel=0.01, level=10 -> (1+0.01)^10 ≈ 1.1046
     */
    public double getSpeedMultiplier(HoeData hoeData) {
        if (!enabled || hoeData == null) return 1.0D;

        int level = hoeData.getEnchantLevel(ID, 0);
        if (level <= 0) return 1.0D;

        if (speedBonusPerLevel <= 0.0D) return 1.0D;

        String mode = (stackMode == null ? "ADD" : stackMode).toUpperCase(Locale.ROOT);
        double mult;

        switch (mode) {
            case "MULTIPLY":
                mult = Math.pow(1.0D + speedBonusPerLevel, level);
                break;

            case "ADD":
            default:
                mult = 1.0D + speedBonusPerLevel * level;
                break;
        }

        if (mult > maxMultiplier) {
            mult = maxMultiplier;
        }

        return mult;
    }
}
