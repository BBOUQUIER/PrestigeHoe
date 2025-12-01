package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class PerkSubCommand extends AbstractSubCommand {

    public PerkSubCommand(PrestigeHoePlugin plugin) {
        super(
                plugin,
                "perk",
                "prestigehoe.admin.perk",
                true,
                "Gérer les perks de prestige (reset / set / add / remove)."
        );
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) return true;

        if (args.length < 1) {
            sendUsage(sender, label);
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        switch (action) {
            case "reset" -> handleResetSelf(player, label, args);
            case "set", "add", "remove" -> handleAdminModify(sender, label, action, args);
            default -> sendUsage(sender, label);
        }

        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        MessageUtil.sendPlain(sender,
                MessageUtil.getPrefix() + "&cUsage:");
        MessageUtil.sendPlain(sender,
                " &e/" + label + " perk reset <perkId> &7- Reset ton propre perk.");
        MessageUtil.sendPlain(sender,
                " &e/" + label + " perk set <joueur> <perkId> <niveau> &7- Définit le niveau d'un perk.");
        MessageUtil.sendPlain(sender,
                " &e/" + label + " perk add <joueur> <perkId> <delta> &7- Ajoute des niveaux.");
        MessageUtil.sendPlain(sender,
                " &e/" + label + " perk remove <joueur> <perkId> <delta> &7- Retire des niveaux.");
    }

    private void handleResetSelf(Player player, String label, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cUsage: &e/" + label + " perk reset <perkId>");
            return;
        }

        String perkId = args[1].toLowerCase(Locale.ROOT);

        if (plugin.getFarmService() == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cLe système de farm n'est pas initialisé.");
            return;
        }

        plugin.getFarmService().attemptResetPrestigePerk(player, perkId);
    }

    private void handleAdminModify(CommandSender sender, String label, String action, String[] args) {
        // /prestigehoe perk <set|add|remove> <joueur> <perkId> <valeur>
        if (args.length < 4) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cUsage: &e/" + label + " perk "
                            + action + " <joueur> <perkId> <valeur>");
            return;
        }

        String targetName = args[1];
        String perkId = args[2].toLowerCase(Locale.ROOT);
        String valueStr = args[3];

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.send(sender, "errors.invalid-target");
            return;
        }

        int value;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException ex) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cValeur invalide: &e" + valueStr);
            return;
        }

        if (value < 0) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cLa valeur doit être positive.");
            return;
        }

        FileConfiguration shopCfg = plugin.getConfigManager().getPrestigeShopConfig();
        ConfigurationSection root = shopCfg.getConfigurationSection("prestige_shop.perks");
        if (root == null) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cLa boutique de prestige n'est pas configurée.");
            return;
        }

        ConfigurationSection sec = root.getConfigurationSection(perkId);
        if (sec == null || !sec.getBoolean("enabled", true)) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cPerk inconnu ou désactivé: &e" + perkId);
            return;
        }

        int maxLevel = sec.getInt("max-level", 1);

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(target);
        if (profile == null) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cProfil introuvable pour ce joueur.");
            return;
        }

        int currentLevel = profile.getPrestigePerkLevel(perkId);
        int newLevel = currentLevel;

        switch (action) {
            case "set" -> newLevel = value;
            case "add" -> newLevel = currentLevel + value;
            case "remove" -> newLevel = currentLevel - value;
        }

        if (newLevel < 0) newLevel = 0;
        if (maxLevel > 0 && newLevel > maxLevel) newLevel = maxLevel;

        profile.setPrestigePerkLevel(perkId, newLevel);

        String displayName = sec.getString("display-name", perkId);
        if (displayName == null || displayName.isEmpty()) displayName = perkId;

        MessageUtil.sendPlain(sender,
                MessageUtil.getPrefix() + "&aPerk &e" + displayName + " &ade &e"
                        + target.getName() + " &amis à &b" + newLevel + "&a.");

        if (!sender.equals(target)) {
            MessageUtil.sendPlain(target,
                    MessageUtil.getPrefix() + "&eTon perk &b" + displayName
                            + " &ea été mis à jour par un administrateur (&b" + newLevel + "&e).");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {

        // /prestigehoe perk <action>
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String s : Arrays.asList("reset", "set", "add", "remove")) {
                if (s.startsWith(current)) result.add(s);
            }
            return result;
        }

        // /prestigehoe perk reset <perkId>
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            String current = args[1].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String perkId : getAllPrestigePerkIds()) {
                if (perkId.toLowerCase(Locale.ROOT).startsWith(current)) {
                    result.add(perkId);
                }
            }
            return result;
        }

        // /prestigehoe perk <set|add|remove> <joueur> <perkId> <valeur>
        if (args.length == 2 && isModifyAction(args[0])) {
            return tabCompleteOnlinePlayers(args[1]);
        }
        if (args.length == 3 && isModifyAction(args[0])) {
            String current = args[2].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String perkId : getAllPrestigePerkIds()) {
                if (perkId.toLowerCase(Locale.ROOT).startsWith(current)) {
                    result.add(perkId);
                }
            }
            return result;
        }

        return Collections.emptyList();
    }

    private boolean isModifyAction(String s) {
        return s.equalsIgnoreCase("set")
                || s.equalsIgnoreCase("add")
                || s.equalsIgnoreCase("remove");
    }

    private List<String> getAllPrestigePerkIds() {
        List<String> list = new ArrayList<>();

        try {
            FileConfiguration shopCfg = plugin.getConfigManager().getPrestigeShopConfig();
            ConfigurationSection root = shopCfg.getConfigurationSection("prestige_shop.perks");
            if (root != null) {
                list.addAll(root.getKeys(false));
            }
        } catch (Throwable ignored) {
        }

        return list;
    }
}
