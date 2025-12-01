package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.PrestigeHoeCommand;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.commands.sub.SubCommand;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class HelpSubCommand extends AbstractSubCommand {

    private final PrestigeHoeCommand root;

    public HelpSubCommand(PrestigeHoePlugin plugin, PrestigeHoeCommand root) {
        super(
                plugin,
                "help",
                null, // accessible à tous
                false,
                "Affiche la liste des commandes disponibles."
        );
        this.root = root;
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {

        List<SubCommand> subs = new ArrayList<>(root.getRegisteredSubCommands());
        subs.sort(Comparator.comparing(SubCommand::getName));

        MessageUtil.sendPlain(sender, "§6§m------------§r §ePrestigeHoe - Aide §6§m------------");

        for (SubCommand sub : subs) {
            String perm = sub.getPermission();
            if (perm != null && !sender.hasPermission(perm)) continue;

            String line = "§e/" + label + " " + sub.getName() + "§7 - " + sub.getDescription();
            MessageUtil.sendPlain(sender, line);
        }

        // Section Essence
        MessageUtil.sendPlain(sender, "");
        MessageUtil.sendPlain(sender, "§6§lEssence :");

        // /essence (view)
        if (sender.hasPermission("prestigehoe.essence.view") || !(sender instanceof org.bukkit.entity.Player)) {
            MessageUtil.sendPlain(sender,
                    "§e/essence§7 - Voir ton solde d'Essence.");
        }

        // /essence pay
        if (sender.hasPermission("prestigehoe.essence.pay")) {
            MessageUtil.sendPlain(sender,
                    "§e/essence pay <joueur> <montant>§7 - Envoyer de l'Essence à un autre joueur.");
        }

        // /essence admin
        if (sender.hasPermission("prestigehoe.admin.essence")) {
            MessageUtil.sendPlain(sender,
                    "§e/essence <set|add|remove> <joueur> <montant>§7 - Gérer l'Essence des joueurs.");
        }

        MessageUtil.sendPlain(sender, "§6§m-------------------------------------------");
        return true;
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        // pas de complétion spéciale pour help
        return java.util.Collections.emptyList();
    }
}
