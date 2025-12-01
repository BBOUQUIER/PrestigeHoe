package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class TokenSubCommand extends AbstractSubCommand {

    public TokenSubCommand(PrestigeHoePlugin plugin) {
        super(
                plugin,
                "token",
                "prestigehoe.admin.token",
                false,
                "Gérer les Prestige Tokens (add/remove/set)."
        );
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {

        // /prestigehoe token <add|remove|set> <joueur> <montant>
        if (args.length < 3) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cUsage: &e/" + label + " token <add|remove|set> <joueur> <montant>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        String targetName = args[1];
        String amountStr = args[2];

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            MessageUtil.send(sender, "errors.invalid-target");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException ex) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cMontant invalide: &e" + amountStr);
            return true;
        }

        if (amount < 0) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cLe montant doit être positif.");
            return true;
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(target);
        if (profile == null) {
            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&cProfil introuvable pour ce joueur.");
            return true;
        }

        long current = profile.getPrestigeTokens();
        long newValue = current;

        switch (action) {
            case "set" -> newValue = amount;
            case "add" -> newValue = current + amount;
            case "remove" -> newValue = current - amount;
            default -> {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "&cAction invalide. Utilise &eadd, remove, set&c.");
                return true;
            }
        }

        if (newValue < 0) newValue = 0;

        // On passe par addPrestigeTokens(delta) pour éviter de créer un setter
        long delta = newValue - current;
        if (delta != 0) {
            profile.addPrestigeTokens(delta);
        }

        String formatted = NumberFormatUtil.formatShort(newValue);
        MessageUtil.sendPlain(sender,
                MessageUtil.getPrefix() + "&aPrestige Tokens de &e" + target.getName()
                        + " &amis à &d" + formatted + "&a.");

        if (!sender.equals(target)) {
            MessageUtil.sendPlain(target,
                    MessageUtil.getPrefix() + "&eUn administrateur a mis à jour tes Prestige Tokens: &d" + formatted);
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {

        // /prestigehoe token <action>
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String s : Arrays.asList("add", "remove", "set")) {
                if (s.startsWith(current)) result.add(s);
            }
            return result;
        }

        // /prestigehoe token <action> <joueur>
        if (args.length == 2) {
            return tabCompleteOnlinePlayers(args[1]);
        }

        // /prestigehoe token <action> <joueur> <montant>
        return Collections.emptyList();
    }
}
