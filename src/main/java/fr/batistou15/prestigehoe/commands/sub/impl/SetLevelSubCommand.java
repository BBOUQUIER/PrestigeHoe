package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class SetLevelSubCommand extends AbstractSubCommand {

    public SetLevelSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "setlevel", "prestigehoe.admin.setlevel", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {

        if (args.length < 2) {
            sendConfigMessage(sender,
                    "admin.usage.setlevel",
                    "%prefix%&cUsage: /" + label + " setlevel <joueur> <niveau>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(sender, "errors.invalid-target");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cNiveau invalide: &e" + args[1]);
            return true;
        }

        if (level < 1) level = 1;

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(target);
        HoeData hoeData = profile.getHoeData();
        if (hoeData == null) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cDonnées de houe introuvables pour ce joueur.");
            return true;
        }

        int maxLevel = plugin.getFarmService().getMaxHoeLevel(hoeData);
        if (level > maxLevel) {
            level = maxLevel;
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&eNiveau trop élevé, clamp au niveau max &6" + maxLevel + "&e.");
        }

        hoeData.setLevel(level);
        hoeData.setXp(0.0);

        plugin.getFarmService().updateHoeDisplay(target, hoeData);

        MessageUtil.sendPlain(sender,
                MessageUtil.getPrefix() + "&aNiveau de la houe de &e" + target.getName()
                        + " &amis à &e" + level + "&a.");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1) {
            return tabCompleteOnlinePlayers(args[0]);
        }
        return Collections.emptyList();
    }
}
