package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.enchant.EnchantProcHelper;
import fr.batistou15.prestigehoe.enchant.FuryEnchant;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.logging.Level;

public class EnchantMenuService {

    private final PrestigeHoePlugin plugin;
    private final MenuItemFactory itemFactory;

    public EnchantMenuService(PrestigeHoePlugin plugin, MenuItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
    }

    public void fillEnchantIcons(Inventory inv,
                                 MenuHolder holder,
                                 Player viewer,
                                 MenuItemConfig template) {
        if (viewer == null) return;

        FileConfiguration enchantsCfg = plugin.getConfigManager().getEnchantsConfig();
        ConfigurationSection root = enchantsCfg.getConfigurationSection("enchants");
        if (root == null) return;

        fr.batistou15.prestigehoe.data.PlayerProfile profile =
                plugin.getPlayerDataManager().getProfile(viewer);
        HoeData hoe = profile != null ? profile.getHoeData() : null;
        if (hoe == null) {
            hoe = new HoeData();
        }

        for (String enchantId : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(enchantId);
            if (sec == null) continue;
            if (!sec.getBoolean("enabled", true)) continue;

            ConfigurationSection guiSec = sec.getConfigurationSection("gui");
            if (guiSec == null) continue;

            int slot = guiSec.getInt("slot", -1);
            if (slot < 0 || slot >= inv.getSize()) continue;

            // Icone override
            MenuIconConfig iconCfg = template.getIcon();
            ConfigurationSection iconSec = guiSec.getConfigurationSection("icon");
            if (iconSec != null) {
                String matName = iconSec.getString("material", iconCfg.getMaterial());
                int cmd = iconSec.getInt("custom-model-data", iconCfg.getCustomModelData());
                String itemsAdderId = iconSec.getString("itemsadder-id", iconCfg.getItemsAdderId());
                String oraxenId = iconSec.getString("oraxen-id", iconCfg.getOraxenId());
                String skullOwner = iconSec.getString("skull-owner", iconCfg.getSkullOwner());
                String skullTexture = iconSec.getString("skull-texture", iconCfg.getSkullTexture());
                iconCfg = new MenuIconConfig(matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture);
            }

            MenuItemConfig perEnchantConfig = new MenuItemConfig(
                    "enchant_" + enchantId,
                    slot,
                    iconCfg,
                    template.getName(),
                    template.getLore(),
                    MenuAction.NONE,
                    null
            );

            Map<String, String> extra = new HashMap<>();
            extra.put("%enchant_id%", enchantId);

            String displayName = sec.getString("display-name", enchantId);
            extra.put("%enchant_display_name%", displayName != null ? displayName : enchantId);

            int currentLevel = hoe.getEnchantLevel(enchantId, 0);
            extra.put("%enchant_level_current%", String.valueOf(currentLevel));

            int maxLevelConfig = sec.getInt("level.max-level", 0);
            if (maxLevelConfig > 0) {
                extra.put("%enchant_level_max%", String.valueOf(maxLevelConfig));
            } else {
                extra.put("%enchant_level_max%", "∞");
            }

            String essenceCostText;
            if (maxLevelConfig > 0 && currentLevel >= maxLevelConfig) {
                essenceCostText = "Niveau max";
            } else {
                double essenceCost =
                        plugin.getEnchantManager().getEssenceCostForNextLevel(enchantId, currentLevel);
                essenceCostText = NumberFormatUtil.formatShort(essenceCost);
            }
            extra.put("%enchant_cost_essence_single%", essenceCostText);
            extra.put("%enchant_cost_money_single%", "");

            // === Chance de proc (base + totale) via EnchantProcHelper ===
            double baseChance = 0.0D;
            double chancePerLevel = 0.0D;
            double maxChance = 1.0D;

            ConfigurationSection chanceSec = sec.getConfigurationSection("chance");
            if (chanceSec != null) {
                baseChance = chanceSec.getDouble("base-chance", 0.0D);
                chancePerLevel = chanceSec.getDouble("chance-per-level", 0.0D);
                maxChance = chanceSec.getDouble("max-chance", 1.0D);
            }

            // === Pouch: min / max affichés (tiers + prestige) pour essence_pouch & money_pouch ===
            if ("essence_pouch".equalsIgnoreCase(enchantId) || "money_pouch".equalsIgnoreCase(enchantId)) {
                double uiMin = 0.0D;
                double uiMax = 0.0D;
                String amountFormula = "base_random";

                ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
                if (settingsSec != null) {
                    amountFormula = settingsSec.getString("amount_formula", "base_random");
                    List<Map<?, ?>> tierList = settingsSec.getMapList("tiers");

                    if (tierList != null && !tierList.isEmpty()) {
                        int effectiveLevel = currentLevel > 0 ? currentLevel : 1;

                        int bestLevelMin = Integer.MIN_VALUE;
                        double bestMin = 0.0D;
                        double bestMax = 0.0D;

                        int lowestLevelMin = Integer.MAX_VALUE;
                        double lowestMin = 0.0D;
                        double lowestMax = 0.0D;

                        for (Map<?, ?> map : tierList) {
                            if (map == null) continue;

                            Object lMinObj = map.get("level-min");
                            Object minAmtObj = map.get("min-amount");
                            Object maxAmtObj = map.get("max-amount");

                            int lvlMin;
                            double minAmt;
                            double maxAmt;

                            try {
                                lvlMin = (lMinObj instanceof Number)
                                        ? ((Number) lMinObj).intValue()
                                        : Integer.parseInt(String.valueOf(lMinObj));
                            } catch (Exception e) {
                                continue;
                            }
                            try {
                                minAmt = (minAmtObj instanceof Number)
                                        ? ((Number) minAmtObj).doubleValue()
                                        : Double.parseDouble(String.valueOf(minAmtObj));
                            } catch (Exception e) {
                                continue;
                            }
                            try {
                                maxAmt = (maxAmtObj instanceof Number)
                                        ? ((Number) maxAmtObj).doubleValue()
                                        : Double.parseDouble(String.valueOf(maxAmtObj));
                            } catch (Exception e) {
                                continue;
                            }

                            // garde le plus petit level-min comme fallback
                            if (lvlMin < lowestLevelMin) {
                                lowestLevelMin = lvlMin;
                                lowestMin = minAmt;
                                lowestMax = maxAmt;
                            }

                            // tier applicable = level >= level-min, et level-min le plus grand possible
                            if (effectiveLevel >= lvlMin && lvlMin > bestLevelMin) {
                                bestLevelMin = lvlMin;
                                bestMin = minAmt;
                                bestMax = maxAmt;
                            }
                        }

                        if (bestLevelMin != Integer.MIN_VALUE) {
                            uiMin = bestMin;
                            uiMax = bestMax;
                        } else if (lowestLevelMin != Integer.MAX_VALUE) {
                            uiMin = lowestMin;
                            uiMax = lowestMax;
                        }
                    }
                }

                // Application simplifiée d'amount_formula avec prestige (comme dans l'enchant)
                double displayMin = uiMin;
                double displayMax = uiMax;
                int prestige = hoe.getPrestige();

                if (amountFormula != null
                        && amountFormula.contains("base_random")
                        && amountFormula.contains("prestige")) {
                    displayMin = uiMin * (1.0D + prestige * 0.01D);
                    displayMax = uiMax * (1.0D + prestige * 0.01D);
                }

                String minFormatted = NumberFormatUtil.formatShort(displayMin);
                String maxFormatted = NumberFormatUtil.formatShort(displayMax);

                if ("essence_pouch".equalsIgnoreCase(enchantId)) {
                    extra.put("%essence_pouch_min_formatted%", minFormatted);
                    extra.put("%essence_pouch_max_formatted%", maxFormatted);
                } else {
                    extra.put("%money_pouch_min_formatted%", minFormatted);
                    extra.put("%money_pouch_max_formatted%", maxFormatted);
                }
            }

            // Calcul proc avec les perks / prestige
            EnchantProcHelper.ProcData proc = EnchantProcHelper.computeProcData(
                    plugin,
                    profile,
                    hoe,
                    enchantId,          // 🆕 très important
                    baseChance,
                    chancePerLevel,
                    maxChance,
                    currentLevel
            );

            double procBaseCurrent = proc.baseCurrent;
            double procBaseNext = proc.baseNext;
            double procTotalCurrent = proc.totalCurrent;
            double procTotalNext = proc.totalNext;

            // Placeholders "de base" (sans boosts)
            extra.put("%enchant_proc_chance_current%", String.format(Locale.US, "%.4f", procBaseCurrent));
            extra.put("%enchant_proc_chance_next%", String.format(Locale.US, "%.4f", procBaseNext));
            extra.put("%enchant_proc_chance_per_level%", String.format(Locale.US, "%.4f", chancePerLevel));
            extra.put("%enchant_proc_chance_current_percent%", String.format(Locale.US, "%.2f%%", procBaseCurrent * 100.0D));
            extra.put("%enchant_proc_chance_next_percent%", String.format(Locale.US, "%.2f%%", procBaseNext * 100.0D));
            extra.put("%enchant_proc_chance_per_level_percent%", String.format(Locale.US, "%.2f%%", chancePerLevel * 100.0D));

            // 🔥 Placeholders TOTAUX (prestige + perks)
            extra.put("%enchant_total_proc_chance%", String.format(Locale.US, "%.4f", procTotalCurrent));
            extra.put("%enchant_total_proc_chance_percent%", String.format(Locale.US, "%.2f%%", procTotalCurrent * 100.0D));
            extra.put("%enchant_total_proc_chance_next%", String.format(Locale.US, "%.4f", procTotalNext));
            extra.put("%enchant_total_proc_chance_next_percent%", String.format(Locale.US, "%.2f%%", procTotalNext * 100.0D));
            // 🔥 Placeholders spécifiques Furie (durée + boosts affichés)
            if ("furie".equalsIgnoreCase(enchantId)) {
                FuryEnchant fury = plugin.getEnchantManager().getFuryEnchant();
                int lvl = currentLevel;

                double moneyMulti = 1.0D;
                double essenceMulti = 1.0D;
                int durationSec = 0;

                if (fury != null) {
                    moneyMulti = fury.computeMoneyMultiplierForLevel(lvl);
                    essenceMulti = fury.computeEssenceMultiplierForLevel(lvl);
                    durationSec = fury.getDurationSecondsForLevel(lvl);
                }

                double moneyBonus = Math.max(0.0D, moneyMulti - 1.0D);
                double essenceBonus = Math.max(0.0D, essenceMulti - 1.0D);

                extra.put("%fury_money_boost_percent%",
                        String.format(Locale.US, "+%.2f%%", moneyBonus * 100.0D));
                extra.put("%fury_essence_boost_percent%",
                        String.format(Locale.US, "+%.2f%%", essenceBonus * 100.0D));
                extra.put("%duration_time_furie%", durationSec + "s");
            }
// === Boost multiplicateur pour essence_booster / money_booster / hoe_xp_booster / player_xp_booster / proc_booster ===
            double boostMultiplier = 1.0D;
            try {
                if ("essence_booster".equalsIgnoreCase(enchantId)
                        && plugin.getEnchantManager().getEssenceBoosterEnchant() != null) {
                    boostMultiplier = plugin.getEnchantManager().getEssenceBoosterEnchant().getMultiplier(hoe);

                } else if ("money_booster".equalsIgnoreCase(enchantId)
                        && plugin.getEnchantManager().getMoneyBoosterEnchant() != null) {
                    boostMultiplier = plugin.getEnchantManager().getMoneyBoosterEnchant().getMoneyMultiplier(hoe);

                } else if ("hoe_xp_booster".equalsIgnoreCase(enchantId)
                        && plugin.getEnchantManager().getHoeXpBoosterEnchant() != null) {
                    boostMultiplier = plugin.getEnchantManager().getHoeXpBoosterEnchant().getXpMultiplier(hoe);

                } else if ("player_xp_booster".equalsIgnoreCase(enchantId)
                        && plugin.getEnchantManager().getPlayerXpBoosterEnchant() != null) {
                    boostMultiplier = plugin.getEnchantManager().getPlayerXpBoosterEnchant().getXpMultiplier(hoe);

                } else if ("job_xp_booster".equalsIgnoreCase(enchantId)
                        && plugin.getEnchantManager().getJobXpBoosterEnchant() != null) {
                    // 🆕 Job XP Booster
                    boostMultiplier = plugin.getEnchantManager().getJobXpBoosterEnchant().getXpMultiplier(hoe);

                } else if ("proc_booster".equalsIgnoreCase(enchantId)
                        && plugin.getEnchantManager().getProcBoosterEnchant() != null) {
                    boostMultiplier = plugin.getEnchantManager().getProcBoosterEnchant().getGlobalProcMultiplier(hoe);
                }
            } catch (Throwable ignored) {}

            double boostBonus = Math.max(0.0D, boostMultiplier - 1.0D);
            extra.put("%enchant_boost_multiplier%", String.format(Locale.US, "x%.2f", boostMultiplier));
            extra.put("%enchant_boost_percent%", String.format(Locale.US, "+%.2f%%", boostBonus * 100.0D));


            // === Description avec placeholders résolus ===
            List<String> descLines = sec.getStringList("description");
            if (descLines != null && !descLines.isEmpty()) {
                MenuPlaceholderService placeholderService = plugin.getMenuManager().getPlaceholderService();

                Map<String, String> allPlaceholders = placeholderService.buildBasePlaceholders(viewer);
                allPlaceholders.putAll(extra);

                List<String> resolved = new ArrayList<>();
                for (String raw : descLines) {
                    String line = placeholderService.applyPlaceholders(raw, allPlaceholders);
                    resolved.add(line);
                }

                extra.put("%enchant_description_lines%", String.join("\n", resolved));
            } else {
                extra.put("%enchant_description_lines%", "");
            }

            // Statut des notifs (global, pour l'instant)
            extra.put("%enchant_notif_status%", "ON");

            ItemStack stack = itemFactory.buildItem(perEnchantConfig, viewer, extra);
            inv.setItem(slot, stack);
            holder.setEnchantForSlot(slot, enchantId);
        }
    }



    public Map<String, String> buildUpgradePlaceholders(Player viewer, String enchantId) {
        Map<String, String> map = new HashMap<>();

        if (viewer == null || enchantId == null || enchantId.isEmpty()) {
            return map;
        }

        FileConfiguration enchantsCfg = plugin.getConfigManager().getEnchantsConfig();
        ConfigurationSection root = enchantsCfg.getConfigurationSection("enchants");
        if (root == null) return map;

        ConfigurationSection sec = root.getConfigurationSection(enchantId);
        if (sec == null) return map;

        fr.batistou15.prestigehoe.data.PlayerProfile profile =
                plugin.getPlayerDataManager().getProfile(viewer);
        HoeData hoe = profile != null ? profile.getHoeData() : null;
        if (hoe == null) {
            hoe = new HoeData();
        }

        int currentLevel = hoe.getEnchantLevel(enchantId, 0);
        int maxLevelConfig = sec.getInt("level.max-level", 0);

        double essenceBalance = profile != null ? profile.getEssence() : 0.0;

        // === Infos de base ===
        map.put("%enchant_id%", enchantId);

        String displayName = sec.getString("display-name", enchantId);
        map.put("%enchant_display_name%", displayName != null ? displayName : enchantId);

        map.put("%enchant_level_current%", String.valueOf(currentLevel));
        if (maxLevelConfig > 0) {
            map.put("%enchant_level_max%", String.valueOf(maxLevelConfig));
        } else {
            map.put("%enchant_level_max%", "∞");
        }

        // === Chances de proc ===
        double procBaseChance = 0.0D;
        double procChancePerLevel = 0.0D;
        double procCurrentChance = 0.0D;
        double procNextChance = 0.0D;

        try {
            ConfigurationSection chanceSec = sec.getConfigurationSection("chance");
            if (chanceSec != null) {
                procBaseChance = chanceSec.getDouble("base-chance", 0.0D);
                procChancePerLevel = chanceSec.getDouble("chance-per-level", 0.0D);

                procCurrentChance = procBaseChance + (procChancePerLevel * currentLevel);
                procNextChance = procBaseChance + (procChancePerLevel * (currentLevel + 1));

                if (procCurrentChance < 0.0D) procCurrentChance = 0.0D;
                if (procNextChance < 0.0D) procNextChance = 0.0D;
                if (procCurrentChance > 1.0D) procCurrentChance = 1.0D;
                if (procNextChance > 1.0D) procNextChance = 1.0D;
            }
        } catch (Throwable ignored) {
        }

        map.put("%enchant_proc_chance_current%", String.format(Locale.US, "%.4f", procCurrentChance));
        map.put("%enchant_proc_chance_next%", String.format(Locale.US, "%.4f", procNextChance));
        map.put("%enchant_proc_chance_per_level%", String.format(Locale.US, "%.4f", procChancePerLevel));

        map.put("%enchant_proc_chance_current_percent%", String.format(Locale.US, "%.2f%%", procCurrentChance * 100.0D));
        map.put("%enchant_proc_chance_next_percent%", String.format(Locale.US, "%.2f%%", procNextChance * 100.0D));
        map.put("%enchant_proc_chance_per_level_percent%", String.format(Locale.US, "%.2f%%", procChancePerLevel * 100.0D));

        // === Multiplicateurs pour les enchants de boost (essence, argent) ===
        double boostMultiplier = 1.0D;
        try {
            if ("essence_booster".equalsIgnoreCase(enchantId)
                    && plugin.getEnchantManager() != null
                    && plugin.getEnchantManager().getEssenceBoosterEnchant() != null) {
                boostMultiplier = plugin.getEnchantManager().getEssenceBoosterEnchant().getMultiplier(hoe);
            } else if ("money_booster".equalsIgnoreCase(enchantId)
                    && plugin.getEnchantManager() != null
                    && plugin.getEnchantManager().getMoneyBoosterEnchant() != null) {
                boostMultiplier = plugin.getEnchantManager().getMoneyBoosterEnchant().getMoneyMultiplier(hoe);
            }
            // PAS de getHoeXpBoosterEnchant ici pour éviter l'erreur de compile
        } catch (Throwable ignored) {
        }

        double boostBonus = Math.max(0.0D, boostMultiplier - 1.0D);
        map.put("%enchant_boost_multiplier%", String.format(Locale.US, "x%.2f", boostMultiplier));
        map.put("%enchant_boost_percent%", String.format(Locale.US, "+%.2f%%", boostBonus * 100.0D));

        // === Coût "1 niveau" ===
        String essenceSingleText;
        if (maxLevelConfig > 0 && currentLevel >= maxLevelConfig) {
            essenceSingleText = "Niveau max";
        } else {
            double essenceSingle = plugin.getEnchantManager()
                    .getEssenceCostForNextLevel(enchantId, currentLevel);
            essenceSingleText = NumberFormatUtil.formatShort(essenceSingle);
        }
        map.put("%enchant_cost_essence_single%", essenceSingleText);
        map.put("%enchant_cost_money_single%", "0");

        // === Coût pour aller au niveau max ===
        int levelsToMax = 0;
        double costToMax = 0.0;

        if (maxLevelConfig > 0 && currentLevel < maxLevelConfig) {
            levelsToMax = maxLevelConfig - currentLevel;
            costToMax = computeTotalEssenceCost(enchantId, currentLevel, levelsToMax, maxLevelConfig);
            map.put("%enchant_cost_essence_max%", NumberFormatUtil.formatShort(costToMax));
        } else {
            map.put("%enchant_cost_essence_max%", "Niveau max");
        }
        map.put("%enchant_levels_to_max%", String.valueOf(levelsToMax));

        // === Combien de niveaux il peut acheter avec son essence actuelle ===
        int affordableLevels = 0;
        double affordableCost = 0.0;
        if (maxLevelConfig > 0 && currentLevel < maxLevelConfig && essenceBalance > 0) {
            int level = currentLevel;
            int remaining = maxLevelConfig - currentLevel;
            for (int i = 0; i < remaining; i++) {
                double cost = plugin.getEnchantManager().getEssenceCostForNextLevel(enchantId, level);
                if (cost <= 0) break;
                if (essenceBalance < affordableCost + cost) break;

                affordableCost += cost;
                affordableLevels++;
                level++;
            }
        }
        map.put("%enchant_affordable_levels%", String.valueOf(affordableLevels));
        map.put("%enchant_cost_essence_affordable%",
                affordableLevels > 0 ? NumberFormatUtil.formatShort(affordableCost) : "0");

        // === Coûts pré-calculés pour X niveaux ===
        int[] steps = {1, 10, 50, 100, 500, 1000};
        for (int step : steps) {
            String keyEss = "%enchant_cost_essence_x" + step + "%";
            String keyMoney = "%enchant_cost_money_x" + step + "%";

            if (maxLevelConfig > 0 && currentLevel >= maxLevelConfig) {
                map.put(keyEss, "Niveau max");
                map.put(keyMoney, "");
            } else {
                int levelsToBuy = step;
                if (maxLevelConfig > 0) {
                    int remaining = maxLevelConfig - currentLevel;
                    if (remaining < levelsToBuy) {
                        levelsToBuy = Math.max(0, remaining);
                    }
                }
                double total = computeTotalEssenceCost(enchantId, currentLevel, levelsToBuy, maxLevelConfig);
                map.put(keyEss, NumberFormatUtil.formatShort(total));
                map.put(keyMoney, "");
            }
        }

        // === Statut des notifs d'enchant ===
        String notifStatus = "ON";
        if (profile != null && !profile.isEnchantMessagesEnabledGlobal()) {
            notifStatus = "OFF";
        }
        map.put("%enchant_notif_status%", notifStatus);

        // === Construction de la description AVEC les placeholders ===
        try {
            MenuPlaceholderService placeholderService = plugin
                    .getMenuManager()
                    .getPlaceholderService();

            List<String> descLinesRaw = sec.getStringList("description");
            if (descLinesRaw != null && !descLinesRaw.isEmpty()) {
                List<String> descLinesResolved = new ArrayList<>();
                for (String raw : descLinesRaw) {
                    String resolved = placeholderService.applyPlaceholders(raw, map);
                    descLinesResolved.add(resolved);
                }
                map.put("%enchant_description_lines%", String.join("\n", descLinesResolved));
            } else {
                map.put("%enchant_description_lines%", "");
            }
        } catch (Throwable t) {
            List<String> descLinesRaw = sec.getStringList("description");
            if (descLinesRaw != null && !descLinesRaw.isEmpty()) {
                map.put("%enchant_description_lines%", String.join("\n", descLinesRaw));
            } else {
                map.put("%enchant_description_lines%", "");
            }
        }

        return map;
    }

    private double computeTotalEssenceCost(String enchantId,
                                           int currentLevel,
                                           int levelsToBuy,
                                           int maxLevelConfig) {
        double total = 0.0;

        try {
            if (plugin.getEnchantManager() == null) {
                return 0.0;
            }

            int level = currentLevel;
            for (int i = 0; i < levelsToBuy; i++) {
                if (maxLevelConfig > 0 && level >= maxLevelConfig) {
                    break;
                }

                double cost = plugin.getEnchantManager().getEssenceCostForNextLevel(enchantId, level);
                total += cost;
                level++;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de calculer le coût total pour " + enchantId, t);
        }

        return total;
    }

    public Map<String, String> buildDisenchantPlaceholders(Player viewer, String enchantId) {
        Map<String, String> map = new HashMap<>();

        if (viewer == null || enchantId == null || enchantId.isEmpty()) {
            return map;
        }

        FileConfiguration enchantsCfg = plugin.getConfigManager().getEnchantsConfig();
        ConfigurationSection root = enchantsCfg.getConfigurationSection("enchants");
        if (root == null) return map;

        ConfigurationSection sec = root.getConfigurationSection(enchantId);
        if (sec == null) return map;

        fr.batistou15.prestigehoe.data.PlayerProfile profile =
                plugin.getPlayerDataManager().getProfile(viewer);
        HoeData hoe = profile != null ? profile.getHoeData() : null;
        if (hoe == null) {
            hoe = new HoeData();
        }

        int currentLevel = hoe.getEnchantLevel(enchantId, 0);
        int maxLevelConfig = sec.getInt("level.max-level", 0);

        map.put("%enchant_id%", enchantId);

        String displayName = sec.getString("display-name", enchantId);
        map.put("%enchant_display_name%", displayName != null ? displayName : enchantId);

        List<String> descLines = sec.getStringList("description");
        if (descLines != null && !descLines.isEmpty()) {
            map.put("%enchant_description_lines%", String.join("\n", descLines));
        } else {
            map.put("%enchant_description_lines%", "");
        }

        map.put("%enchant_level_current%", String.valueOf(currentLevel));
        if (maxLevelConfig > 0) {
            map.put("%enchant_level_max%", String.valueOf(maxLevelConfig));
        } else {
            map.put("%enchant_level_max%", "∞");
        }

        int removableLevels = currentLevel;
        map.put("%enchant_removable_levels%", String.valueOf(removableLevels));

        String refundSingleText;
        if (currentLevel <= 0) {
            refundSingleText = "Aucun niveau";
        } else {
            double refundSingle = computeTotalEssenceRefund(enchantId, currentLevel, 1);
            refundSingleText = NumberFormatUtil.formatShort(refundSingle);
        }
        map.put("%enchant_refund_essence_single%", refundSingleText);

        int[] steps = {1, 10, 50, 100, 500, 1000};
        for (int step : steps) {
            String keyEss = "%enchant_refund_essence_x" + step + "%";

            if (currentLevel <= 0) {
                map.put(keyEss, "Aucun niveau");
            } else {
                int levelsToRemove = Math.min(step, currentLevel);
                double total = computeTotalEssenceRefund(enchantId, currentLevel, levelsToRemove);
                map.put(keyEss, NumberFormatUtil.formatShort(total));
            }
        }

        if (removableLevels > 0) {
            double refundMax = computeTotalEssenceRefund(enchantId, currentLevel, removableLevels);
            map.put("%enchant_refund_essence_max%", NumberFormatUtil.formatShort(refundMax));
        } else {
            map.put("%enchant_refund_essence_max%", "Aucun niveau");
        }
        // === Construction finale de la description avec les placeholders déjà calculés ===
        try {
            MenuPlaceholderService placeholderService = plugin
                    .getMenuManager()
                    .getPlaceholderService();

            List<String> descLinesRaw = sec.getStringList("description");
            if (descLinesRaw != null && !descLinesRaw.isEmpty()) {
                List<String> descLinesResolved = new ArrayList<>();
                for (String raw : descLinesRaw) {
                    // on applique tous les %...% connus dans "map"
                    String resolved = placeholderService.applyPlaceholders(raw, map);
                    descLinesResolved.add(resolved);
                }
                map.put("%enchant_description_lines%", String.join("\n", descLinesResolved));
            } else {
                map.put("%enchant_description_lines%", "");
            }
        } catch (Throwable t) {
            // fallback : description brute si jamais un truc foire
            List<String> descLinesRaw = sec.getStringList("description");
            if (descLinesRaw != null && !descLinesRaw.isEmpty()) {
                map.put("%enchant_description_lines%", String.join("\n", descLinesRaw));
            } else {
                map.put("%enchant_description_lines%", "");
            }
        }

        return map;
    }

    private double computeTotalEssenceRefund(String enchantId,
                                             int currentLevel,
                                             int levelsToRemove) {
        if (levelsToRemove <= 0 || currentLevel <= 0) {
            return 0.0;
        }

        double refundPercent = plugin.getConfigManager()
                .getMainConfig()
                .getDouble("disenchant.essence-refund-percent", 50.0);

        double totalCost = 0.0;
        int level = currentLevel;

        try {
            if (plugin.getEnchantManager() == null) {
                return 0.0;
            }

            for (int i = 0; i < levelsToRemove; i++) {
                if (level <= 0) break;

                int fromLevel = level - 1;
                double cost = plugin.getEnchantManager()
                        .getEssenceCostForNextLevel(enchantId, fromLevel);

                if (cost <= 0) break;

                totalCost += cost;
                level--;
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de calculer le refund total pour " + enchantId, t);
        }

        return totalCost * (refundPercent / 100.0);
    }
}
