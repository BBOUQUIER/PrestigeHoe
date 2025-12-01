package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EssencePouchEnchant implements HoeEnchant {

    public static final String ID = "essence_pouch";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    // Chance (section chance:)
    private double baseChance;
    private double chancePerLevel;
    private double maxChance;

    // Tiers
    private static class Tier {
        final int levelMin;
        final double minAmount;
        final double maxAmount;

        Tier(int levelMin, double minAmount, double maxAmount) {
            this.levelMin = levelMin;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }
    }

    private final List<Tier> tiers = new ArrayList<>();

    // amount_formula (string brute, mais on gère ton cas dedans)
    private String amountFormula;

    // Notifications
    private boolean notifEnabled;
    private String notifMessage; // notifications.on-proc-message

    public EssencePouchEnchant(PrestigeHoePlugin plugin) {
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
            plugin.getLogger().warning("[EssencePouchEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            baseChance = 0.0D;
            chancePerLevel = 0.0D;
            maxChance = 1.0D;
            tiers.clear();
            amountFormula = null;
            notifEnabled = false;
            notifMessage = null;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        maxLevel = sec.getInt("level.max-level", 100);
        defaultLevel = sec.getInt("level.default-level", 0);

        // === Chance ===
        ConfigurationSection chanceSec = sec.getConfigurationSection("chance");
        if (chanceSec != null) {
            baseChance = chanceSec.getDouble("base-chance", 0.0D);
            chancePerLevel = chanceSec.getDouble("chance-per-level", 0.0D);
            maxChance = chanceSec.getDouble("max-chance", 1.0D);
        } else {
            baseChance = 0.0D;
            chancePerLevel = 0.0D;
            maxChance = 1.0D;
        }

        // === Tiers ===
        tiers.clear();
        ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
        if (settingsSec != null) {
            List<Map<?, ?>> tierList = settingsSec.getMapList("tiers");
            if (tierList != null) {
                for (Map<?, ?> map : tierList) {
                    if (map == null) continue;
                    int levelMin = getInt(map, "level-min", 1);
                    double minAmount = getDouble(map, "min-amount", 0.0D);
                    double maxAmount = getDouble(map, "max-amount", 0.0D);
                    tiers.add(new Tier(levelMin, minAmount, maxAmount));
                }
            }
            amountFormula = settingsSec.getString("amount_formula", "base_random");
        } else {
            amountFormula = "base_random";
        }

        // === Notifications ===
        ConfigurationSection notifSec = sec.getConfigurationSection("notifications");
        if (notifSec != null) {
            notifEnabled = notifSec.getBoolean("enabled", true);
            notifMessage = notifSec.getString(
                    "on-proc-message",
                    "%prefix%&bTu as trouvé une bourse d'Essence: &e+%amount_formatted% Essence&b !"
            );
        } else {
            notifEnabled = false;
            notifMessage = null;
        }

        plugin.getLogger().info(String.format(
                Locale.US,
                "[PrestigeHoe] [EssencePouchEnchant] enabled=%s, maxLevel=%d, defaultLevel=%d, baseChance=%.4f, chancePerLevel=%.4f, tiers=%d",
                enabled, maxLevel, defaultLevel, baseChance, chancePerLevel, tiers.size()
        ));
    }

    private int getInt(Map<?, ?> map, String key, int def) {
        Object o = map.get(key);
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    private double getDouble(Map<?, ?> map, String key, double def) {
        Object o = map.get(key);
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Retourne le tier applicable pour un niveau donné :
     * - le tier avec level-min le plus grand <= level
     * - si aucun ne matche, on prend le plus bas level-min
     */
    private Tier getTierForLevel(int level) {
        if (tiers.isEmpty()) return null;

        Tier best = null;
        for (Tier t : tiers) {
            if (level >= t.levelMin) {
                if (best == null || t.levelMin > best.levelMin) {
                    best = t;
                }
            }
        }
        if (best != null) return best;

        // Aucun tier éligible → tier avec le level-min le plus petit
        Tier lowest = tiers.get(0);
        for (Tier t : tiers) {
            if (t.levelMin < lowest.levelMin) {
                lowest = t;
            }
        }
        return lowest;
    }

    /**
     * Tente un proc de EssencePouch à chaque casse de bloc.
     *
     * @return montant d'Essence gagné (0.0 si aucun proc)
     */
    public double tryExecute(Player player, HoeData hoe) {
        if (!enabled || player == null || hoe == null) return 0.0D;

        int level = hoe.getEnchantLevel(ID, 0);
        if (level <= 0) {
            return 0.0D;
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) {
            return 0.0D;
        }

        // Chance (prestige + perks + proc_booster)
        EnchantProcHelper.ProcData procData = EnchantProcHelper.computeProcData(
                plugin,
                profile,
                hoe,
                EssencePouchEnchant.ID,   // "essence_pouch"
                baseChance,
                chancePerLevel,
                maxChance,
                level
        );

        double chanceToUse = procData.totalCurrent;
        if (chanceToUse <= 0.0D) {
            return 0.0D;
        }

        double roll = ThreadLocalRandom.current().nextDouble(); // [0,1)
        if (roll > chanceToUse) {
            return 0.0D; // pas de proc
        }

        // Déterminer le tier de montant à partir du niveau de l'enchant
        Tier tier = getTierForLevel(level);
        if (tier == null) {
            return 0.0D;
        }

        double baseRandom;
        if (tier.maxAmount <= tier.minAmount) {
            baseRandom = tier.minAmount;
        } else {
            baseRandom = ThreadLocalRandom.current().nextDouble(tier.minAmount, tier.maxAmount);
        }

        // Appliquer amount_formula
        double finalAmount = baseRandom;
        int prestige = hoe.getPrestige();

        if (amountFormula != null && !amountFormula.isEmpty()) {
            try {
                if (amountFormula.contains("base_random") && amountFormula.contains("prestige")) {
                    finalAmount = baseRandom * (1.0D + prestige * 0.01D);
                } else {
                    finalAmount = baseRandom;
                }
            } catch (Throwable ignored) {
                finalAmount = baseRandom;
            }
        }

        if (finalAmount <= 0.0D) {
            return 0.0D;
        }

        // On crédite l'essence (met aussi à jour totalEssenceEarned pour le récap)
        profile.addEssence(finalAmount);

        // Message de proc si activé + si le joueur n'a pas coupé les messages
        if (notifEnabled
                && notifMessage != null && !notifMessage.isEmpty()
                && profile.isEnchantMessagesEnabledGlobal()
                && profile.isEnchantProcMessagesEnabledGlobal()
                && profile.isEnchantProcMessageEnabled(EssencePouchEnchant.ID)
                && profile.isChatNotificationsEnabled()) {

            String amountFormatted = NumberFormatUtil.formatShort(finalAmount);

            String msg = notifMessage
                    .replace("%prefix%", MessageUtil.getPrefix())
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.format(Locale.US, "%.2f", finalAmount))
                    .replace("%amount_formatted%", amountFormatted);

            MessageUtil.sendPlain(player, msg);
        }

        return finalAmount;
    }


}
