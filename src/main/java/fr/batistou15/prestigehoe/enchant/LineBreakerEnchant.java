package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class LineBreakerEnchant implements HoeEnchant {

    public static final String ID = "linebreaker";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    private double baseChance;
    private double chancePerLevel;
    private double maxChance;

    // Mode visuel : PLAYER_ONLY / EVERYONE
    private String visualMode;

    // Notifications
    private boolean notifEnabled;
    private String notifDisplayMode;   // "CHAT" / "ACTIONBAR" / "TITLE"
    private String notifMessage;       // notifications.on-proc-message
    private String notifTitle;         // notifications.on-proc-title (optionnel)
    private String notifSubtitle;      // notifications.on-proc-subtitle (optionnel)

    private static class LengthTier {
        final int levelMin;
        final int length;

        LengthTier(int levelMin, int length) {
            this.levelMin = levelMin;
            this.length = length;
        }
    }

    private final List<LengthTier> tiers = new ArrayList<>();

    public LineBreakerEnchant(PrestigeHoePlugin plugin) {
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
            plugin.getLogger().warning("[LineBreakerEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            baseChance = 0.0D;
            chancePerLevel = 0.0D;
            maxChance = 1.0D;
            visualMode = "PLAYER_ONLY";
            tiers.clear();
            notifEnabled = false;
            notifDisplayMode = "CHAT";
            notifMessage = null;
            notifTitle = null;
            notifSubtitle = null;
            return;
        }

        enabled = sec.getBoolean("enabled", true);

        // Niveau
        ConfigurationSection levelSec = sec.getConfigurationSection("level");
        if (levelSec != null) {
            maxLevel = levelSec.getInt("max-level", 100);
            defaultLevel = levelSec.getInt("default-level", 0);
        } else {
            maxLevel = 100;
            defaultLevel = 0;
        }

        // Chance
        ConfigurationSection chanceSec = sec.getConfigurationSection("chance");
        if (chanceSec != null) {
            baseChance = chanceSec.getDouble("base-chance", 0.0D);
            chancePerLevel = chanceSec.getDouble("chance-per-level", 0.0D);
            maxChance = chanceSec.getDouble("max-chance", 1.0D);
            if (maxChance <= 0.0D) {
                maxChance = 1.0D;
            }
        } else {
            baseChance = 0.0D;
            chancePerLevel = 0.0D;
            maxChance = 1.0D;
        }

        // Settings
        tiers.clear();
        ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
        if (settingsSec != null) {
            visualMode = settingsSec.getString("visual-mode", "PLAYER_ONLY");

            List<Map<?, ?>> list = settingsSec.getMapList("tiers");
            if (list != null) {
                for (Map<?, ?> map : list) {
                    if (map == null) continue;
                    int levelMin = getInt(map.get("level-min"), 1);
                    int length = getInt(map.get("length"), 1);
                    if (length <= 0) continue;
                    tiers.add(new LengthTier(levelMin, length));
                }
            }
        } else {
            visualMode = "PLAYER_ONLY";
        }

        // Notifications
        ConfigurationSection notifSec = sec.getConfigurationSection("notifications");
        if (notifSec != null) {
            notifEnabled = notifSec.getBoolean("enabled", true);
            notifDisplayMode = notifSec.getString("display-mode", "ACTIONBAR");
            notifMessage = notifSec.getString(
                    "on-proc-message",
                    "&9Line Breaker &7s'est déclenché !"
            );
            notifTitle = notifSec.getString("on-proc-title", null);
            notifSubtitle = notifSec.getString("on-proc-subtitle", null);
        } else {
            notifEnabled = false;
            notifDisplayMode = "CHAT";
            notifMessage = null;
            notifTitle = null;
            notifSubtitle = null;
        }

        plugin.getLogger().info(String.format(
                Locale.US,
                "[PrestigeHoe] [LineBreakerEnchant] enabled=%s, maxLevel=%d, baseChance=%.5f, perLevel=%.5f, maxChance=%.3f, tiers=%d",
                enabled, maxLevel, baseChance, chancePerLevel, maxChance, tiers.size()
        ));
    }

    private int getInt(Object o, int def) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Calcule la longueur de ligne à partir du niveau de l'enchant.
     * On prend le tier le plus haut tel que level >= level-min.
     */
    private int getLengthForLevel(int level) {
        int length = 0;
        for (LengthTier tier : tiers) {
            if (level >= tier.levelMin && tier.length > length) {
                length = tier.length;
            }
        }
        return length;
    }

    /**
     * Tente un proc LineBreaker.
     *
     * @return la longueur de la ligne (>=1) si proc, 0 sinon.
     */
    public int tryGetLineLength(Player player, HoeData hoe) {
        if (!enabled || player == null || hoe == null) {
            return 0;
        }

        int level = hoe.getEnchantLevel(ID, 0);
        if (level <= 0) {
            return 0;
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) {
            return 0;
        }

        // Chance de proc (avec ProcBooster + perks via EnchantProcHelper)
        EnchantProcHelper.ProcData procData = EnchantProcHelper.computeProcData(
                plugin,
                profile,
                hoe,
                ID,
                baseChance,
                chancePerLevel,
                maxChance,
                level
        );

        double chanceToUse = procData.totalCurrent;
        if (chanceToUse <= 0.0D) {
            return 0;
        }

        double roll = ThreadLocalRandom.current().nextDouble(); // [0,1)
        if (roll > chanceToUse) {
            return 0;
        }

        int length = getLengthForLevel(level);
        if (length <= 0) {
            plugin.getLogger().fine("[LineBreakerEnchant] Proc réussi mais aucun tier/length trouvé pour level=" + level);
            return 0;
        }

        // Message de proc (respect des toggles)
        sendProcNotification(player, profile);

        return length;
    }

    /**
     * Affiche un rayon de particules devant le joueur, sur la longueur donnée.
     * Purement visuel, aucune logique de farm ici.
     */
    public void playVisualRay(Player player, Block origin, int length) {
        if (player == null || origin == null || length <= 0) return;

        World world = origin.getWorld();
        if (world == null) return;

        BlockFace face = player.getFacing();
        if (face != BlockFace.NORTH && face != BlockFace.SOUTH
                && face != BlockFace.EAST && face != BlockFace.WEST) {
            face = BlockFace.SOUTH;
        }

        String mode = (visualMode == null ? "PLAYER_ONLY" : visualMode).toUpperCase(Locale.ROOT);

        // Son de "coup" le long de la ligne
        Location start = origin.getLocation().add(0.5, 0.6, 0.5);
        if ("PLAYER_ONLY".equals(mode)) {
            player.playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.4f);
        } else {
            world.playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.4f);
        }

        // Particules sur chaque bloc de la ligne
        for (int i = 1; i <= length; i++) {
            Block b2 = origin.getRelative(face, i);
            Location loc = b2.getLocation().add(0.5, 0.6, 0.5);

            if ("PLAYER_ONLY".equals(mode)) {
                // Visible uniquement pour le joueur
                player.spawnParticle(Particle.CRIT, loc, 8, 0.15, 0.1, 0.15, 0.01);
            } else {
                // Visible pour tout le monde
                world.spawnParticle(Particle.CRIT, loc, 8, 0.15, 0.1, 0.15, 0.01);
            }
        }
    }

    private void sendProcNotification(Player player, PlayerProfile profile) {
        if (!notifEnabled) return;
        if (profile == null) return;

        if (!profile.isEnchantMessagesEnabledGlobal()) return;
        if (!profile.isEnchantProcMessagesEnabledGlobal()) return;
        if (!profile.isEnchantProcMessageEnabled(ID)) return;

        String mode = (notifDisplayMode == null ? "CHAT" : notifDisplayMode).toUpperCase(Locale.ROOT);

        String baseMessage = notifMessage != null
                ? notifMessage.replace("%prefix%", MessageUtil.getPrefix())
                : null;
        String titleText = notifTitle != null
                ? notifTitle.replace("%prefix%", MessageUtil.getPrefix())
                : null;
        String subtitleText = notifSubtitle != null
                ? notifSubtitle.replace("%prefix%", MessageUtil.getPrefix())
                : "";

        switch (mode) {
            case "ACTIONBAR":
                if (baseMessage == null || baseMessage.isEmpty()) return;
                if (!profile.isActionBarNotificationsEnabled()) return;

                player.spigot().sendMessage(
                        net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                                MessageUtil.color(baseMessage)
                        )
                );
                break;

            case "TITLE":
                if (!profile.isTitleNotificationsEnabled()) return;

                String finalTitle = (titleText != null && !titleText.isEmpty())
                        ? titleText
                        : (baseMessage != null ? baseMessage : "");
                String finalSubtitle = (subtitleText != null) ? subtitleText : "";

                if (finalTitle.isEmpty() && finalSubtitle.isEmpty()) return;

                player.sendTitle(
                        MessageUtil.color(finalTitle),
                        MessageUtil.color(finalSubtitle),
                        5,
                        40,
                        5
                );
                break;

            case "CHAT":
            default:
                if (baseMessage == null || baseMessage.isEmpty()) return;
                if (!profile.isChatNotificationsEnabled()) return;
                MessageUtil.sendPlain(player, baseMessage);
                break;
        }
    }
}
