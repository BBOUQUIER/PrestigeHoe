package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProcBoosterEnchant implements HoeEnchant {

    public static final String ID = "proc_booster";

    public enum StackMode {
        ADD,
        MULTIPLY
    }

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    private StackMode stackMode;
    private double procMultiplierPerLevel;
    private final Set<String> affectedEnchants = new HashSet<>();

    public ProcBoosterEnchant(PrestigeHoePlugin plugin) {
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

    public double getProcMultiplierPerLevel() {
        return procMultiplierPerLevel;
    }

    @Override
    public void reload(ConfigManager configManager) {
        FileConfiguration cfg = configManager.getEnchantsConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("enchants." + ID);

        if (sec == null) {
            plugin.getLogger().warning("[ProcBoosterEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            procMultiplierPerLevel = 0.0D;
            affectedEnchants.clear();
            stackMode = StackMode.MULTIPLY;
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
            String rawMode = settingsSec.getString("stack-mode", "MULTIPLY").toUpperCase(Locale.ROOT);
            try {
                stackMode = StackMode.valueOf(rawMode);
            } catch (IllegalArgumentException ex) {
                stackMode = StackMode.MULTIPLY;
            }

            procMultiplierPerLevel = settingsSec.getDouble("proc-multiplier-per-level", 0.01D);

            affectedEnchants.clear();
            List<String> list = settingsSec.getStringList("affected-enchants");
            if (list != null) {
                for (String id : list) {
                    if (id == null || id.isEmpty()) continue;
                    affectedEnchants.add(id.toLowerCase(Locale.ROOT));
                }
            }
        } else {
            stackMode = StackMode.MULTIPLY;
            procMultiplierPerLevel = 0.01D;
            affectedEnchants.clear();
        }

        plugin.getLogger().info(String.format(Locale.US,
                "[PrestigeHoe] [ProcBoosterEnchant] enabled=%s, maxLevel=%d, perLevel=%.4f, affected=%d, mode=%s",
                enabled, maxLevel, procMultiplierPerLevel, affectedEnchants.size(), stackMode
        ));
    }

    private boolean affects(String enchantId) {
        if (enchantId == null) return false;
        return affectedEnchants.contains(enchantId.toLowerCase(Locale.ROOT));
    }

    /**
     * Multiplier global "théorique" pour l'affichage (menu),
     * indépendant de l'enchant ciblé.
     */
    public double getGlobalProcMultiplier(HoeData hoe) {
        if (!enabled || hoe == null) return 1.0D;

        int level = hoe.getEnchantLevel(ID, 0);
        if (level <= 0) return 1.0D;

        double multi;
        if (stackMode == StackMode.ADD) {
            multi = 1.0D + procMultiplierPerLevel * level;
        } else {
            // MULTIPLY
            multi = Math.pow(1.0D + procMultiplierPerLevel, level);
        }
        if (multi < 0.0D) multi = 0.0D;
        return multi;
    }

    /**
     * Multiplier à appliquer à la chance de proc d'un enchant donné
     * SI cet enchant est dans la liste affected-enchants.
     */
    public double getProcMultiplierFor(String enchantId, HoeData hoe) {
        if (!enabled || hoe == null) return 1.0D;
        if (!affects(enchantId)) return 1.0D;

        int level = hoe.getEnchantLevel(ID, 0);
        if (level <= 0) return 1.0D;

        double multi;
        if (stackMode == StackMode.ADD) {
            multi = 1.0D + procMultiplierPerLevel * level;
        } else {
            multi = Math.pow(1.0D + procMultiplierPerLevel, level);
        }
        if (multi < 0.0D) multi = 0.0D;
        return multi;
    }
}
