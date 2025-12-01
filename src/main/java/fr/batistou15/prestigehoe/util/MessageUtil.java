package fr.batistou15.prestigehoe.util;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class MessageUtil {

    private static FileConfiguration messages;
    private static String prefix = "§6[PrestigeHoe]§r ";

    private static void ensureLoaded() {
        if (messages == null) {
            PrestigeHoePlugin plugin = PrestigeHoePlugin.getInstance();
            if (plugin == null) return;

            ConfigManager configManager = plugin.getConfigManager();
            if (configManager == null) return;

            messages = configManager.getMessagesConfig();
            if (messages != null) {
                String rawPrefix = messages.getString("prefix", prefix);
                prefix = color(rawPrefix);
            }
        }
    }

    public static String getPrefix() {
        ensureLoaded();
        return prefix;
    }

    /** Récupère un message depuis messages.yml (avec %prefix%). */
    public static String get(String path) {
        ensureLoaded();
        if (messages == null) {
            return prefix + "§cMessage introuvable: " + path;
        }

        String raw = messages.getString(path);
        if (raw == null) {
            return prefix + "§cMessage introuvable: " + path;
        }
        raw = raw.replace("%prefix%", prefix);
        return color(raw);
    }

    /** Envoie un message depuis messages.yml (clé). */
    public static void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    /**
     * Envoie une ligne "brute" passée en paramètre (sans aller dans messages.yml),
     * mais avec support de & et du placeholder %prefix%.
     *
     * Exemple :
     *   MessageUtil.sendPlain(sender, "%prefix%&cUsage: /prestigehoe give <joueur>");
     */
    public static void sendPlain(CommandSender sender, String raw) {
        ensureLoaded();
        if (raw == null) return;
        raw = raw.replace("%prefix%", prefix);
        sender.sendMessage(color(raw));
    }

    public static String color(String msg) {
        if (msg == null) return null;
        return msg.replace("&", "§");
    }
}
