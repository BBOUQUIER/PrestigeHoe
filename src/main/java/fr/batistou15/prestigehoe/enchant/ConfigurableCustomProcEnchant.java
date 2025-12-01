package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enchant générique pour gérer:
 *  - custom_enchant1
 *  - custom_enchant2
 *  - custom_enchantX ...
 *
 * Config supportée (exemples) :
 *
 * custom_enchant1:
 *   enabled: true
 *   chance:
 *     base-chance: 0.01
 *     chance-per-level: 0.001
 *     max-chance: 1.0
 *   settings:
 *     rewards:
 *       - chance: 0.90
 *         commands:
 *           - "give %player% SPRUCE_BUTTON 1"
 *         on-proc-message:
 *           - "say %prefix%&dCustomEnchant s'est déclenché !"
 *       - chance: 0.10
 *         commands:
 *           - "give %player% DIAMOND 1"
 *
 * custom_enchant2:
 *   enabled: true
 *   chance:
 *     base-chance: 1.0
 *     chance-per-level: 0.0
 *   settings:
 *     rewards:
 *       - chance: 1.0
 *         commands:
 *           - "say CustomEnchant2 proc pour %player%."
 */
public class ConfigurableCustomProcEnchant implements HoeEnchant {

    private final PrestigeHoePlugin plugin;
    private final String id; // ex: "custom_enchant1"

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    // chance:
    private double baseChance;
    private double chancePerLevel;
    private double maxChance;

    public static class RewardDef {
        final double chance;
        final List<String> commands;
        final List<String> messages; // messages spécifiques (on-proc-message)

        RewardDef(double chance, List<String> commands, List<String> messages) {
            this.chance = chance;
            this.commands = commands;
            this.messages = messages;
        }
    }

    private final List<RewardDef> rewards = new ArrayList<>();

    // notifications:
    private boolean notifEnabled;
    private String notifMessage;   // notifications.on-proc-message
    private String notifDisplayMode; // CHAT / ACTIONBAR (pour plus tard si tu veux)

    public ConfigurableCustomProcEnchant(PrestigeHoePlugin plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
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
        FileConfiguration enchCfg = configManager.getEnchantsConfig();
        ConfigurationSection sec = enchCfg.getConfigurationSection("enchants." + id);
        if (sec == null) {
            plugin.getLogger().warning("[CustomProcEnchant] Section enchants." + id + " introuvable");
            enabled = false;
            rewards.clear();
            return;
        }

        enabled = sec.getBoolean("enabled", true);

        // --- Level ---
        ConfigurationSection levelSec = sec.getConfigurationSection("level");
        if (levelSec != null) {
            maxLevel = levelSec.getInt("max-level", 50);
            defaultLevel = levelSec.getInt("default-level", 0);
        } else {
            maxLevel = 50;
            defaultLevel = 0;
        }

        // --- Chance ---
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

        // --- Rewards / settings ---
        rewards.clear();
        ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
        if (settingsSec != null) {

            // Cas 1 : settings.rewards est une liste de rewards
            if (settingsSec.isList("rewards")) {
                List<Map<?, ?>> rawList = settingsSec.getMapList("rewards");
                for (Map<?, ?> map : rawList) {
                    if (map == null) continue;

                    double chance = 0.0D;
                    Object chanceObj = map.get("chance");
                    if (chanceObj instanceof Number) {
                        chance = ((Number) chanceObj).doubleValue();
                    } else if (chanceObj != null) {
                        try {
                            chance = Double.parseDouble(String.valueOf(chanceObj));
                        } catch (Exception ignored) {
                        }
                    }

                    List<String> commands = new ArrayList<>();
                    Object cmdsObj = map.get("commands");
                    if (cmdsObj instanceof List) {
                        for (Object c : (List<?>) cmdsObj) {
                            if (c != null) {
                                commands.add(String.valueOf(c));
                            }
                        }
                    }

                    List<String> rewardMessages = null;
                    Object msgObj = map.get("on-proc-message");
                    if (msgObj instanceof List) {
                        rewardMessages = new ArrayList<>();
                        for (Object l : (List<?>) msgObj) {
                            if (l != null) {
                                rewardMessages.add(String.valueOf(l));
                            }
                        }
                    } else if (msgObj instanceof String) {
                        rewardMessages = Collections.singletonList((String) msgObj);
                    }

                    if (chance > 0.0D && !commands.isEmpty()) {
                        rewards.add(new RewardDef(chance, commands, rewardMessages));
                    }
                }
            }
            // Cas 2 : vieux style : settings.commands (sans weighting)
            else if (settingsSec.isList("commands")) {
                List<String> cmds = settingsSec.getStringList("commands");
                if (!cmds.isEmpty()) {
                    rewards.add(new RewardDef(1.0D, cmds, null));
                }
            }
        }

        // --- Notifications ---
        ConfigurationSection notifSec = sec.getConfigurationSection("notifications");
        if (notifSec != null) {
            notifEnabled = notifSec.getBoolean("enabled", true);
            notifDisplayMode = notifSec.getString("display-mode", "CHAT").toUpperCase(Locale.ROOT);
            notifMessage = notifSec.getString(
                    "on-proc-message",
                    "%prefix%&d" + id + " &as'est déclenché !"
            );
        } else {
            notifEnabled = false;
            notifDisplayMode = "CHAT";
            notifMessage = null;
        }

        plugin.getLogger().info("[CustomProcEnchant] Loaded '" + id
                + "' enabled=" + enabled
                + ", maxLevel=" + maxLevel
                + ", rewards=" + rewards.size());
    }

    /**
     * Appelé depuis FarmExecutionService pour tenter un proc.
     */
    public void tryExecute(Player player, HoeData hoe) {
        if (!enabled || player == null || hoe == null) return;

        int level = hoe.getEnchantLevel(id, 0);
        if (level <= 0) {
            return;
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) {
            return;
        }

        if (rewards.isEmpty()) {
            return;
        }

        // 🔥 On utilise le helper commun avec l'id de l'enchant
        EnchantProcHelper.ProcData proc = EnchantProcHelper.computeProcData(
                plugin,
                profile,
                hoe,
                id,
                baseChance,
                chancePerLevel,
                maxChance,
                level
        );

        double chanceToUse = proc.totalCurrent;
        if (chanceToUse <= 0.0D) {
            return;
        }

        double roll = ThreadLocalRandom.current().nextDouble(); // [0,1)
        if (roll > chanceToUse) {
            return; // pas de proc
        }

        // Choisir un reward en fonction de ses chances relatives
        RewardDef chosen = pickReward();
        if (chosen == null) return;

        // Exécuter les commandes
        for (String raw : chosen.commands) {
            if (raw == null || raw.isEmpty()) continue;

            String cmd = raw
                    .replace("%player%", player.getName())
                    .replace("%prefix%", MessageUtil.getPrefix());

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }

        // Messages :
        sendMessagesOnProc(player, chosen);
    }


    /**
     * Sélectionne un reward en pondérant avec "chance".
     * Si la somme des chances n'est pas 1.0, on normalise quand même :
     *   r in [0, totalChance)
     *   on prend le premier pour lequel sum >= r.
     */
    private RewardDef pickReward() {
        if (rewards.isEmpty()) return null;
        if (rewards.size() == 1) return rewards.get(0);

        double totalChance = 0.0D;
        for (RewardDef r : rewards) {
            if (r.chance > 0.0D) {
                totalChance += r.chance;
            }
        }
        if (totalChance <= 0.0D) {
            return rewards.get(0);
        }

        double roll = ThreadLocalRandom.current().nextDouble() * totalChance;
        double cumulative = 0.0D;
        for (RewardDef r : rewards) {
            if (r.chance <= 0.0D) continue;
            cumulative += r.chance;
            if (roll <= cumulative) {
                return r;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    private void sendMessagesOnProc(Player player, RewardDef reward) {
        if (player == null || reward == null) return;

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) return;

        // Respect des paramètres joueur
        if (!profile.isEnchantMessagesEnabledGlobal()
                || !profile.isEnchantProcMessagesEnabledGlobal()
                || !profile.isEnchantProcMessageEnabled(id)   // id = "custom_enchant1", etc.
                || !profile.isChatNotificationsEnabled()) {   // ⚠️ toggle CHAT global
            return;
        }

        // 1) Messages spécifiques au reward
        if (reward.messages != null && !reward.messages.isEmpty()) {
            for (String raw : reward.messages) {
                if (raw == null || raw.isEmpty()) continue;
                String line = raw
                        .replace("%player%", player.getName())
                        .replace("%prefix%", MessageUtil.getPrefix());
                MessageUtil.sendPlain(player, MessageUtil.color(line));
            }
            return;
        }

        // 2) Sinon message global
        if (!notifEnabled || notifMessage == null || notifMessage.isEmpty()) {
            return;
        }

        String line = notifMessage
                .replace("%player%", player.getName())
                .replace("%prefix%", MessageUtil.getPrefix());

        MessageUtil.sendPlain(player, MessageUtil.color(line));
    }



}
