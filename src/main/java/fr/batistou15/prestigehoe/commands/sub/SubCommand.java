package fr.batistou15.prestigehoe.commands.sub;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface SubCommand {

    /**
     * Nom de la sous-commande (ex: "give").
     */
    String getName();

    /**
     * Permission requise (ou null si aucune).
     */
    String getPermission();

    /**
     * Description courte pour /prestigehoe help.
     */
    String getDescription();

    /**
     * Alias éventuels de la sous-commande (ex : ["g"]).
     */
    List<String> getAliases();

    /**
     * Exécution de la sous-commande.
     * @param sender l'expéditeur
     * @param label le label de la commande principale (prestigehoe)
     * @param args les arguments APRÈS le nom de la sous-commande
     */
    boolean execute(CommandSender sender, String label, String[] args);

    /**
     * Tab-completion pour la sous-commande.
     * @param sender l'expéditeur
     * @param alias alias de la commande principale
     * @param args les arguments APRÈS le nom de la sous-commande
     */
    List<String> tabComplete(CommandSender sender, String alias, String[] args);
}
