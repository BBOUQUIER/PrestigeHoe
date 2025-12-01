package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GiveSubCommand extends AbstractSubCommand {

    public GiveSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "give", "prestigehoe.admin.give", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sendConfigMessage(sender,
                    "admin.usage.give",
                    "%prefix%&cUsage: /" + label + " give <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            MessageUtil.send(sender, "errors.invalid-target");
            return true;
        }

        plugin.getHoeItemManager().giveNewHoe(target);

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(target);
        HoeData hoeData = profile.getHoeData();
        if (hoeData != null) {
            plugin.getEnchantManager().applyDefaultEnchants(hoeData);
            plugin.getFarmService().updateHoeDisplay(target, hoeData);
        }

        MessageUtil.sendPlain(sender,
                MessageUtil.getPrefix() + "&aPrestigeHoe donnée à &e" + target.getName());
        return true;
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // args[0] = nom du joueur
        if (args.length == 1) {
            return tabCompleteOnlinePlayers(args[0]);
        }
        return java.util.Collections.emptyList();
    }
}
