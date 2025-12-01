package fr.batistou15.prestigehoe.hooks;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyHook {

    private final PrestigeHoePlugin plugin;
    private Economy economy;

    public EconomyHook(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault n'est pas présent ! L'économie ne fonctionnera pas.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("Aucun fournisseur d'économie trouvé via Vault.");
            return;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("Hook Vault initialisé avec " + economy.getName());
    }

    public boolean isReady() {
        return economy != null;
    }

    public void deposit(Player player, double amount) {
        if (!isReady() || amount <= 0) return;
        economy.depositPlayer((OfflinePlayer) player, amount);
    }
    public double getBalance(Player player) {
        if (economy == null || player == null) return 0.0;
        return economy.getBalance(player);
    }
    public void withdraw(Player player, double amount) {
        if (!isReady() || amount <= 0) return;
        economy.withdrawPlayer((OfflinePlayer) player, amount);
    }
}
