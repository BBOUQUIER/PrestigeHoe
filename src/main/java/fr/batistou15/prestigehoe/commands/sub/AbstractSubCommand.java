package fr.batistou15.prestigehoe.commands.sub;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public abstract class AbstractSubCommand implements SubCommand {

    protected final PrestigeHoePlugin plugin;
    private final String name;
    private final String permission;
    private final boolean playerOnly;
    private final String description;
    private final List<String> aliases;

    // ==== CONSTRUCTEURS COMPAT ====

    // Ancien style : super(plugin, "debug", "perm", true);
    protected AbstractSubCommand(PrestigeHoePlugin plugin,
                                 String name,
                                 String permission,
                                 boolean playerOnly) {
        this(plugin, name, permission, playerOnly, "", Collections.emptyList());
    }

    // Nouveau style : super(plugin, "debug", "perm", true, "Description");
    protected AbstractSubCommand(PrestigeHoePlugin plugin,
                                 String name,
                                 String permission,
                                 boolean playerOnly,
                                 String description) {
        this(plugin, name, permission, playerOnly, description, Collections.emptyList());
    }

    // Nouveau style + aliases
    protected AbstractSubCommand(PrestigeHoePlugin plugin,
                                 String name,
                                 String permission,
                                 boolean playerOnly,
                                 String description,
                                 List<String> aliases) {
        this.plugin = plugin;
        this.name = name;
        this.permission = permission;
        this.playerOnly = playerOnly;
        this.description = (description == null) ? "" : description;
        this.aliases = (aliases == null) ? Collections.emptyList() : aliases;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<String> getAliases() {
        return aliases;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        // Permission
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            MessageUtil.send(sender, "errors.no-permission");
            return true;
        }

        // Joueur uniquement ?
        if (playerOnly && !(sender instanceof Player)) {
            MessageUtil.send(sender, "errors.not-a-player");
            return true;
        }

        return executeCommand(sender, label, args);
    }

    /**
     * Exécution réelle de la sous-commande (après vérifs permission + playerOnly).
     */
    protected abstract boolean executeCommand(CommandSender sender, String label, String[] args);

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return Collections.emptyList();
    }

    // =======================
    //     HELPERS COMMUNS
    // =======================

    protected Player asPlayer(CommandSender sender) {
        return (sender instanceof Player) ? (Player) sender : null;
    }

    protected void sendConfigMessage(CommandSender sender, String path, String defaultMsg) {
        sendConfigMessage(sender, path, defaultMsg, null);
    }

    protected void sendConfigMessage(CommandSender sender,
                                     String path,
                                     String defaultMsg,
                                     Map<String, String> placeholders) {
        FileConfiguration messages = plugin.getConfigManager().getMessagesConfig();
        String raw = messages.getString(path, defaultMsg);
        if (raw == null || raw.isEmpty()) {
            raw = defaultMsg;
        }

        raw = raw.replace("%prefix%", MessageUtil.getPrefix());

        if (placeholders != null && !placeholders.isEmpty()) {
            raw = applyPlaceholders(raw, placeholders);
        }

        MessageUtil.sendPlain(sender, MessageUtil.color(raw));
    }

    // 👉 version commune pour toutes les sous-commandes
    protected String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || placeholders == null || placeholders.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    protected List<String> tabCompleteOnlinePlayers(String prefix) {
        String current = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                result.add(p.getName());
            }
        }
        return result;
    }
}
