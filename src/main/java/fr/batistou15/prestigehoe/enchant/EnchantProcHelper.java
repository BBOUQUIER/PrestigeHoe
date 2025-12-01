package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;

public final class EnchantProcHelper {

    private EnchantProcHelper() {
    }

    public static class ProcData {
        public final double baseCurrent;
        public final double baseNext;
        public final double totalCurrent;
        public final double totalNext;

        public ProcData(double baseCurrent, double baseNext,
                        double totalCurrent, double totalNext) {
            this.baseCurrent = baseCurrent;
            this.baseNext = baseNext;
            this.totalCurrent = totalCurrent;
            this.totalNext = totalNext;
        }
    }

    /**
     * @param enchantId    id de l'enchant concerné ("token_finder", "essence_pouch", etc.)
     */
    public static ProcData computeProcData(PrestigeHoePlugin plugin,
                                           PlayerProfile profile,
                                           HoeData hoe,
                                           String enchantId,
                                           double baseChance,
                                           double chancePerLevel,
                                           double maxChance,
                                           int currentLevel) {
        if (maxChance <= 0.0D) maxChance = 1.0D;

        // --- chance de base en fonction du niveau de l'enchant ---
        double baseCurrent;
        double baseNext;

        if (currentLevel <= 0) {
            baseCurrent = 0.0D;
            baseNext = baseChance;
        } else {
            baseCurrent = baseChance + chancePerLevel * (currentLevel - 1);
            baseNext = baseChance + chancePerLevel * currentLevel;
        }

        baseCurrent = clamp(baseCurrent, 0.0D, maxChance);
        baseNext = clamp(baseNext, 0.0D, maxChance);

        double globalMultiplier = 1.0D;

        // 1) Perks prestige (ENCHANT_PROC_MULTIPLIER)
        try {
            PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
            if (bonusService != null && profile != null) {
                double perkMulti = bonusService.getPerkMultiplier(
                        profile,
                        PrestigeBonusService.BonusContext.ENCHANT_PROC_MULTIPLIER
                );
                if (perkMulti > 0.0D) {
                    globalMultiplier *= perkMulti;
                }
            }
        } catch (Throwable ignored) {}

        // 2) Enchant proc_booster (uniquement si l'enchant est affecté)
        try {
            if (plugin.getEnchantManager() != null && hoe != null) {
                ProcBoosterEnchant booster = plugin.getEnchantManager().getProcBoosterEnchant();
                if (booster != null && booster.isEnabled()) {
                    double boosterMulti = booster.getProcMultiplierFor(enchantId, hoe);
                    if (boosterMulti > 0.0D) {
                        globalMultiplier *= boosterMulti;
                    }
                }
            }
        } catch (Throwable ignored) {}

        double totalCurrent = clamp(baseCurrent * globalMultiplier, 0.0D, maxChance);
        double totalNext = clamp(baseNext * globalMultiplier, 0.0D, maxChance);

        return new ProcData(baseCurrent, baseNext, totalCurrent, totalNext);
    }

    private static double clamp(double val, double min, double max) {
        if (val < min) return min;
        if (val > max) return max;
        return val;
    }
}
