package fr.batistou15.prestigehoe.farm;

import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.formula.FormulaEngine;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService.BonusContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Service responsable des calculs de RÉCOMPENSES :
 * - Essence
 * - Argent
 *
 * Il combine :
 *  - les valeurs de base (crops.yml)
 *  - les formules (formulas.* dans config.yml, via FormulaEngine)
 *  - le multiplicateur de prestige (en fonction du prestige de la houe)
 *  - le multiplicateur de PERKS (prestige_shop.yml, via PrestigeBonusService)
 *
 * IMPORTANT :
 *  - Les bonus de prestige (niveau de prestige de la houe) restent gérés ici
 *    via formulas.prestige_multiplier.
 *  - Les PERKS sont complètement séparés dans PrestigeBonusService
 *    (prestige_shop.yml) et injectés ici via la variable
 *    "permanent_shop_multi" des formules.
 */
public class RewardFormulaService {

    private final FormulaEngine formulaEngine;
    private final PrestigeBonusService prestigeBonusService;

    public RewardFormulaService(FormulaEngine formulaEngine,
                                PrestigeBonusService prestigeBonusService) {
        this.formulaEngine = formulaEngine;
        this.prestigeBonusService = prestigeBonusService;
    }

    // =========================================================
    //           MÉTHODES PUBLIQUES UTILISÉES PAR LE FARM
    // =========================================================

    /**
     * Calcul du gain d'Essence total pour un break de blocs.
     *
     * @param cropEssence essence de base définie dans crops.yml (par bloc)
     * @param quantity    nombre logique d'items (après Fortune & co)
     * @param hoe         données de la houe (prestige, etc.)
     * @param profile     profil du joueur (perks, grades, etc.)
     */
    public double computeEssenceGain(double cropEssence,
                                     double quantity,
                                     HoeData hoe,
                                     PlayerProfile profile) {
        if (cropEssence <= 0.0D || quantity <= 0.0D || hoe == null) {
            return 0.0D;
        }

        double prestigeMulti = getPrestigeMultiplier(hoe);

        double perksMulti = 1.0D;
        if (prestigeBonusService != null && profile != null) {
            perksMulti = prestigeBonusService.getPerkMultiplier(profile, BonusContext.ESSENCE);
        }

        Map<String, Double> vars = new HashMap<>();
        vars.put("crop_essence", cropEssence);
        vars.put("quantity", quantity);

        vars.put("prestige_multi", prestigeMulti);
        vars.put("grades_multi", 1.0D);
        vars.put("skins_multi", 1.0D);
        vars.put("boosts_multi", 1.0D);
        vars.put("events_multi", 1.0D);
        vars.put("enchants_multi", 1.0D);

        // 🎯 Perks prestige shop
        vars.put("permanent_shop_multi", perksMulti);

        double fallback = cropEssence * quantity * prestigeMulti * perksMulti;

        return formulaEngine.eval("formulas.essence_gain", vars, fallback);
    }

    /**
     * Calcul du gain d'ARGENT total pour un break de blocs.
     *
     * @param basePrice prix de base défini dans crops.yml (par item vendu)
     * @param quantity  nombre d'items vendus (après Fortune & Autosell)
     * @param hoe       données de la houe
     * @param profile   profil du joueur (perks, grades, etc.)
     */
    public double computeMoneyGain(double basePrice,
                                   double quantity,
                                   HoeData hoe,
                                   PlayerProfile profile) {
        if (basePrice <= 0.0D || quantity <= 0.0D || hoe == null) {
            return 0.0D;
        }

        double prestigeMulti = getPrestigeMultiplier(hoe);

        double perksMulti = 1.0D;
        if (prestigeBonusService != null && profile != null) {
            perksMulti = prestigeBonusService.getPerkMultiplier(profile, BonusContext.MONEY);
        }

        Map<String, Double> vars = new HashMap<>();
        vars.put("base_price", basePrice);
        vars.put("quantity", quantity);

        vars.put("prestige_multi", prestigeMulti);
        vars.put("grades_multi", 1.0D);
        vars.put("skins_multi", 1.0D);
        vars.put("boosts_multi", 1.0D);
        vars.put("events_multi", 1.0D);
        vars.put("enchants_multi", 1.0D);
        vars.put("permanent_shop_multi", perksMulti);

        double fallback = basePrice * quantity * prestigeMulti * perksMulti;

        return formulaEngine.eval("formulas.money_gain", vars, fallback);
    }


    // =========================================================
    //            PRESTIGE (multiplicateur global)
    // =========================================================

    /**
     * Multiplicateur global de prestige.
     *
     * Utilise formulas.prestige_multiplier
     *   - variable disponible : "prestige" (niveau de prestige de la houe)
     *
     * Fallback : 1 + 0.01 * prestige
     */
    private double getPrestigeMultiplier(HoeData hoe) {
        if (hoe == null) return 1.0;

        int prestige = hoe.getPrestige();

        Map<String, Double> vars = new HashMap<>();
        vars.put("prestige", (double) prestige);

        double result = formulaEngine.eval(
                "formulas.prestige_multiplier",
                vars,
                1.0 + 0.01 * prestige
        );

        if (result < 0.0) {
            result = 0.0;
        }

        return result;
    }
}
