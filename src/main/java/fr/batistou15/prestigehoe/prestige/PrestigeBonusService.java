package fr.batistou15.prestigehoe.prestige;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Service qui gère les PERKS (prestige_shop.yml).
 *  - Chargement des perks
 *  - Calcul des multiplicateurs en fonction des niveaux du joueur
 */
public class PrestigeBonusService {

    /**
     * Type de bonus fourni par un perk.
     * Doit correspondre au champ "type" dans prestige_shop.yml
     */
    public enum BonusContext {
        ESSENCE,
        MONEY,
        XP_HOE_MULTIPLIER,
        XP_PLAYER_MULTIPLIER,
        JOBS_XP_MULTIPLIER,
        ENCHANT_PROC_MULTIPLIER,
        SPEED
    }

    /**
     * Définition d'un perk dans prestige_shop.yml
     */
    public static class PerkDefinition {
        private final String id;
        private final BonusContext type;
        private final double valuePerLevel;

        public PerkDefinition(String id, BonusContext type, double valuePerLevel) {
            this.id = id;
            this.type = type;
            this.valuePerLevel = valuePerLevel;
        }

        public String getId() {
            return id;
        }

        public BonusContext getType() {
            return type;
        }

        public double getValuePerLevel() {
            return valuePerLevel;
        }
    }

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;

    // key = perkId (lowercase), value = définition
    private final Map<String, PerkDefinition> perkDefinitions = new HashMap<>();

    public PrestigeBonusService(PrestigeHoePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        reload();
    }

    /**
     * Recharge les définitions de perks à partir de prestige_shop.yml
     */
    public void reload() {
        perkDefinitions.clear();

        FileConfiguration shopCfg = configManager.getPrestigeShopConfig();
        ConfigurationSection root = shopCfg.getConfigurationSection("prestige_shop.perks");
        if (root == null) {
            plugin.getLogger().warning("[PrestigeHoe] Aucune section prestige_shop.perks dans prestige_shop.yml");
            return;
        }

        for (String perkId : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(perkId);
            if (sec == null) continue;
            if (!sec.getBoolean("enabled", true)) continue;

            String typeRaw = sec.getString("type", "").toUpperCase(Locale.ROOT);
            BonusContext type;
            try {
                type = BonusContext.valueOf(typeRaw);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[PrestigeHoe] Perk '" + perkId + "' a un type invalide: '" + typeRaw + "'. Ignoré.");
                continue;
            }

            double valuePerLevel = sec.getDouble("value-per-level", 0.0D);
            String key = perkId.toLowerCase(Locale.ROOT);

            perkDefinitions.put(key, new PerkDefinition(key, type, valuePerLevel));
        }

        plugin.getLogger().info("[PrestigeHoe] Chargé " + perkDefinitions.size() + " perks depuis prestige_shop.yml");
    }

    /**
     * Calcule un bonus "flat" (somme des valeurs) pour un contexte donné.
     * Exemple : si essence_boost = 10 niveaux, 0.10 par level → 1.0 de bonus.
     */
    private double getPerkFlatBonus(PlayerProfile profile, BonusContext context) {
        if (profile == null) return 0.0D;

        double total = 0.0D;

        for (PerkDefinition def : perkDefinitions.values()) {
            if (def.getType() != context) continue;

            int level = profile.getPrestigePerkLevel(def.getId());
            if (level <= 0) continue;

            total += def.getValuePerLevel() * level;
        }

        return total;
    }

    /**
     * Retourne un multiplicateur générique pour un contexte donné.
     * Exemple pour ESSENCE/MONEY :
     *   1.0 + (value_per_level * niveau_total)
     */
    public double getPerkMultiplier(PlayerProfile profile, BonusContext context) {
        double flat = getPerkFlatBonus(profile, context);
        double result = 1.0D + flat;
        if (result < 0.0D) result = 0.0D;
        return result;
    }

    // --- Helpers explicites si tu veux t'en servir ailleurs plus tard ---

    public double getEssenceMultiplier(PlayerProfile profile) {
        return getPerkMultiplier(profile, BonusContext.ESSENCE);
    }

    public double getMoneyMultiplier(PlayerProfile profile) {
        return getPerkMultiplier(profile, BonusContext.MONEY);
    }

    public double getHoeXpMultiplier(PlayerProfile profile) {
        return getPerkMultiplier(profile, BonusContext.XP_HOE_MULTIPLIER);
    }

    public double getPlayerXpMultiplier(PlayerProfile profile) {
        return getPerkMultiplier(profile, BonusContext.XP_PLAYER_MULTIPLIER);
    }

    public double getJobsXpMultiplier(PlayerProfile profile) {
        return getPerkMultiplier(profile, BonusContext.JOBS_XP_MULTIPLIER);
    }

    public double getEnchantProcMultiplier(PlayerProfile profile) {
        return getPerkMultiplier(profile, BonusContext.ENCHANT_PROC_MULTIPLIER);
    }

    /**
     * Multiplicateur de VITESSE de déplacement venant des perks.
     * Si le joueur a 3 niveaux de speed_boost avec 0.05 par level → 1 + 0.15 = 1.15
     */
    public double getSpeedMultiplier(PlayerProfile profile) {
        double flat = getPerkFlatBonus(profile, BonusContext.SPEED);
        double result = 1.0D + flat;
        if (result < 0.0D) result = 0.0D;
        return result;
    }
}
