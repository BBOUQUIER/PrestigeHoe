package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class FuryEnchant implements HoeEnchant {

    public static final String ID = "furie";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    // Chance
    private double baseChance;
    private double chancePerLevel;
    private double maxChance;

    // 🔥 NOUVEAU : bonus numériques par niveau
    private double moneyBonusPerLevel;
    private double essenceBonusPerLevel;
    // Tiers de durée
    private static class Tier {
        final int levelMin;
        final int durationSeconds;

        Tier(int levelMin, int durationSeconds) {
            this.levelMin = levelMin;
            this.durationSeconds = durationSeconds;
        }
    }

    private java.util.List<Tier> tiers = new java.util.ArrayList<>();

    // Formules
    private String moneyBonusFormula;   // ex: "1 + (level * 0.1)"
    private String essenceBonusFormula; // ex: "1 + (level * 0.1)"

    // Notifications
    private boolean notifEnabled;
    private String notifTitle;
    private String notifSubtitle;

    // État de furie par joueur
    private static class FuryState {
        long endTimeMillis;
        double moneyMultiplier;
        double essenceMultiplier;
        long nextNotifyMillis;
    }

    private final Map<UUID, FuryState> activeFuries = new ConcurrentHashMap<>();
    private int taskId = -1;

    public FuryEnchant(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        startTask();
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
            plugin.getLogger().warning("[FuryEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        maxLevel = sec.getInt("level.max-level", 50);
        defaultLevel = sec.getInt("level.default-level", 0);

        // Chance
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

        // === Tiers de durée & bonus ===
        tiers.clear();
        ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
        if (settingsSec != null) {
            java.util.List<Map<?, ?>> tierList = settingsSec.getMapList("tiers");
            if (tierList != null) {
                for (Map<?, ?> map : tierList) {
                    if (map == null) continue;
                    int levelMin = getInt(map, "level-min", 1);
                    int duration = getInt(map, "duration-seconds", 30);
                    tiers.add(new Tier(levelMin, duration));
                }
            }

            // On lit encore les strings (au cas où tu les utilises plus tard)
            moneyBonusFormula = settingsSec.getString("money_bonus_formula", "1 + (level * 0.1)");
            essenceBonusFormula = settingsSec.getString("essence_bonus_formula", "1 + (level * 0.1)");

            // 🔥 NOUVEAU : bonus numériques par niveau
            moneyBonusPerLevel = settingsSec.getDouble("money_bonus_per_level", 0.10D);
            essenceBonusPerLevel = settingsSec.getDouble("essence_bonus_per_level", 0.10D);
        } else {
            moneyBonusFormula = "1 + (level * 0.1)";
            essenceBonusFormula = "1 + (level * 0.1)";
            moneyBonusPerLevel = 0.10D;
            essenceBonusPerLevel = 0.10D;
        }

        // Notifications
        ConfigurationSection notifSec = sec.getConfigurationSection("notifications");
        if (notifSec != null) {
            notifEnabled = notifSec.getBoolean("enabled", true);
            notifTitle = notifSec.getString("on-proc-title", "&4&lFURIE !");
            notifSubtitle = notifSec.getString("on-proc-subtitle",
                    "&7Tes gains sont augmentés pendant %seconds% secondes.");
        } else {
            notifEnabled = false;
            notifTitle = null;
            notifSubtitle = null;
        }

        plugin.getLogger().info(String.format(
                Locale.US,
                "[PrestigeHoe] [FuryEnchant] enabled=%s, maxLevel=%d, baseChance=%.4f, chancePerLevel=%.4f, tiers=%d",
                enabled, maxLevel, baseChance, chancePerLevel, tiers.size()
        ));
    }

    private int getInt(Map<?, ?> map, String key, int def) {
        Object o = map.get(key);
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    // =========== API utilisée par le farm & les menus ===========

    /**
     * Durée (en secondes) de la furie pour un niveau donné.
     */
    public int getDurationSecondsForLevel(int level) {
        if (tiers.isEmpty()) return 0;

        // tier avec levelMin le plus grand <= level
        Tier best = null;
        for (Tier t : tiers) {
            if (level >= t.levelMin) {
                if (best == null || t.levelMin > best.levelMin) {
                    best = t;
                }
            }
        }
        if (best != null) return best.durationSeconds;

        // sinon, le plus petit level-min
        Tier lowest = tiers.get(0);
        for (Tier t : tiers) {
            if (t.levelMin < lowest.levelMin) {
                lowest = t;
            }
        }
        return lowest.durationSeconds;
    }

    public double computeMoneyMultiplierForLevel(int level) {
        if (level <= 0) return 1.0D;
        return 1.0D + (moneyBonusPerLevel * level);
    }

    public double computeEssenceMultiplierForLevel(int level) {
        if (level <= 0) return 1.0D;
        return 1.0D + (essenceBonusPerLevel * level);
    }

    private double evalLinearLevelFormula(String formula, int level) {
        // On s'attend à un truc du style "1 + (level * 0.1)"
        if (formula == null || formula.isEmpty()) return 1.0D;
        try {
            int idx = formula.indexOf("level *");
            if (idx == -1) return 1.0D;
            int start = idx + "level *".length();
            int end = formula.indexOf(")", start);
            if (end == -1) end = formula.length();
            String numStr = formula.substring(start, end).trim();
            double coef = Double.parseDouble(numStr);
            return 1.0D + level * coef;
        } catch (Exception e) {
            return 1.0D;
        }
    }

    /**
     * Multiplicateur d'argent actuellement actif pour ce joueur.
     */
    public double getCurrentMoneyMultiplier(Player player) {
        if (player == null) return 1.0D;
        FuryState state = activeFuries.get(player.getUniqueId());
        if (state == null) return 1.0D;
        long now = System.currentTimeMillis();
        if (now >= state.endTimeMillis) {
            activeFuries.remove(player.getUniqueId());
            return 1.0D;
        }
        return state.moneyMultiplier;
    }

    /**
     * Multiplicateur d'essence actuellement actif pour ce joueur.
     */
    public double getCurrentEssenceMultiplier(Player player) {
        if (player == null) return 1.0D;
        FuryState state = activeFuries.get(player.getUniqueId());
        if (state == null) return 1.0D;
        long now = System.currentTimeMillis();
        if (now >= state.endTimeMillis) {
            activeFuries.remove(player.getUniqueId());
            return 1.0D;
        }
        return state.essenceMultiplier;
    }

    // =========== Tente un proc à chaque casse de bloc ===========

    public void tryExecute(Player player, HoeData hoe) {
        if (!enabled || player == null || hoe == null) return;

        int level = hoe.getEnchantLevel(ID, 0);
        if (level <= 0) return;

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) return;

        // === Chance de proc avec perks + proc_booster ===
        EnchantProcHelper.ProcData procData = EnchantProcHelper.computeProcData(
                plugin,
                profile,
                hoe,
                ID,                 // "furie"
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

        // On (re)démarre une furie pour ce joueur
        int durationSeconds = getDurationSecondsForLevel(level);
        long now = System.currentTimeMillis();

        FuryState state = new FuryState();
        state.endTimeMillis = now + durationSeconds * 1000L;
        state.moneyMultiplier = computeMoneyMultiplierForLevel(level);
        state.essenceMultiplier = computeEssenceMultiplierForLevel(level);
        state.nextNotifyMillis = now; // pour envoyer direct un message / actionbar

        activeFuries.put(player.getUniqueId(), state);

        // Notif de début (Title), en respectant les toggles
        if (notifEnabled
                && profile.isEnchantMessagesEnabledGlobal()
                && profile.isEnchantProcMessagesEnabledGlobal()
                && profile.isEnchantProcMessageEnabled(ID)
                && profile.isTitleNotificationsEnabled()) {

            String title = notifTitle != null ? notifTitle : "&4&lFURIE !";
            String subtitle = notifSubtitle != null
                    ? notifSubtitle
                    : "&7Tes gains sont augmentés pendant %seconds% secondes.";

            // support %seconds%, %sencondes%, %duration_time_furie%
            subtitle = subtitle
                    .replace("%seconds%", String.valueOf(durationSeconds))
                    .replace("%sencondes%", String.valueOf(durationSeconds))
                    .replace("%duration_time_furie%", durationSeconds + "s");

            title = MessageUtil.color(title);
            subtitle = MessageUtil.color(subtitle);

            player.sendTitle(title, subtitle, 10, 40, 10);
        }
    }


    // =========== Tâche répétée pour afficher le temps restant ===========

    private void startTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        taskId = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tick,
                20L,   // delay 1s
                20L    // toutes les 1s
        ).getTaskId();
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, FuryState> entry : activeFuries.entrySet()) {
            UUID uuid = entry.getKey();
            FuryState state = entry.getValue();

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                activeFuries.remove(uuid);
                continue;
            }

            if (now >= state.endTimeMillis) {
                activeFuries.remove(uuid);
                continue;
            }

            // On envoie l'ActionBar toutes les 5 secondes
            if (now >= state.nextNotifyMillis) {
                long remainingMs = state.endTimeMillis - now;
                long remainingSeconds = Math.max(0L, (remainingMs + 999L) / 1000L);

                String msg = "&4FURIE &7- Temps restant: &c" + remainingSeconds + "s";
                msg = MessageUtil.color(msg);

                player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(msg)
                );

                state.nextNotifyMillis = now + 5000L; // prochain tick dans 5s
            }
        }
    }
}
