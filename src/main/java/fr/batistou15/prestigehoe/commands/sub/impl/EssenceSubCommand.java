package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class EssenceSubCommand extends AbstractSubCommand {

    public EssenceSubCommand(PrestigeHoePlugin plugin) {
        super(
                plugin,
                "essence",
                null, // on gère les permissions en interne (view/pay/admin)
                false,
                "Voir ou gérer l'Essence des joueurs."
        );
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {

        // /prestigehoe essence
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendConfigMessage(sender,
                        "commands.essence.errors.not-a-player",
                        "%prefix%&cSeul un joueur peut utiliser cette commande.");
                return true;
            }

            if (!sender.hasPermission("prestigehoe.essence.view")) {
                // on utilise toujours l'erreur générique
                plugin.getLogger().fine("Player " + player.getName() + " tried /" + label + " essence without permission.");
                plugin.getLogger().fine("Required permission: prestigehoe.essence.view");
                // errors.no-permission déjà dans messages.yml
                fr.batistou15.prestigehoe.util.MessageUtil.send(sender, "errors.no-permission");
                return true;
            }

            PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
            if (profile == null) {
                sendConfigMessage(sender,
                        "commands.essence.errors.no-profile",
                        "%prefix%&cProfil introuvable.");
                return true;
            }

            double essence = profile.getEssence();
            String formatted = NumberFormatUtil.formatShort(essence);

            Map<String, String> ph = new HashMap<>();
            ph.put("%player%", player.getName());
            ph.put("%amount%", formatted);

            sendConfigMessage(player,
                    "commands.essence.self-balance",
                    "%prefix%&bTu possèdes &e%amount% &bEssence.",
                    ph);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // /prestigehoe essence pay <joueur> <montant>
        if (sub.equals("pay")) {
            if (!(sender instanceof Player player)) {
                sendConfigMessage(sender,
                        "commands.essence.errors.not-a-player",
                        "%prefix%&cSeul un joueur peut utiliser cette commande.");
                return true;
            }

            if (!sender.hasPermission("prestigehoe.essence.pay")) {
                fr.batistou15.prestigehoe.util.MessageUtil.send(sender, "errors.no-permission");
                return true;
            }

            if (args.length < 3) {
                sendConfigMessage(sender,
                        "commands.essence.usage.pay",
                        "%prefix%&cUsage: /%label% essence pay <joueur> <montant>",
                        Map.of("%label%", label));
                return true;
            }

            String targetName = args[1];
            String amountStr = args[2];

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                fr.batistou15.prestigehoe.util.MessageUtil.send(sender, "errors.invalid-target");
                return true;
            }

            PlayerProfile fromProfile = plugin.getPlayerDataManager().getProfile(player);
            PlayerProfile toProfile = plugin.getPlayerDataManager().getProfile(target);
            if (fromProfile == null || toProfile == null) {
                sendConfigMessage(sender,
                        "commands.essence.errors.no-profile",
                        "%prefix%&cProfil introuvable pour l'un des joueurs.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException ex) {
                sendConfigMessage(sender,
                        "commands.essence.errors.invalid-amount",
                        "%prefix%&cMontant invalide: &e%value%",
                        Map.of("%value%", amountStr));
                return true;
            }

            if (amount <= 0) {
                sendConfigMessage(sender,
                        "commands.essence.errors.negative-amount",
                        "%prefix%&cLe montant doit être supérieur à 0.");
                return true;
            }

            double balance = fromProfile.getEssence();
            if (balance < amount) {
                sendConfigMessage(sender,
                        "commands.essence.errors.not-enough",
                        "%prefix%&cTu n'as pas assez d'Essence pour envoyer &e%amount%&c.",
                        Map.of("%amount%", NumberFormatUtil.formatShort(amount)));
                return true;
            }

            fromProfile.removeEssence(amount);
            toProfile.addEssence(amount);

            String formatted = NumberFormatUtil.formatShort(amount);

            Map<String, String> phSender = new HashMap<>();
            phSender.put("%target%", target.getName());
            phSender.put("%amount%", formatted);

            Map<String, String> phTarget = new HashMap<>();
            phTarget.put("%sender%", player.getName());
            phTarget.put("%amount%", formatted);

            sendConfigMessage(player,
                    "commands.essence.pay.sent",
                    "%prefix%&aTu as envoyé &b%amount% Essence &aà &e%target%&a.",
                    phSender);

            sendConfigMessage(target,
                    "commands.essence.pay.received",
                    "%prefix%&aTu as reçu &b%amount% Essence &ade &e%sender%&a.",
                    phTarget);

            return true;
        }

        // /prestigehoe essence <set|add|remove> <joueur> <montant>
        if (sub.equals("set") || sub.equals("add") || sub.equals("remove")) {

            if (!sender.hasPermission("prestigehoe.admin.essence")) {
                fr.batistou15.prestigehoe.util.MessageUtil.send(sender, "errors.no-permission");
                return true;
            }

            if (args.length < 3) {
                Map<String, String> ph = new HashMap<>();
                ph.put("%label%", label);
                ph.put("%sub%", sub);
                sendConfigMessage(sender,
                        "commands.essence.admin.usage",
                        "%prefix%&cUsage: /%label% essence %sub% <joueur> <montant>",
                        ph);
                return true;
            }

            String targetName = args[1];
            String amountStr = args[2];

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                fr.batistou15.prestigehoe.util.MessageUtil.send(sender, "errors.invalid-target");
                return true;
            }

            PlayerProfile profile = plugin.getPlayerDataManager().getProfile(target);
            if (profile == null) {
                sendConfigMessage(sender,
                        "commands.essence.errors.no-profile",
                        "%prefix%&cProfil introuvable pour ce joueur.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException ex) {
                sendConfigMessage(sender,
                        "commands.essence.errors.invalid-amount",
                        "%prefix%&cMontant invalide: &e%value%",
                        Map.of("%value%", amountStr));
                return true;
            }

            if (amount < 0) {
                sendConfigMessage(sender,
                        "commands.essence.errors.negative-amount",
                        "%prefix%&cLe montant doit être positif.");
                return true;
            }

            double current = profile.getEssence();
            double newValue = current;

            switch (sub) {
                case "set" -> newValue = amount;
                case "add" -> newValue = current + amount;
                case "remove" -> newValue = current - amount;
            }

            if (newValue < 0) newValue = 0;

            double delta = newValue - current;
            if (delta > 0) {
                profile.addEssence(delta);
            } else if (delta < 0) {
                profile.removeEssence(-delta);
            }

            String formatted = NumberFormatUtil.formatShort(newValue);

            Map<String, String> phAdmin = new HashMap<>();
            phAdmin.put("%target%", target.getName());
            phAdmin.put("%amount%", formatted);

            Map<String, String> phPlayer = new HashMap<>();
            phPlayer.put("%amount%", formatted);

            sendConfigMessage(sender,
                    "commands.essence.admin.updated-admin",
                    "%prefix%&aEssence de &e%target% &amis à &b%amount%&a.",
                    phAdmin);

            if (!sender.equals(target)) {
                sendConfigMessage(target,
                        "commands.essence.admin.updated-player",
                        "%prefix%&eUn administrateur a mis à jour ton Essence: &b%amount%",
                        phPlayer);
            }

            return true;
        }

        // Action inconnue
        sendConfigMessage(sender,
                "commands.essence.usage.unknown",
                "%prefix%&cUsage: &e/%label% essence [pay|set|add|remove] ...",
                Map.of("%label%", label));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {

        // /prestigehoe essence <sub>
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();

            if (sender.hasPermission("prestigehoe.essence.pay") && "pay".startsWith(current)) {
                result.add("pay");
            }
            if (sender.hasPermission("prestigehoe.admin.essence")) {
                for (String s : Arrays.asList("set", "add", "remove")) {
                    if (s.startsWith(current)) result.add(s);
                }
            }

            return result;
        }

        // /prestigehoe essence pay <joueur> <montant>
        // /prestigehoe essence set|add|remove <joueur> <montant>
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("pay") || sub.equals("set") || sub.equals("add") || sub.equals("remove")) {
                return tabCompleteOnlinePlayers(args[1]);
            }
        }

        return Collections.emptyList();
    }
}
