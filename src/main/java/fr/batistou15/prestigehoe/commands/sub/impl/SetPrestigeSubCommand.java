package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SetPrestigeSubCommand extends AbstractSubCommand {

    public SetPrestigeSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "setprestige", "prestigehoe.admin.setprestige", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendConfigMessage(
                    sender,
                    "admin.usage.setprestige",
                    "%prefix%&cUsage: /" + label + " setprestige <joueur> <valeur>"
            );
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.send(sender, "errors.invalid-target");
            return true;
        }

        int prestigeValue;
        try {
            prestigeValue = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            FileConfiguration messages = plugin.getConfigManager().getMessagesConfig();
            String raw = messages.getString(
                    "errors.invalid-level",
                    "%prefix%&cValeur de prestige invalide: &e%value%"
            );
            raw = raw
                    .replace("%prefix%", MessageUtil.getPrefix())
                    .replace("%value%", args[1]);
            MessageUtil.sendPlain(sender, raw);
            return true;
        }

        if (prestigeValue < 0) prestigeValue = 0;

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(target);
        HoeData hoeData = profile.getHoeData();
        if (hoeData == null) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cDonnées de houe introuvables pour ce joueur.");
            return true;
        }

        hoeData.setPrestige(prestigeValue);
        plugin.getFarmService().updateHoeDisplay(target, hoeData);
        plugin.getNotificationService().notifyPrestigeChange(target, hoeData);

        FileConfiguration messages = plugin.getConfigManager().getMessagesConfig();
        String raw = messages.getString(
                "admin.setprestige-success",
                "%prefix%&aPrestige de la houe de &e%player% &amis à &d%value%&a."
        );
        raw = raw
                .replace("%prefix%", MessageUtil.getPrefix())
                .replace("%player%", target.getName())
                .replace("%value%", String.valueOf(prestigeValue));
        MessageUtil.sendPlain(sender, raw);

        if (!sender.equals(target)) {
            String rawPlayer = messages.getString(
                    "admin.setprestige-notify",
                    "%prefix%&eTon prestige de houe a été défini à &d%value% &epar un administrateur."
            );
            rawPlayer = rawPlayer
                    .replace("%prefix%", MessageUtil.getPrefix())
                    .replace("%value%", String.valueOf(prestigeValue));
            MessageUtil.sendPlain(target, rawPlayer);
        }

        return true;
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // args[0] = joueur
        if (args.length == 1) {
            return tabCompleteOnlinePlayers(args[0]);
        }
        return java.util.Collections.emptyList();
    }
}
