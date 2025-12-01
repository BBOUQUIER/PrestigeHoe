package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class InfoSubCommand extends AbstractSubCommand {

    public InfoSubCommand(PrestigeHoePlugin plugin) {
        super(plugin, "info", "prestigehoe.admin.info", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {

        Player target;

        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                MessageUtil.send(sender, "errors.invalid-target");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sendConfigMessage(sender,
                        "admin.usage.info",
                        "%prefix%&cUsage: /" + label + " info <joueur>");
                return true;
            }
            target = (Player) sender;
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(target);
        HoeData hoe = profile.getHoeData();
        if (hoe == null) {
            MessageUtil.send(sender, "errors.no-hoe-data");
            return true;
        }

        int level = hoe.getLevel();
        double xp = hoe.getXp();
        int prestige = hoe.getPrestige();

        double essenceBalance = profile.getEssence();
        long prestigeTokens = profile.getPrestigeTokens();

        long totalCrops = profile.getTotalCropsBroken();
        double totalMoney = profile.getTotalMoneyEarned();
        double totalEssence = profile.getTotalEssenceEarned();

        String enchantsFormatted = buildEnchantsInfo(hoe);

        FileConfiguration messages = plugin.getConfigManager().getMessagesConfig();
        List<String> lines = messages.getStringList("admin.info.lines");
        if (lines == null || lines.isEmpty()) {
            lines = getDefaultInfoLines();
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("%prefix%", MessageUtil.getPrefix());
        ph.put("%player%", target.getName());
        ph.put("%hoe_level%", String.valueOf(level));
        ph.put("%hoe_prestige%", String.valueOf(prestige));
        ph.put("%hoe_xp%", NumberFormatUtil.formatShort(xp));

        ph.put("%essence%", NumberFormatUtil.formatShort(essenceBalance));
        ph.put("%prestige_tokens%", NumberFormatUtil.formatShort(prestigeTokens));

        ph.put("%crops_broken%", NumberFormatUtil.formatShort(totalCrops));
        ph.put("%money_earned%", NumberFormatUtil.formatShort(totalMoney));
        ph.put("%essence_earned%", NumberFormatUtil.formatShort(totalEssence));

        ph.put("%enchants%", enchantsFormatted);

        for (String rawLine : lines) {
            if (rawLine == null || rawLine.isEmpty()) continue;
            String line = applyPlaceholders(rawLine, ph);
            MessageUtil.sendPlain(sender, MessageUtil.color(line));
        }

        return true;
    }

    protected String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String buildEnchantsInfo(HoeData hoe) {
        StringBuilder enchantsInfo = new StringBuilder();

        FileConfiguration enchCfg = plugin.getConfigManager().getEnchantsConfig();
        ConfigurationSection enchSec = enchCfg.getConfigurationSection("enchants");
        if (enchSec != null) {
            for (String id : enchSec.getKeys(false)) {
                int lvl = hoe.getEnchantLevel(id, 0);
                if (lvl <= 0) continue;

                String displayName = enchCfg.getString("enchants." + id + ".display-name", id);

                if (enchantsInfo.length() > 0) enchantsInfo.append("§7, ");
                enchantsInfo
                        .append("§e")
                        .append(displayName)
                        .append("§7: §f")
                        .append(lvl);
            }
        }

        if (enchantsInfo.length() == 0) {
            enchantsInfo.append("§7Aucun enchant ou tous à 0.");
        }
        return enchantsInfo.toString();
    }

    private List<String> getDefaultInfoLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&8&m----------------------------------------");
        lines.add("%prefix%&eInfos PrestigeHoe pour &a%player%&e :");
        lines.add("&7▪ &fNiveau houe : &e%hoe_level%");
        lines.add("&7▪ &fPrestige houe : &d%hoe_prestige%");
        lines.add("&7▪ &fXP houe : &e%hoe_xp%");
        lines.add("&7▪ &fEssence actuelle : &b%essence%");
        lines.add("&7▪ &fPrestige Tokens : &d%prestige_tokens%");
        lines.add("&7▪ &fCrops cassées (total) : &e%crops_broken%");
        lines.add("&7▪ &fArgent généré (total) : &a$%money_earned%");
        lines.add("&7▪ &fEssence gagnée (total) : &b%essence_earned%");
        lines.add("&7▪ &fEnchants : %enchants%");
        lines.add("&8&m----------------------------------------");
        return lines;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // args[0] = joueur
        if (args.length == 1) {
            return tabCompleteOnlinePlayers(args[0]);
        }
        return Collections.emptyList();
    }
}
