package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SetEnchantSubCommand extends AbstractSubCommand {

    public SetEnchantSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "setenchant", "prestigehoe.admin.setenchant", true);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) return true;

        if (args.length < 2) {
            sendConfigMessage(player,
                    "admin.usage.setenchant",
                    "%prefix%&cUsage: /" + label + " setenchant <id> <level>");
            return true;
        }

        String enchantId = args[0].toLowerCase(Locale.ROOT);

        FileConfiguration enchCfg = plugin.getConfigManager().getEnchantsConfig();
        if (!enchCfg.isConfigurationSection("enchants." + enchantId)) {
            FileConfiguration messages = plugin.getConfigManager().getMessagesConfig();
            String raw = messages.getString("errors.invalid-enchant",
                    "%prefix%&cEnchantement inconnu: &e%enchant_id%");
            raw = raw
                    .replace("%prefix%", MessageUtil.getPrefix())
                    .replace("%enchant_id%", enchantId);
            MessageUtil.sendPlain(player, raw);
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException ex) {
            FileConfiguration messages = plugin.getConfigManager().getMessagesConfig();
            String raw = messages.getString("errors.invalid-level",
                    "%prefix%&cLevel invalide: &e%value%");
            raw = raw
                    .replace("%prefix%", MessageUtil.getPrefix())
                    .replace("%value%", args[1]);
            MessageUtil.sendPlain(player, raw);
            return true;
        }

        if (level < 0) level = 0;

        int maxLevel = enchCfg.getInt("enchants." + enchantId + ".level.max-level", 100);
        if (level > maxLevel) {
            level = maxLevel;
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&eLevel trop élevé, clamp au niveau max &6" + maxLevel + "&e.");
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null || itemInHand.getType() == Material.AIR
                || !plugin.getHoeItemManager().isPrestigeHoe(itemInHand)) {
            MessageUtil.send(player, "errors.not-holding-hoe");
            return true;
        }

        if (!plugin.getHoeItemManager().isOwnedBy(itemInHand, player)) {
            MessageUtil.send(player, "errors.not-owner");
            return true;
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        HoeData hoeData = profile.getHoeData();
        if (hoeData == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cDonnées de houe introuvables pour ce joueur.");
            return true;
        }

        hoeData.setEnchantLevel(enchantId, level);
        plugin.getFarmService().updateHoeDisplay(player, hoeData);

        MessageUtil.sendPlain(player,
                MessageUtil.getPrefix() + "&aEnchant &e" + enchantId
                        + " &amis au niveau &e" + level + "&a sur ta houe.");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // args[0] = enchantId
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();

            FileConfiguration enchCfg = plugin.getConfigManager().getEnchantsConfig();
            ConfigurationSection enchSec = enchCfg.getConfigurationSection("enchants");
            if (enchSec == null) {
                return Collections.emptyList();
            }

            for (String id : enchSec.getKeys(false)) {
                if (id.toLowerCase(Locale.ROOT).startsWith(current)) {
                    result.add(id);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }
}
