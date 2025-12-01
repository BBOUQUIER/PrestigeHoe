package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.command.CommandSender;

public class ReloadSubCommand extends AbstractSubCommand {

    public ReloadSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "reload", "prestigehoe.admin.reload", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        plugin.reloadPluginConfigs();
        MessageUtil.send(sender, "admin.reload-success");
        return true;
    }
}
