package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

public class SetOwnerSubCommand extends AbstractSubCommand {

    public SetOwnerSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "setowner", "prestigehoe.admin.setowner", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        Player admin = asPlayer(sender);
        if (admin == null) return true;

        if (args.length < 1) {
            sendConfigMessage(
                    admin,
                    "admin.usage.setowner",
                    "%prefix%&cUsage: /" + label + " setowner <joueur>"
            );
            return true;
        }

        ItemStack item = admin.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR
                || !plugin.getHoeItemManager().isPrestigeHoe(item)) {
            MessageUtil.send(admin, "errors.not-holding-hoe");
            return true;
        }

        String newOwnerName = args[0];
        OfflinePlayer newOwner = Bukkit.getOfflinePlayer(newOwnerName);

        plugin.getHoeItemManager().setOwner(item, newOwner.getUniqueId(), newOwnerName);

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(admin);
        HoeData hoeData = profile.getHoeData();
        plugin.getFarmService().updateHoeDisplay(admin, hoeData);

        FileConfiguration messages = plugin.getConfigManager().getMessagesConfig();
        String raw = messages.getString(
                "admin.setowner-success",
                "%prefix%&aPropriétaire de la houe défini sur &e%new_owner%&a."
        );
        raw = raw
                .replace("%prefix%", MessageUtil.getPrefix())
                .replace("%new_owner%", newOwnerName);
        MessageUtil.sendPlain(admin, raw);

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
