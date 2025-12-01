package fr.batistou15.prestigehoe.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Données liées à la houe du joueur :
 * - niveau
 * - xp
 * - prestige
 * - niveaux d'enchants (par id, ex: "fortune", "autosell")
 */
public class HoeData {

    private int level;
    private double xp;
    private int prestige;
    private String skinId = "default";

    /**
     * Map id_enchant -> niveau
     * Ex : "fortune" -> 12, "autosell" -> 1
     */
    private Map<String, Integer> enchantLevels = new HashMap<>();

    public HoeData() {
        this.level = 1;
        this.xp = 0.0;
        this.prestige = 0;
    }

    // ---------- Niveau / XP / Prestige ----------

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        if (level < 1) level = 1;
        this.level = level;
    }

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        if (xp < 0) xp = 0;
        this.xp = xp;
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        if (prestige < 0) prestige = 0;
        this.prestige = prestige;
    }

    // ---------- Enchants ----------

    /**
     * Retourne la map brute (utilisée pour la sérialisation JSON).
     */
    public Map<String, Integer> getEnchantLevels() {
        if (enchantLevels == null) {
            enchantLevels = new HashMap<>();
        }
        return enchantLevels;
    }

    public void setEnchantLevels(Map<String, Integer> enchantLevels) {
        this.enchantLevels = (enchantLevels != null) ? enchantLevels : new HashMap<>();
    }

    /**
     * Niveau d'un enchant, 0 si aucun niveau stocké.
     */
    public int getEnchantLevel(String id) {
        if (id == null) return 0;
        return getEnchantLevels().getOrDefault(id.toLowerCase(), 0);
    }
    public String getSkinId() {
        return (skinId == null || skinId.isEmpty()) ? "default" : skinId;
    }

    public void setSkinId(String skinId) {
        this.skinId = (skinId == null || skinId.isEmpty()) ? "default" : skinId;
    }
    /**
     * Niveau d'un enchant avec valeur par défaut si *aucun niveau stocké*.
     * Attention : si on a explicitement mis 0 dans la map,
     * cette valeur 0 est utilisée (et pas defaultValue).
     */
    public int getEnchantLevel(String id, int defaultValue) {
        if (id == null) return defaultValue;
        return getEnchantLevels().getOrDefault(id.toLowerCase(), defaultValue);
    }

    /**
     * Définir le niveau d'un enchant pour cette houe.
     * IMPORTANT :
     * - On NE SUPPRIME PLUS la clé quand level <= 0.
     * - Niveau 0 = enchant explicitement désactivé.
     */
    public void setEnchantLevel(String enchantId, int level) {
        if (enchantId == null) {
            return;
        }

        String key = enchantId.toLowerCase();
        getEnchantLevels().put(key, level);
    }
}
