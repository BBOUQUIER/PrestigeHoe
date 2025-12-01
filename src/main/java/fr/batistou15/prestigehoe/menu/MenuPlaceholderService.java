package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.enchant.EssenceBoosterEnchant;
import fr.batistou15.prestigehoe.enchant.MoneyBoosterEnchant;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;
import fr.batistou15.prestigehoe.skin.SkinManager;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

public class MenuPlaceholderService {

    private final PrestigeHoePlugin plugin;

    public MenuPlaceholderService(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, String> buildBasePlaceholders(Player viewer) {
        Map<String, String> map = new HashMap<>();

        if (viewer == null) {
            return map;
        }

        map.put("%player%", viewer.getName());

        fr.batistou15.prestigehoe.data.PlayerProfile profile =
                plugin.getPlayerDataManager().getProfile(viewer);
        if (profile == null) {
            return map;
        }

        HoeData hoe = profile.getHoeData();
        if (hoe == null) {
            hoe = new HoeData();
        }

        int level = hoe.getLevel();
        int prestige = hoe.getPrestige();
        double xpCurrent = hoe.getXp();

        // Niveau / prestige / xp
        map.put("%hoe_level%", String.valueOf(level));
        map.put("%hoe_prestige%", String.valueOf(prestige));
        map.put("%hoe_xp_current%", String.format(Locale.US, "%.1f", xpCurrent));

        // XP requise pour le niveau actuel (formule du config.yml)
        double xpRequired = computeXpRequired(hoe);
        map.put("%hoe_xp_required%", String.format(Locale.US, "%.1f", xpRequired));

        if (xpRequired > 0.0) {
            double percent = Math.max(0.0, Math.min(100.0, (xpCurrent / xpRequired) * 100.0));
            map.put("%hoe_xp_percent%", String.format(Locale.US, "%.1f", percent));
        }

        // Niveau max (via FarmService)
        try {
            int maxLevel = plugin.getFarmService().getMaxHoeLevel(hoe);
            map.put("%hoe_max_level%", String.valueOf(maxLevel));
        } catch (Throwable ignored) {
            // si jamais farmService est null ou autre
        }

        // Monnaies
        double essence = profile.getEssence();
        long prestigeTokens = profile.getPrestigeTokens();

        map.put("%player_essence_formatted%", NumberFormatUtil.formatShort(essence));
        map.put("%prestige_tokens_formatted%", NumberFormatUtil.formatShort(prestigeTokens));

        // Solde Vault actuel
        double balance = 0.0;
        try {
            if (plugin.getEconomyHook() != null) {
                balance = plugin.getEconomyHook().getBalance(viewer);
            }
        } catch (Throwable ignored) {
        }
        map.put("%player_money_formatted%", NumberFormatUtil.formatShort(balance));

        // Stats globales (crops / argent / essence)
        long totalCropsBroken = 0L;
        Map<String, Long> crops = profile.getCropsBroken();
        if (crops != null) {
            for (long v : crops.values()) {
                totalCropsBroken += v;
            }
        }
        map.put("%hoe_crops_broken_formatted%", NumberFormatUtil.formatShort(totalCropsBroken));
        map.put("%hoe_money_earned_formatted%", NumberFormatUtil.formatShort(profile.getTotalMoneyEarned()));
        map.put("%hoe_essence_earned_formatted%", NumberFormatUtil.formatShort(profile.getTotalEssenceEarned()));

        // Recap (config.yml)
        int recapInterval = plugin.getConfigManager()
                .getMainConfig()
                .getInt("recap.interval-seconds", 60);
        map.put("%recap_interval_seconds%", String.valueOf(recapInterval));

        // 🔔 Statuts globaux pour le menu settings
        try {
            String on = "§aON";
            String off = "§cOFF";

            map.put("%recap_status%", profile.isRecapEnabled() ? on : off);
            map.put("%notif_chat_status%", profile.isChatNotificationsEnabled() ? on : off);
            map.put("%notif_enchant_proc_status%", profile.isEnchantProcMessagesEnabledGlobal() ? on : off);
            map.put("%notif_levelup_status%", profile.isLevelUpMessageEnabled() ? on : off);
            map.put("%notif_actionbar_status%", profile.isActionBarNotificationsEnabled() ? on : off);
            map.put("%notif_title_status%", profile.isTitleNotificationsEnabled() ? on : off);
        } catch (Throwable ignored) {
        }

        // === Placeholders de SKIN (multiplicateurs du skin actif) ===
        try {
            SkinManager skinManager = plugin.getSkinManager();
            if (skinManager != null) {
                double skinMoneyMult       = skinManager.getMoneyMultiplier(profile);
                double skinEssenceMult     = skinManager.getEssenceMultiplier(profile);
                double skinHoeXpMult       = skinManager.getHoeXpMultiplier(profile);
                double skinPlayerXpMult    = skinManager.getPlayerXpMultiplier(profile);
                double skinJobXpMult       = skinManager.getJobXpMultiplier(profile);
                double skinEnchantProcMult = skinManager.getEnchantProcMultiplier(profile);

                // Version "x1.10"
                map.put("%skin_money_mult%",        formatMultiplier(skinMoneyMult));
                map.put("%skin_essence_mult%",      formatMultiplier(skinEssenceMult));
                map.put("%skin_hoe_xp_mult%",       formatMultiplier(skinHoeXpMult));
                map.put("%skin_player_xp_mult%",    formatMultiplier(skinPlayerXpMult));
                map.put("%skin_job_xp_mult%",       formatMultiplier(skinJobXpMult));
                map.put("%skin_enchant_proc_mult%", formatMultiplier(skinEnchantProcMult));

                // Version "+10.00%" (bonus vs 1.0)
                map.put("%skin_money_bonus%",        formatPercent(skinMoneyMult - 1.0D));
                map.put("%skin_essence_bonus%",      formatPercent(skinEssenceMult - 1.0D));
                map.put("%skin_hoe_xp_bonus%",       formatPercent(skinHoeXpMult - 1.0D));
                map.put("%skin_player_xp_bonus%",    formatPercent(skinPlayerXpMult - 1.0D));
                map.put("%skin_job_xp_bonus%",       formatPercent(skinJobXpMult - 1.0D));
                map.put("%skin_enchant_proc_bonus%", formatPercent(skinEnchantProcMult - 1.0D));
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de construire les placeholders de skin", t);
        }

        // === Lignes d'enchantements pour les menus (%hoe_enchants_lines%) ===
        try {
            FileConfiguration enchCfg = plugin.getConfigManager().getEnchantsConfig();
            ConfigurationSection root = enchCfg.getConfigurationSection("enchants");
            if (root != null) {
                List<String> enchantLines = new ArrayList<>();

                for (String enchantId : root.getKeys(false)) {
                    ConfigurationSection sec = root.getConfigurationSection(enchantId);
                    if (sec == null) continue;

                    if (!sec.getBoolean("show-in-menu-hoe-info", true)) {
                        continue;
                    }

                    int lvl = hoe.getEnchantLevel(enchantId, 0);
                    if (lvl <= 0) continue;

                    String name = sec.getString("display-name", enchantId);
                    if (name == null || name.isEmpty()) {
                        name = enchantId;
                    }

                    String format = sec.getString(
                            "menu-hoe-info-format",
                            "&7- &e%name% &7niv &f%level%"
                    );

                    String line = format
                            .replace("%name%", name)
                            .replace("%level%", String.valueOf(lvl));

                    enchantLines.add(line);
                }

                if (enchantLines.isEmpty()) {
                    map.put("%hoe_enchants_lines%", "&7- Aucun enchant");
                } else {
                    map.put("%hoe_enchants_lines%", String.join("\n", enchantLines));
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de construire %hoe_enchants_lines%", t);
        }

        // === Boosts de prestige (%prestige_boost_...%) ===
        try {
            FileConfiguration prestigeCfg = plugin.getConfigManager().getPrestigeConfig();
            ConfigurationSection pRoot = prestigeCfg.getConfigurationSection("prestige");
            if (pRoot != null) {

                double moneyInc = pRoot.getDouble("increments.money-multiplier-per-prestige", 0.0D);
                double essenceInc = pRoot.getDouble("increments.essence-multiplier-per-prestige", 0.0D);
                double xpHoeInc = pRoot.getDouble("increments.xp-hoe-multiplier-per-prestige", 0.0D);
                double xpPlayerInc = pRoot.getDouble("increments.xp-player-multiplier-per-prestige", 0.0D);
                double jobXpInc = pRoot.getDouble("increments.job-xp-multiplier-per-prestige", 0.0D);
                double enchantProcInc = pRoot.getDouble("increments.enchant-proc-multiplier-per-prestige", 0.0D);

                map.put("%prestige_boost_money%", formatMultiplier(1.0D + prestige * moneyInc));
                map.put("%prestige_boost_essence%", formatMultiplier(1.0D + prestige * essenceInc));
                map.put("%prestige_boost_xp_hoe%", formatMultiplier(1.0D + prestige * xpHoeInc));
                map.put("%prestige_boost_xp_player%", formatMultiplier(1.0D + prestige * xpPlayerInc));
                map.put("%prestige_boost_job_xp%", formatMultiplier(1.0D + prestige * jobXpInc));
                map.put("%prestige_boost_enchant_proc%", formatMultiplier(1.0D + prestige * enchantProcInc));

                map.put("%prestige_boost_money_per_prestige%", formatIncrement(moneyInc));
                map.put("%prestige_boost_essence_per_prestige%", formatIncrement(essenceInc));
                map.put("%prestige_boost_xp_hoe_per_prestige%", formatIncrement(xpHoeInc));
                map.put("%prestige_boost_xp_player_per_prestige%", formatIncrement(xpPlayerInc));
                map.put("%prestige_boost_job_xp_per_prestige%", formatIncrement(jobXpInc));
                map.put("%prestige_boost_enchant_proc_per_prestige%", formatIncrement(enchantProcInc));
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de construire les placeholders de boosts de prestige", t);
        }

        // === Boosts cumulés "totaux" (prestige + perks + enchants + SKIN) ===
        try {
            // 1) Multiplicateur de prestige global (formulas.prestige_multiplier)
            double prestigeMulti = 1.0D;
            try {
                Map<String, Double> vars = new HashMap<>();
                vars.put("prestige", (double) prestige);

                prestigeMulti = plugin.getFormulaEngine().eval(
                        "formulas.prestige_multiplier",
                        vars,
                        1.0D + 0.01D * prestige // fallback simple si la formule foire
                );
            } catch (Throwable ignored) {
                prestigeMulti = 1.0D + 0.01D * prestige;
            }

            // 2) Multiplicateurs de PERKS (prestige_shop.yml)
            double essencePerksMulti = 1.0D;
            double moneyPerksMulti = 1.0D;

            PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
            if (bonusService != null) {
                essencePerksMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.ESSENCE);
                moneyPerksMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.MONEY);
            }

            // 3) Multiplicateurs d'ENCHANTS (EssenceBooster, MoneyBooster)
            double essenceEnchantMulti = 1.0D;
            double moneyEnchantMulti = 1.0D;

            if (plugin.getEnchantManager() != null) {
                EssenceBoosterEnchant essenceBooster = plugin.getEnchantManager().getEssenceBoosterEnchant();
                if (essenceBooster != null && essenceBooster.isEnabled()) {
                    essenceEnchantMulti = essenceBooster.getMultiplier(hoe);
                }

                MoneyBoosterEnchant moneyBooster = plugin.getEnchantManager().getMoneyBoosterEnchant();
                if (moneyBooster != null) {
                    double mm = moneyBooster.getMoneyMultiplier(hoe);
                    if (mm > 1.0D) {
                        moneyEnchantMulti = mm;
                    }
                }
            }

            // 4) Multiplicateurs de SKIN pour argent / essence
            double skinEssenceMulti = 1.0D;
            double skinMoneyMulti = 1.0D;
            try {
                SkinManager skinManager = plugin.getSkinManager();
                if (skinManager != null) {
                    skinEssenceMulti = skinManager.getEssenceMultiplier(profile);
                    skinMoneyMulti = skinManager.getMoneyMultiplier(profile);
                }
            } catch (Throwable ignored) {
            }

            // 5) Multiplicateurs finaux (ce que tu veux afficher)
            double totalEssenceMulti = prestigeMulti * essencePerksMulti * essenceEnchantMulti * skinEssenceMulti;
            double totalMoneyMulti = prestigeMulti * moneyPerksMulti * moneyEnchantMulti * skinMoneyMulti;

            if (totalEssenceMulti < 0.0D) totalEssenceMulti = 0.0D;
            if (totalMoneyMulti < 0.0D) totalMoneyMulti = 0.0D;

            map.put("%hoe_boost_essence_total_mult%", String.format(Locale.US, "x%.2f", totalEssenceMulti));
            map.put("%hoe_boost_essence_total_percent%", String.format(Locale.US, "+%.2f%%", (totalEssenceMulti - 1.0D) * 100.0D));

            map.put("%hoe_boost_money_total_mult%", String.format(Locale.US, "x%.2f", totalMoneyMulti));
            map.put("%hoe_boost_money_total_percent%", String.format(Locale.US, "+%.2f%%", (totalMoneyMulti - 1.0D) * 100.0D));

        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de calculer les boosts cumulés d'essence/argent pour le menu.", t);
        }

        // === Boosts cumulés XP (prestige + perks + enchant hoe_xp_booster + SKIN) ===
        try {
            FileConfiguration prestigeCfg = plugin.getConfigManager().getPrestigeConfig();
            ConfigurationSection pRoot = prestigeCfg.getConfigurationSection("prestige");
            if (pRoot != null) {
                // 1) Multiplicateurs "prestige" venant de prestige.yml
                double xpHoeInc = pRoot.getDouble("increments.xp-hoe-multiplier-per-prestige", 0.0D);
                double xpPlayerInc = pRoot.getDouble("increments.xp-player-multiplier-per-prestige", 0.0D);

                double xpHoePrestigeMulti = 1.0D + prestige * xpHoeInc;
                double xpPlayerPrestigeMulti = 1.0D + prestige * xpPlayerInc;

                if (xpHoePrestigeMulti < 0.0D) xpHoePrestigeMulti = 0.0D;
                if (xpPlayerPrestigeMulti < 0.0D) xpPlayerPrestigeMulti = 0.0D;

                // 2) Multiplicateurs de PERKS (prestige_shop.yml)
                double xpHoePerksMulti = 1.0D;
                double xpPlayerPerksMulti = 1.0D;

                PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
                if (bonusService != null && profile != null) {
                    xpHoePerksMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.XP_HOE_MULTIPLIER);
                    xpPlayerPerksMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.XP_PLAYER_MULTIPLIER);
                }

                // 3) Multiplicateur de l'enchant hoe_xp_booster (depuis enchants.yml)
                double xpEnchantMulti = 1.0D;
                try {
                    FileConfiguration enchCfg = plugin.getConfigManager().getEnchantsConfig();
                    ConfigurationSection hoeXpSec = enchCfg.getConfigurationSection("enchants.hoe_xp_booster");
                    int xpEnchantLevel = hoe.getEnchantLevel("hoe_xp_booster", 0);
                    if (hoeXpSec != null && xpEnchantLevel > 0) {
                        ConfigurationSection settingsSec = hoeXpSec.getConfigurationSection("settings");
                        String stackMode = settingsSec != null
                                ? settingsSec.getString("stack-mode", "ADD")
                                : "ADD";
                        double perLevel = settingsSec != null
                                ? settingsSec.getDouble("xp-bonus-per-level", 0.0D)
                                : 0.0D;

                        if ("MULTIPLY".equalsIgnoreCase(stackMode)) {
                            xpEnchantMulti = Math.pow(1.0D + perLevel, xpEnchantLevel);
                        } else {
                            // ADD par défaut : 1 + (perLevel * level)
                            xpEnchantMulti = 1.0D + perLevel * xpEnchantLevel;
                        }
                    }
                } catch (Throwable ignored) {
                }

                if (xpEnchantMulti < 0.0D) xpEnchantMulti = 0.0D;

                // 4) Multiplicateurs de SKIN pour XP
                double skinHoeXpMulti = 1.0D;
                double skinPlayerXpMulti = 1.0D;
                try {
                    SkinManager skinManager = plugin.getSkinManager();
                    if (skinManager != null) {
                        skinHoeXpMulti = skinManager.getHoeXpMultiplier(profile);
                        skinPlayerXpMulti = skinManager.getPlayerXpMultiplier(profile);
                    }
                } catch (Throwable ignored) {
                }

                // 5) Multiplicateurs finaux
                double totalXpHoeMulti = xpHoePrestigeMulti * xpHoePerksMulti * xpEnchantMulti * skinHoeXpMulti;
                double totalXpPlayerMulti = xpPlayerPrestigeMulti * xpPlayerPerksMulti * skinPlayerXpMulti; // pas d'enchant direct joueur

                if (totalXpHoeMulti < 0.0D) totalXpHoeMulti = 0.0D;
                if (totalXpPlayerMulti < 0.0D) totalXpPlayerMulti = 0.0D;

                map.put("%hoe_boost_xp_hoe_total_mult%", String.format(Locale.US, "x%.2f", totalXpHoeMulti));
                map.put("%hoe_boost_xp_hoe_total_percent%", String.format(Locale.US, "+%.2f%%", (totalXpHoeMulti - 1.0D) * 100.0D));

                map.put("%hoe_boost_xp_player_total_mult%", String.format(Locale.US, "x%.2f", totalXpPlayerMulti));
                map.put("%hoe_boost_xp_player_total_percent%", String.format(Locale.US, "+%.2f%%", (totalXpPlayerMulti - 1.0D) * 100.0D));
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de calculer les boosts cumulés d'XP pour le menu.", t);
        }

        return map;
    }

    public String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        String out = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && value != null) {
                out = out.replace(key, value);
            }
        }
        return out;
    }

    private double computeXpRequired(HoeData hoe) {
        if (hoe == null) return 100.0;

        try {
            int level = hoe.getLevel();
            Map<String, Double> vars = new HashMap<>();
            vars.put("level", (double) level);

            return plugin.getFormulaEngine().eval(
                    "formulas.xp_required_per_level",
                    vars,
                    200.0
            );
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de calculer xp_required pour le menu.", t);
            return 200.0;
        }
    }

    public String formatMultiplier(double factor) {
        return String.format(Locale.US, "x%.2f", factor);
    }

    public String formatIncrement(double inc) {
        return String.format(Locale.US, "+%.2f", inc);
    }

    public String formatPercent(double value) {
        return String.format(Locale.US, "+%.2f%%", value * 100.0);
    }
}
