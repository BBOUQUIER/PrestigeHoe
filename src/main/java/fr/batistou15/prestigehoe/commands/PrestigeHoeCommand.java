package fr.batistou15.prestigehoe.commands;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.sub.SubCommand;
import fr.batistou15.prestigehoe.commands.sub.impl.*;
import org.bukkit.command.*;

import java.util.*;

public class PrestigeHoeCommand implements CommandExecutor, TabCompleter {

    private final PrestigeHoePlugin plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public PrestigeHoeCommand(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        registerSubCommands();
    }

    private void registerSubCommands() {
        register(new HelpSubCommand(plugin, this));
        register(new ReloadSubCommand(plugin));
        register(new GiveSubCommand(plugin));
        register(new SetEnchantSubCommand(plugin));
        register(new SetPrestigeSubCommand(plugin));
        register(new InfoSubCommand(plugin));
        register(new ResetSubCommand(plugin));
        register(new SetLevelSubCommand(plugin));
        register(new DebugSubCommand(plugin));
        register(new MenuSubCommand(plugin));
        register(new SetOwnerSubCommand(plugin));
        register(new PerkSubCommand(plugin));
        register(new TokenSubCommand(plugin));
        register(new EssenceSubCommand(plugin)); // <-- nouveau
        register(new BoostGiveSubCommand(plugin));
    }

    private void register(SubCommand sub) {
        String name = sub.getName().toLowerCase(Locale.ROOT);
        subCommands.put(name, sub);
        for (String alias : sub.getAliases()) {
            subCommands.put(alias.toLowerCase(Locale.ROOT), sub);
        }
    }

    public Collection<SubCommand> getRegisteredSubCommands() {
        return new LinkedHashSet<>(subCommands.values());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Alias /essencehoe -> on préfixe par "essence"
        if (command.getName().equalsIgnoreCase("essencehoe")) {
            String[] newArgs = new String[args.length + 1];
            newArgs[0] = "essence";
            System.arraycopy(args, 0, newArgs, 1, args.length);
            args = newArgs;
        }

        if (args.length == 0) {
            // Rien pour l'instant sur /prestigehoe directement
            return true;
        }

        String key = args[0].toLowerCase(Locale.ROOT);
        SubCommand sub = subCommands.get(key);
        if (sub == null) {
            // Sous-commande inconnue
            return true;
        }

        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return sub.execute(sender, label, subArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        // Tab-complete pour /essencehoe -> on délègue directement à la subcommande "essence"
        if (command.getName().equalsIgnoreCase("essencehoe")) {
            SubCommand essenceSub = subCommands.get("essence");
            if (essenceSub == null) return Collections.emptyList();

            String perm = essenceSub.getPermission();
            // attention : la subcommande essence gère les perms en interne; ici on laisse passer
            String[] subArgs = args; // args sont déjà après "essence"
            List<String> completions = essenceSub.tabComplete(sender, alias, subArgs);
            return completions == null ? Collections.emptyList() : completions;
        }

        // /prestigehoe <sub>
        if (args.length == 1) {
            String current = args[0].toLowerCase(Locale.ROOT);
            Set<String> names = new HashSet<>();

            for (SubCommand sub : getRegisteredSubCommands()) {
                String perm = sub.getPermission();
                if (perm != null && !sender.hasPermission(perm)) continue;
                names.add(sub.getName());
            }

            List<String> result = new ArrayList<>();
            for (String s : names) {
                if (s.toLowerCase(Locale.ROOT).startsWith(current)) {
                    result.add(s);
                }
            }
            Collections.sort(result);
            return result;
        }

        // /prestigehoe <sub> ...
        if (args.length >= 2) {
            String key = args[0].toLowerCase(Locale.ROOT);
            SubCommand sub = subCommands.get(key);
            if (sub == null) return Collections.emptyList();

            String perm = sub.getPermission();
            if (perm != null && !sender.hasPermission(perm)) {
                return Collections.emptyList();
            }

            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
            List<String> completions = sub.tabComplete(sender, alias, subArgs);
            return completions == null ? Collections.emptyList() : completions;
        }

        return Collections.emptyList();
    }
}
