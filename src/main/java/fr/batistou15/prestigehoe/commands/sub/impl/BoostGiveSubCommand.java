package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.boost.BoostManager;
import fr.batistou15.prestigehoe.boost.BoostManager.BoostDefinition;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class BoostGiveSubCommand extends AbstractSubCommand {

    public BoostGiveSubCommand(PrestigeHoePlugin plugin) {
        // /prestigehoe boost ...
        // permission: prestigehoe.admin.boost.give
        super(plugin, "boost", "prestigehoe.admin.boost.give", false);
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {
        // /prestigehoe boost give <joueur> <boostId> [amount]
        // args[0] = "give"
        // args[1] = "<joueur>"
        // args[2] = "<boostId>"
        // args[3] = "[amount]"

        if (args.length < 3) {
            sendConfigMessage(
                    sender,
                    "admin.usage.boost-give",
                    "%prefix%&cUsage: /" + label + " boost give <joueur> <boostId> [amount]"
            );
            return true;
        }

        String subAction = args[0].toLowerCase(Locale.ROOT);
        if (!subAction.equals("give")) {
            sendConfigMessage(
                    sender,
                    "admin.usage.boost-give",
                    "%prefix%&cUsage: /" + label + " boost give <joueur> <boostId> [amount]"
            );
            return true;
        }

        String targetName = args[1];
        String boostId = args[2];

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0) {
                    amount = 1;
                }
            } catch (NumberFormatException ex) {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "§cQuantité invalide: §e" + args[3]);
                return true;
            }
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            // Tu as déjà ce message dans messages.yml
            MessageUtil.send(sender, "errors.invalid-target");
            return true;
        }

        BoostManager boostManager = plugin.getBoostManager();
        if (boostManager == null) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "§cBoostManager non initialisé.");
            return true;
        }

        BoostDefinition def = boostManager.getDefinition(boostId);
        if (def == null || !def.isEnabled()) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "§cBoost introuvable ou désactivé: §e" + boostId);
            return true;
        }

        // Don de l’item (BoostManager gère inventaire + drop au sol)
        boostManager.giveBoostItem(target, def.getId(), amount);

        // Feedback au staff
        MessageUtil.sendPlain(sender,
                MessageUtil.getPrefix() + "&aTu as donné &ex" + amount + " &a[&e"
                        + def.getDisplayName() + "&a] à &e" + target.getName());

        // Message au joueur (si différent de l’émetteur)
        if (!sender.equals(target)) {
            MessageUtil.sendPlain(target,
                    MessageUtil.getPrefix() + "&aTu as reçu &ex" + amount + " &a[&e"
                            + def.getDisplayName() + "&a].");
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // /prestigehoe boost <...>
        List<String> result = new ArrayList<>();

        // args[0] → sous-action : "give"
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            if ("give".startsWith(partial)) {
                result.add("give");
            }
            return result;
        }

        // args[1] → joueur
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return tabCompleteOnlinePlayers(args[1]);
        }

        // args[2] → boostId
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            BoostManager boostManager = plugin.getBoostManager();
            if (boostManager == null) {
                return Collections.emptyList();
            }

            String partial = args[2].toLowerCase(Locale.ROOT);
            for (BoostDefinition def : boostManager.getAllDefinitions()) {
                String id = def.getId();
                if (id != null && id.toLowerCase(Locale.ROOT).startsWith(partial)) {
                    result.add(id);
                }
            }
            return result;
        }

        // args[3] → quantité (pas de tab complète particulière)
        return Collections.emptyList();
    }
}
