package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.ProtocolLibEffectsUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ExplosiveEnchant implements HoeEnchant {

    public static final String ID = "explosive";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    private double baseChance;
    private double chancePerLevel;
    private double maxChance;

    /**
     * "PLAYER_ONLY" -> visuel uniquement pour le joueur (ProtocolLib + fake TNT)
     * "EVERYONE"   -> fallback classique serveur (particules/sons mondiaux)
     */
    private String tntVisualMode; // "PLAYER_ONLY" / "EVERYONE"

    // Notifications
    private boolean notifEnabled;
    private String notifDisplayMode;   // "CHAT" / "ACTIONBAR" / "TITLE"
    private String notifMessage;       // notifications.on-proc-message
    private String notifTitle;         // notifications.on-proc-title
    private String notifSubtitle;      // notifications.on-proc-subtitle

    // Tiers de rayon
    private static class RadiusTier {
        final int levelMin;
        final int radius;

        RadiusTier(int levelMin, int radius) {
            this.levelMin = levelMin;
            this.radius = radius;
        }
    }

    private final List<RadiusTier> tiers = new ArrayList<>();

    public ExplosiveEnchant(PrestigeHoePlugin plugin) {
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
            plugin.getLogger().warning("[ExplosiveEnchant] Section enchants." + ID + " introuvable");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            baseChance = 0.0D;
            chancePerLevel = 0.0D;
            maxChance = 1.0D;
            tntVisualMode = "PLAYER_ONLY";
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

        // Settings (tiers + mode visuel)
        tiers.clear();
        ConfigurationSection settingsSec = sec.getConfigurationSection("settings");
        if (settingsSec != null) {
            tntVisualMode = settingsSec.getString("tnt-visual-mode", "PLAYER_ONLY");

            List<Map<?, ?>> list = settingsSec.getMapList("tiers");
            if (list != null) {
                for (Map<?, ?> map : list) {
                    if (map == null) continue;
                    int levelMin = getInt(map.get("level-min"), 1);
                    int radius = getInt(map.get("radius"), 1);
                    if (radius <= 0) continue;
                    tiers.add(new RadiusTier(levelMin, radius));
                }
            }
        } else {
            tntVisualMode = "PLAYER_ONLY";
        }

        // Notifications
        ConfigurationSection notifSec = sec.getConfigurationSection("notifications");
        if (notifSec != null) {
            notifEnabled = notifSec.getBoolean("enabled", true);
            notifDisplayMode = notifSec.getString("display-mode", "ACTIONBAR");
            notifMessage = notifSec.getString(
                    "on-proc-message",
                    "&cExplosive &7s'est déclenché !"
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
                "[PrestigeHoe] [ExplosiveEnchant] enabled=%s, maxLevel=%d, baseChance=%.5f, perLevel=%.5f, maxChance=%.3f, tiers=%d",
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
     * Calcule le radius à partir du niveau de l'enchant, en prenant
     * le tier le plus haut tel que level >= level-min.
     */
    private int getRadiusForLevel(int level) {
        int radius = 0;
        for (RadiusTier tier : tiers) {
            if (level >= tier.levelMin && tier.radius > radius) {
                radius = tier.radius;
            }
        }
        return radius;
    }

    /**
     * Tente un proc Explosive.
     *
     * @return le radius à utiliser si proc (>=1), 0 si aucun proc.
     */
    public int tryExplode(Player player, HoeData hoe, Block origin) {
        if (!enabled || player == null || hoe == null || origin == null) {
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

        int radius = getRadiusForLevel(level);
        if (radius <= 0) {
            plugin.getLogger().fine("[ExplosiveEnchant] Proc réussi mais aucun tier/radius trouvé pour level=" + level);
            return 0;
        }

        // Effet TNT visuel (client-side / fallback)
        playVisualEffect(player, origin.getLocation().add(0.5, 0.5, 0.5));

        // Message de proc (respect des toggles)
        sendProcNotification(player, profile);

        return radius;
    }

    /**
     * Gère le visuel TNT :
     * - PLAYER_ONLY + ProtocolLib dispo -> fake TNT côté client uniquement
     * - sinon -> particule/sound serveur classique
     */
    private void playVisualEffect(Player player, Location loc) {
        if (loc == null) return;
        World world = loc.getWorld();
        if (world == null) return;

        String mode = (tntVisualMode == null ? "PLAYER_ONLY" : tntVisualMode).toUpperCase(Locale.ROOT);

        // 🎯 Mode PLAYER_ONLY : TNT & explosion full client-side via ProtocolLibEffectsUtil
        if ("PLAYER_ONLY".equals(mode)) {
            ProtocolLibEffectsUtil.playClientSideTntExplosion(plugin, player, loc, 20L);
            return;
        }

        // 🔁 Fallback EVERYONE (ou si ProtocolLib non dispo -> géré dans util)
        world.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
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
