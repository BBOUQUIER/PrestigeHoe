package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class TokenFinderEnchant implements HoeEnchant {

    public static final String ID = "token_finder";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    // chance:
    private double baseChance;
    private double chancePerLevel;
    private double maxChance;

    /**
     * Reward configurable :
     *  - weight (chance)
     *  - tokens (valeur numérique pour les placeholders)
     *  - commands (liste de commandes à exécuter)
     * Le plugin NE touche PAS à prestigeTokens, "tokens" est juste une info pour message/commande.
     */
    private static class RewardEntry {
        final double weight;
        final int tokens;
        final List<String> commands;

        RewardEntry(double weight, int tokens, List<String> commands) {
            this.weight = weight;
            this.tokens = tokens;
            this.commands = commands;
        }
    }

    private final List<RewardEntry> rewards = new ArrayList<>();

    // Notifications
    private boolean notifEnabled;
    private String notifMessage; // notifications.on-proc-message

    public TokenFinderEnchant(PrestigeHoePlugin plugin) {
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
            plugin.getLogger().warning("[TokenFinderEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            baseChance = 0.0D;
            chancePerLevel = 0.0D;
            maxChance = 1.0D;
            rewards.clear();
            notifEnabled = false;
            notifMessage = null;
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

        // === Rewards (settings.rewards: [ { chance, tokens, commands } ]) ===
        rewards.clear();
        ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
        if (settingsSec != null) {
            List<Map<?, ?>> rewardList = settingsSec.getMapList("rewards");
            if (rewardList != null) {
                for (Map<?, ?> map : rewardList) {
                    if (map == null) continue;

                    double weight = getDouble(map, "chance", 0.0D);
                    int tokens = getInt(map, "tokens", 0);

                    // Liste de commandes optionnelle
                    List<String> commands = new ArrayList<>();
                    Object cmdsObj = map.get("commands");
                    if (cmdsObj instanceof List<?>) {
                        for (Object o : (List<?>) cmdsObj) {
                            if (o == null) continue;
                            commands.add(String.valueOf(o));
                        }
                    }

                    // Il faut au moins une chance > 0 et soit tokens>0, soit des commandes
                    if (weight <= 0.0D) continue;
                    if (tokens <= 0 && commands.isEmpty()) continue;

                    rewards.add(new RewardEntry(weight, tokens, commands));
                }
            }
        }

        // === Notifications ===
        ConfigurationSection notifSec = sec.getConfigurationSection("notifications");
        if (notifSec != null) {
            notifEnabled = notifSec.getBoolean("enabled", true);
            notifMessage = notifSec.getString(
                    "on-proc-message",
                    "%prefix%&dTu as trouvé &5%token_formatted% &dcrédits !"
            );
        } else {
            notifEnabled = false;
            notifMessage = null;
        }

        plugin.getLogger().info(String.format(
                Locale.US,
                "[PrestigeHoe] [TokenFinderEnchant] enabled=%s, maxLevel=%d, baseChance=%.5f, perLevel=%.5f, rewards=%d",
                enabled, maxLevel, baseChance, chancePerLevel, rewards.size()
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
     * Sélectionne une reward en fonction des poids "chance".
     */
    private RewardEntry pickReward() {
        if (rewards.isEmpty()) return null;

        double totalWeight = 0.0D;
        for (RewardEntry r : rewards) {
            totalWeight += Math.max(0.0D, r.weight);
        }
        if (totalWeight <= 0.0D) return null;

        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0.0D;
        RewardEntry last = null;

        for (RewardEntry r : rewards) {
            double w = Math.max(0.0D, r.weight);
            if (w <= 0.0D) continue;
            cumulative += w;
            last = r;
            if (roll <= cumulative) {
                return r;
            }
        }
        return last;
    }

    /**
     * Proc à chaque bloc cassé, appelé depuis FarmExecutionService.
     */
    public void tryExecute(Player player, HoeData hoe) {
        if (!enabled || player == null || hoe == null) return;

        int level = hoe.getEnchantLevel(ID, 0);
        if (level <= 0) return;

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) return;

        // Chance de proc avec perks + proc_booster
        EnchantProcHelper.ProcData procData = EnchantProcHelper.computeProcData(
                plugin,
                profile,
                hoe,
                TokenFinderEnchant.ID,    // "token_finder"
                baseChance,
                chancePerLevel,
                maxChance,
                level
        );

        double chanceToUse = procData.totalCurrent;
        if (chanceToUse <= 0.0D) return;

        double roll = ThreadLocalRandom.current().nextDouble(); // [0,1)
        if (roll > chanceToUse) {
            return; // pas de proc
        }

        RewardEntry reward = pickReward();
        if (reward == null) {
            return;
        }

        int tokens = Math.max(0, reward.tokens);
        String tokensFormatted = NumberFormatUtil.formatShort(tokens);

        // Exécution des commandes
        if (!reward.commands.isEmpty()) {
            for (String rawCmd : reward.commands) {
                if (rawCmd == null || rawCmd.isEmpty()) continue;
                String cmd = rawCmd
                        .replace("%player%", player.getName())
                        .replace("%tokens%", String.valueOf(tokens))
                        .replace("%token%", String.valueOf(tokens))
                        .replace("%tokens_formatted%", tokensFormatted)
                        .replace("%token_formatted%", tokensFormatted);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }

        // Message générique, avec le nombre, respectant les toggles
        if (notifEnabled
                && notifMessage != null && !notifMessage.isEmpty()
                && profile.isEnchantMessagesEnabledGlobal()
                && profile.isEnchantProcMessagesEnabledGlobal()
                && profile.isEnchantProcMessageEnabled(TokenFinderEnchant.ID)
                && profile.isChatNotificationsEnabled()) {

            String msg = notifMessage
                    .replace("%prefix%", MessageUtil.getPrefix())
                    .replace("%player%", player.getName())
                    .replace("%tokens%", String.valueOf(tokens))
                    .replace("%token%", String.valueOf(tokens))
                    .replace("%tokens_formatted%", tokensFormatted)
                    .replace("%token_formatted%", tokensFormatted);

            MessageUtil.sendPlain(player, msg);
        }
    }

}
