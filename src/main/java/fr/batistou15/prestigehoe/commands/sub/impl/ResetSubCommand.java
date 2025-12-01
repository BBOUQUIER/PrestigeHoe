package fr.batistou15.prestigehoe.commands.sub.impl;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.AbstractSubCommand;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class ResetSubCommand extends AbstractSubCommand {

    public ResetSubCommand(PrestigeHoePlugin plugin) {
        super(
                plugin,
                "reset",
                "prestigehoe.admin.reset",
                false,
                "Reset complet de la houe et des stats du joueur."
        );
    }

    @Override
    protected boolean executeCommand(CommandSender sender, String label, String[] args) {

        if (args.length < 1) {
            sendConfigMessage(sender,
                    "admin.usage.reset",
                    "%prefix%&cUsage: /" + label + " reset <joueur>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
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

        // 1) Reset complet des stats & monnaies (crops, essence farmée, argent farmé, tokens...)
        // -> à implémenter dans PlayerProfile si ce n'est pas déjà le cas
        profile.resetAllProgress(); // ⬅️ méthode à ajouter dans PlayerProfile (voir commentaire plus bas)

        // 2) On recrée une HoeData clean
        HoeData newHoe = new HoeData();
        plugin.getEnchantManager().applyDefaultEnchants(newHoe);
        profile.setHoeData(newHoe);

        // 3) Met à jour l'affichage
        plugin.getFarmService().updateHoeDisplay(target, newHoe);

        MessageUtil.sendPlain(sender,
                MessageUtil.getPrefix() + "&aLa houe et les stats de &e" + target.getName() + " &aont été réinitialisées.");
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
