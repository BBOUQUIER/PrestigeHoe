package fr.batistou15.prestigehoe.commands;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class EssenceCommand implements CommandExecutor, TabCompleter {

    private final PrestigeHoePlugin plugin;

    public EssenceCommand(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /essence
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                MessageUtil.send(sender, "errors.not-a-player");
                return true;
            }

            if (!sender.hasPermission("prestigehoe.essence.view")) {
                MessageUtil.send(sender, "errors.no-permission");
                return true;
            }

            PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
            if (profile == null) {
                MessageUtil.sendPlain(player,
                        MessageUtil.getPrefix() + "&cProfil introuvable.");
                return true;
            }

            double essence = profile.getEssence();
            String formatted = NumberFormatUtil.formatShort(essence);
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&bTu possèdes &e" + formatted + " &bEssence.");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // /essence pay <joueur> <montant>
        if (sub.equals("pay")) {
            if (!(sender instanceof Player player)) {
                MessageUtil.send(sender, "errors.not-a-player");
                return true;
            }

            if (!sender.hasPermission("prestigehoe.essence.pay")) {
                MessageUtil.send(sender, "errors.no-permission");
                return true;
            }

            if (args.length < 3) {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "&cUsage: &e/" + label + " pay <joueur> <montant>");
                return true;
            }

            String targetName = args[1];
            String amountStr = args[2];

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                MessageUtil.send(sender, "errors.invalid-target");
                return true;
            }

            PlayerProfile fromProfile = plugin.getPlayerDataManager().getProfile(player);
            PlayerProfile toProfile = plugin.getPlayerDataManager().getProfile(target);
            if (fromProfile == null || toProfile == null) {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "&cProfil introuvable pour l'un des joueurs.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException ex) {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "&cMontant invalide: &e" + amountStr);
                return true;
            }

            if (amount <= 0) {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "&cLe montant doit être supérieur à 0.");
                return true;
            }

            double balance = fromProfile.getEssence();
            if (balance < amount) {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "&cTu n'as pas assez d'Essence.");
                return true;
            }

            fromProfile.removeEssence(amount);
            toProfile.addEssence(amount);

            String formatted = NumberFormatUtil.formatShort(amount);

            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&aTu as envoyé &b" + formatted + " Essence &aà &e" + target.getName() + "&a.");
            MessageUtil.sendPlain(target,
                    MessageUtil.getPrefix() + "&aTu as reçu &b" + formatted + " Essence &ade &e" + player.getName() + "&a.");

            return true;
        }

        // /essence <set|add|remove> <joueur> <montant> (admin)
        if (sub.equals("set") || sub.equals("add") || sub.equals("remove")) {

            if (!sender.hasPermission("prestigehoe.admin.essence")) {
                MessageUtil.send(sender, "errors.no-permission");
                return true;
            }

            if (args.length < 3) {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "&cUsage: &e/" + label + " " + sub + " <joueur> <montant>");
                return true;
            }

            String targetName = args[1];
            String amountStr = args[2];

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                MessageUtil.send(sender, "errors.invalid-target");
                return true;
            }

            PlayerProfile profile = plugin.getPlayerDataManager().getProfile(target);
            if (profile == null) {
                MessageUtil.sendPlain(sender,
                        MessageUtil.getPrefix() + "&cProfil introuvable pour ce joueur.");
                return true;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
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

            double current = profile.getEssence();
            double newValue = current;

            switch (sub) {
                case "set" -> newValue = amount;
                case "add" -> newValue = current + amount;
                case "remove" -> newValue = current - amount;
            }

            if (newValue < 0) newValue = 0;

            // On utilise add/remove pour éviter d'ajouter un setter
            double delta = newValue - current;
            if (delta > 0) {
                profile.addEssence(delta);
            } else if (delta < 0) {
                profile.removeEssence(-delta);
            }

            String formatted = NumberFormatUtil.formatShort(newValue);

            MessageUtil.sendPlain(sender,
                    MessageUtil.getPrefix() + "&aEssence de &e" + target.getName()
                            + " &amis à &b" + formatted + "&a.");

            if (!sender.equals(target)) {
                MessageUtil.sendPlain(target,
                        MessageUtil.getPrefix() + "&eUn administrateur a mis à jour ton Essence: &b" + formatted);
            }

            return true;
        }

        // Action inconnue
        MessageUtil.sendPlain(sender,
                MessageUtil.getPrefix() + "&cUsage: &e/" + label
                        + " [pay|set|add|remove] ...");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        // /essence <sub>
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

        // /essence pay <joueur> <montant>
        // /essence set|add|remove <joueur> <montant>
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("pay")
                    || sub.equals("set")
                    || sub.equals("add")
                    || sub.equals("remove")) {
                String current = args[1].toLowerCase(Locale.ROOT);
                List<String> result = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(current)) {
                        result.add(p.getName());
                    }
                }
                return result;
            }
        }

        return Collections.emptyList();
    }
}
