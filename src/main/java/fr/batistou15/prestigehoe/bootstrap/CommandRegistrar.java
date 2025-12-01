package fr.batistou15.prestigehoe.bootstrap;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.commands.PrestigeHoeCommand;
import org.bukkit.command.PluginCommand;

public class CommandRegistrar {

    private final PrestigeHoePlugin plugin;

    public CommandRegistrar(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        PrestigeHoeCommand prestigeCmd = new PrestigeHoeCommand(plugin);

        registerCommand("prestigehoe", prestigeCmd, true);
        registerCommand("essencehoe", prestigeCmd, false);
    }

    private void registerCommand(String name, PrestigeHoeCommand executor, boolean required) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else if (required) {
            plugin.getLogger().severe("Commande /" + name + " non trouvée dans plugin.yml !");
        } else {
            plugin.getLogger().warning("Commande /" + name + " non trouvée dans plugin.yml (alias).");
        }
    }
}
