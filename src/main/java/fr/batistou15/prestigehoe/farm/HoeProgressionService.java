package fr.batistou15.prestigehoe.farm;

import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.formula.FormulaEngine;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HoeProgressionService {

    private final FormulaEngine formulaEngine;
    private final ConfigManager configManager;

    public HoeProgressionService(FormulaEngine formulaEngine, ConfigManager configManager) {
        this.formulaEngine = formulaEngine;
        this.configManager = configManager;
    }

    /**
     * XP requise pour passer du niveau actuel au suivant.
     * Utilise formulas.xp_required_per_level.
     */
    public double getXpRequiredForLevel(HoeData hoe) {
        if (hoe == null) return 100.0;

        int level = hoe.getLevel();
        Map<String, Double> vars = new HashMap<>();
        vars.put("level", (double) level);

        return formulaEngine.eval(
                "formulas.xp_required_per_level",
                vars,
                200.0
        );
    }

    /**
     * Niveau max de la houe en fonction du prestige.
     */
    public int getMaxHoeLevel(HoeData hoe) {
        if (hoe == null) return 50;

        int prestige = hoe.getPrestige();
        Map<String, Double> vars = new HashMap<>();
        vars.put("prestige", (double) prestige);

        double max = formulaEngine.eval(
                "formulas.hoe_max_level",
                vars,
                50.0
        );

        if (max < 1) max = 1;
        return (int) Math.round(max);
    }

    /**
     * Applique les bonus d'XP de la houe via l'enchant hoe_xp_booster.
     */
    public double applyHoeXpBoost(HoeData hoe, double baseAmount) {
        if (hoe == null || baseAmount <= 0) {
            return baseAmount;
        }

        var enchCfg = configManager.getEnchantsConfig();
        var rootSec = enchCfg.getConfigurationSection("enchants.hoe_xp_booster");
        if (rootSec == null || !rootSec.getBoolean("enabled", true)) {
            return baseAmount;
        }

        int level = hoe.getEnchantLevel("hoe_xp_booster", 0);
        if (level <= 0) {
            return baseAmount;
        }

        var settings = rootSec.getConfigurationSection("settings");
        if (settings == null) {
            return baseAmount;
        }

        String modeRaw = settings.getString("stack-mode", "ADD").toUpperCase(Locale.ROOT);
        double perLevel = settings.getDouble("xp-bonus-per-level", 0.01);

        double multiplier;
        switch (modeRaw) {
            case "MULTIPLY":
                multiplier = Math.pow(1.0 + perLevel, level);
                break;

            case "ADD":
            default:
                multiplier = 1.0 + perLevel * level;
                break;
        }

        if (multiplier <= 0) {
            multiplier = 1.0;
        }

        return baseAmount * multiplier;
    }
}
