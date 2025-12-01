package fr.batistou15.prestigehoe.notification;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NotificationService {

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;

    public NotificationService(PrestigeHoePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    // =========================================================
    //                     LEVEL-UP HOE (global)
    // =========================================================
    public void notifyLevelUp(Player player, HoeData hoe) {
        if (player == null || hoe == null) return;

        NotificationType type = getType("notifications.level-up.type", NotificationType.CHAT);
        if (type == NotificationType.NONE) return;

        FileConfiguration messages = configManager.getMessagesConfig();
        FileConfiguration mainCfg = configManager.getMainConfig();

        Map<String, String> ph = basePlaceholders(player, hoe);

        switch (type) {
            case CHAT -> {
                String raw = messages.getString(
                        "hoe.level-up.chat",
                        messages.getString("hoe.level-up.message",
                                "%prefix%&aTa houe est passée niveau &e%level%&a !")
                );
                sendChat(player, raw, ph);
            }
            case ACTIONBAR -> {
                String raw = messages.getString(
                        "hoe.level-up.actionbar",
                        messages.getString("hoe.level-up.chat",
                                "%prefix%&aTa houe est passée niveau &e%level%&a !")
                );
                sendActionbar(player, raw, ph);
            }
            case TITLE -> {
                String title = messages.getString("hoe.level-up.title.title", "&aLEVEL UP !");
                String subtitle = messages.getString("hoe.level-up.title.subtitle", "&fTa houe est maintenant &e%level%");

                int fadeIn = mainCfg.getInt("notifications.level-up.title.fade-in", 10);
                int stay = mainCfg.getInt("notifications.level-up.title.stay", 40);
                int fadeOut = mainCfg.getInt("notifications.level-up.title.fade-out", 10);

                sendTitle(player, title, subtitle, ph, fadeIn, stay, fadeOut);
            }
            case BOSSBAR -> {
                String raw = messages.getString(
                        "hoe.level-up.bossbar",
                        messages.getString("hoe.level-up.chat",
                                "%prefix%&aTa houe est passée niveau &e%level%&a !")
                );

                String cfgPath = "notifications.level-up.bossbar";
                sendBossbarFromConfig(player, raw, ph, cfgPath);
            }
            default -> {
                // NONE
            }
        }
    }

    // =========================================================
    //                  PRESTIGE HOE (global)
    // =========================================================
    public void notifyPrestigeChange(Player player, HoeData hoe) {
        if (player == null || hoe == null) return;

        NotificationType type = getType("notifications.prestige.type", NotificationType.CHAT);
        if (type == NotificationType.NONE) return;

        FileConfiguration messages = configManager.getMessagesConfig();
        FileConfiguration mainCfg = configManager.getMainConfig();

        Map<String, String> ph = basePlaceholders(player, hoe);

        switch (type) {
            case CHAT -> {
                String raw = messages.getString(
                        "hoe.prestige-up.chat",
                        "%prefix%&dTa houe est passée prestige &5%prestige%&d !"
                );
                sendChat(player, raw, ph);
            }
            case ACTIONBAR -> {
                String raw = messages.getString(
                        "hoe.prestige-up.actionbar",
                        "&dPrestige &5%prestige%&d atteint !"
                );
                sendActionbar(player, raw, ph);
            }
            case TITLE -> {
                String title = messages.getString("hoe.prestige-up.title.title", "&dPRESTIGE &5%prestige%");
                String subtitle = messages.getString("hoe.prestige-up.title.subtitle", "&fGG pour ta houe !");

                int fadeIn = mainCfg.getInt("notifications.prestige.title.fade-in", 10);
                int stay = mainCfg.getInt("notifications.prestige.title.stay", 60);
                int fadeOut = mainCfg.getInt("notifications.prestige.title.fade-out", 10);

                sendTitle(player, title, subtitle, ph, fadeIn, stay, fadeOut);
            }
            case BOSSBAR -> {
                String raw = messages.getString(
                        "hoe.prestige-up.bossbar",
                        "%prefix%&dTa houe a atteint le prestige &5%prestige%&d !"
                );

                String cfgPath = "notifications.prestige.bossbar";
                sendBossbarFromConfig(player, raw, ph, cfgPath);
            }
            default -> {
                // NONE
            }
        }
    }

    // =========================================================
    //          ENCHANT UPGRADE (par ENCHANT, enchants.yml)
    // =========================================================
    public void notifyEnchantUpgrade(Player player,
                                     HoeData hoe,
                                     String enchantId,
                                     String enchantDisplayName,
                                     int newLevel) {
        if (player == null || hoe == null || enchantId == null) return;

        FileConfiguration enchCfg = configManager.getEnchantsConfig();
        String basePath = "enchants." + enchantId + ".upgrade-notification";

        Map<String, String> ph = basePlaceholders(player, hoe);

        String name = (enchantDisplayName != null && !enchantDisplayName.isEmpty())
                ? enchantDisplayName
                : enchantId;

        ph.put("%enchant_id%", enchantId);
        ph.put("%enchant_name%", name);
        ph.put("%new_level%", String.valueOf(newLevel));

        // Si aucune section définie -> fallback simple en CHAT
        if (!enchCfg.isConfigurationSection(basePath)) {
            String raw = "%prefix%&aTu as amélioré &e%enchant_name% &aau niveau &e%new_level%&a !";
            sendChat(player, raw, ph);
            return;
        }

        boolean enabled = enchCfg.getBoolean(basePath + ".enabled", true);
        if (!enabled) return;

        String typeRaw = enchCfg.getString(basePath + ".type", "CHAT");
        NotificationType type;
        try {
            type = NotificationType.valueOf(typeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            type = NotificationType.CHAT;
        }
        if (type == NotificationType.NONE) return;

        switch (type) {
            case CHAT -> {
                String raw = enchCfg.getString(
                        basePath + ".chat",
                        "%prefix%&aTu as amélioré &e%enchant_name% &aau niveau &e%new_level%&a !"
                );
                sendChat(player, raw, ph);
            }
            case ACTIONBAR -> {
                String raw = enchCfg.getString(
                        basePath + ".actionbar",
                        enchCfg.getString(
                                basePath + ".chat",
                                "%prefix%&aTu as amélioré &e%enchant_name% &aau niveau &e%new_level%&a !"
                        )
                );
                sendActionbar(player, raw, ph);
            }
            case TITLE -> {
                String title = enchCfg.getString(
                        basePath + ".title.title",
                        "&aENCHANT UPGRADE"
                );
                String subtitle = enchCfg.getString(
                        basePath + ".title.subtitle",
                        "&e%enchant_name% &a→ &e%new_level%"
                );

                int fadeIn = enchCfg.getInt(basePath + ".title.fade-in", 10);
                int stay = enchCfg.getInt(basePath + ".title.stay", 40);
                int fadeOut = enchCfg.getInt(basePath + ".title.fade-out", 10);

                sendTitle(player, title, subtitle, ph, fadeIn, stay, fadeOut);
            }
            case BOSSBAR -> {
                String raw = enchCfg.getString(
                        basePath + ".bossbar.text",
                        enchCfg.getString(
                                basePath + ".chat",
                                "%prefix%&aTu as amélioré &e%enchant_name% &aau niveau &e%new_level%&a !"
                        )
                );

                String colorRaw = enchCfg.getString(basePath + ".bossbar.color", "YELLOW");
                String styleRaw = enchCfg.getString(basePath + ".bossbar.style", "SOLID");
                int duration = enchCfg.getInt(basePath + ".bossbar.duration-ticks", 60);

                sendBossbarCustom(player, raw, ph, colorRaw, styleRaw, duration);
            }
            default -> {
                // NONE
            }
        }
    }

    // =========================================================
    //                       HELPERS GLOBAUX
    // =========================================================

    private NotificationType getType(String path, NotificationType def) {
        FileConfiguration cfg = configManager.getMainConfig();
        String raw = cfg.getString(path, null);
        if (raw == null || raw.isEmpty()) return def;
        try {
            return NotificationType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }

    private Map<String, String> basePlaceholders(Player player, HoeData hoe) {
        Map<String, String> ph = new HashMap<>();

        int level = hoe.getLevel();
        int prestige = hoe.getPrestige();

        ph.put("%prefix%", MessageUtil.getPrefix());
        ph.put("%player%", player.getName());

        ph.put("%level%", String.valueOf(level));
        ph.put("%prestigehoe_level%", String.valueOf(level));

        ph.put("%prestige%", String.valueOf(prestige));
        ph.put("%prestigehoe_prestige%", String.valueOf(prestige));

        return ph;
    }

    private String applyPlaceholders(String input, Map<String, String> ph) {
        if (input == null) return "";
        String result = input;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private void sendChat(Player player, String rawMsg, Map<String, String> ph) {
        if (rawMsg == null || rawMsg.isEmpty()) return;
        String msg = applyPlaceholders(rawMsg, ph);
        MessageUtil.sendPlain(player, MessageUtil.color(msg));
    }

    private void sendActionbar(Player player, String rawMsg, Map<String, String> ph) {
        if (rawMsg == null || rawMsg.isEmpty()) return;
        String msg = applyPlaceholders(rawMsg, ph);
        msg = MessageUtil.color(msg);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    private void sendTitle(Player player,
                           String rawTitle,
                           String rawSubtitle,
                           Map<String, String> ph,
                           int fadeIn,
                           int stay,
                           int fadeOut) {
        String title = MessageUtil.color(applyPlaceholders(rawTitle, ph));
        String subtitle = MessageUtil.color(applyPlaceholders(rawSubtitle, ph));
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    /**
     * Bossbar basée sur config.yml (level-up / prestige)
     */
    private void sendBossbarFromConfig(Player player,
                                       String rawMsg,
                                       Map<String, String> ph,
                                       String cfgPath) {
        if (rawMsg == null || rawMsg.isEmpty()) return;

        FileConfiguration cfg = configManager.getMainConfig();

        String colorRaw = cfg.getString(cfgPath + ".color", "GREEN");
        String styleRaw = cfg.getString(cfgPath + ".style", "SOLID");
        int duration = cfg.getInt(cfgPath + ".duration-ticks", 60);

        sendBossbarCustom(player, rawMsg, ph, colorRaw, styleRaw, duration);
    }

    /**
     * Bossbar avec paramètres passés directement (pour enchants.yml)
     */
    private void sendBossbarCustom(Player player,
                                   String rawMsg,
                                   Map<String, String> ph,
                                   String colorRaw,
                                   String styleRaw,
                                   int duration) {
        if (rawMsg == null || rawMsg.isEmpty()) return;

        BarColor color;
        try {
            color = BarColor.valueOf(colorRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            color = BarColor.GREEN;
        }

        BarStyle style;
        try {
            style = BarStyle.valueOf(styleRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            style = BarStyle.SOLID;
        }

        String msg = MessageUtil.color(applyPlaceholders(rawMsg, ph));

        BossBar bar = Bukkit.createBossBar(msg, color, style);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        bar.setVisible(true);

        new BukkitRunnable() {
            @Override
            public void run() {
                bar.removePlayer(player);
                bar.setVisible(false);
            }
        }.runTaskLater(plugin, duration);
    }
}
