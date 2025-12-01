package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DebugSubCommand extends AbstractSubCommand {

    public DebugSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "debug", "prestigehoe.admin.debug", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) return true;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR
                || !plugin.getHoeItemManager().isPrestigeHoe(item)) {
            MessageUtil.send(player, "errors.not-holding-hoe");
            return true;
        }

        if (!plugin.getHoeItemManager().isOwnedBy(item, player)) {
            MessageUtil.send(player, "errors.not-owner");
            return true;
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        HoeData hoeData = profile.getHoeData();

        player.sendMessage("§6[PrestigeHoe] §eDEBUG:");
        player.sendMessage(" §7Level: §f" + hoeData.getLevel());
        player.sendMessage(" §7XP: §f" + NumberFormatUtil.formatShort(hoeData.getXp()));
        player.sendMessage(" §7Prestige: §f" + hoeData.getPrestige());
        player.sendMessage(" §7Enchant levels: §f" + hoeData.getEnchantLevels().toString());
        player.sendMessage(" §7Total crops cassées: §f" + NumberFormatUtil.formatShort(profile.getTotalCropsBroken()));
        player.sendMessage(" §7Total argent: §f" + NumberFormatUtil.formatShort(profile.getTotalMoneyEarned()));
        player.sendMessage(" §7Total essence: §f" + NumberFormatUtil.formatShort(profile.getTotalEssenceEarned()));
        return true;
    }
}
