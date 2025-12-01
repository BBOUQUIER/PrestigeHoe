package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MenuSubCommand extends AbstractSubCommand {

    public MenuSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "menu", "prestigehoe.menu", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) return true;

        if (!player.hasPermission("prestigehoe.menu")) {
            MessageUtil.send(player, "errors.no-permission");
            return true;
        }

        plugin.getMenuManager().openMenu(player, "main");
        return true;
    }
}
