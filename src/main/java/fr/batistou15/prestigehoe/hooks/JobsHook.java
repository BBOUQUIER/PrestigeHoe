package fr.batistou15.prestigehoe.hooks;

import com.gamingmesh.jobs.api.JobsExpGainEvent;
import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.enchant.EnchantManager;
import fr.batistou15.prestigehoe.enchant.JobXpBoosterEnchant;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.skin.SkinManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

public class JobsHook implements Listener {

    private final PrestigeHoePlugin plugin;
    private final boolean enabledInConfig;
    private final boolean debug;

    private boolean jobsPresent = false;

    public JobsHook(PrestigeHoePlugin plugin, boolean enabledInConfig, boolean debug) {
        this.plugin = plugin;
        this.enabledInConfig = enabledInConfig;
        this.debug = debug;
    }

    public void setup() {
        if (!enabledInConfig) {
            plugin.getLogger().info("[JobsHook] Hook Jobs désactivé dans config.yml (hooks.jobs.enabled = false).");
            return;
        }

        PluginManager pm = plugin.getServer().getPluginManager();
        Plugin jobs = pm.getPlugin("Jobs");

        if (jobs == null || !jobs.isEnabled()) {
            plugin.getLogger().warning("[JobsHook] Plugin Jobs Reborn non trouvé ou désactivé. Le hook ne sera pas actif.");
            return;
        }

        pm.registerEvents(this, plugin);
        jobsPresent = true;

        plugin.getLogger().info("[JobsHook] Hook Jobs Reborn initialisé avec succès.");
    }

    public boolean isJobsPresent() {
        return jobsPresent;
    }

    public boolean isEnabledInConfig() {
        return enabledInConfig;
    }

    @EventHandler
    public void onJobsExpGain(JobsExpGainEvent event) {
        if (!enabledInConfig || !jobsPresent) {
            return;
        }

        // Jobs Reborn renvoie un OfflinePlayer
        OfflinePlayer offline = event.getPlayer();
        if (offline == null) {
            return;
        }

        Player player = offline.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }

        // Il faut que le joueur tienne la PrestigeHoe en main
        HoeItemManager hoeItemManager = plugin.getHoeItemManager();
        if (hoeItemManager == null) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hoeItemManager.isPrestigeHoe(hand)) {
            return;
        }

        // Récupérer le profil + HoeData
        PlayerDataManager playerDataManager = plugin.getPlayerDataManager();
        if (playerDataManager == null) return;

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null) return;

        HoeData hoe = profile.getHoeData();
        if (hoe == null) return;

        // Récupérer l'enchant JobXpBooster
        EnchantManager enchantManager = plugin.getEnchantManager();
        if (enchantManager == null) return;

        JobXpBoosterEnchant jobXpBooster = enchantManager.getJobXpBoosterEnchant();
        if (jobXpBooster == null || !jobXpBooster.isEnabled()) {
            return;
        }

        // XP de base renvoyée par Jobs
        double baseExp = event.getExp();
        if (baseExp <= 0.0D) {
            return;
        }

        // 🔹 Mult enchants (JobXpBooster)
        double enchMult = jobXpBooster.getXpMultiplier(hoe);  // >= 1.0 normalement
        if (enchMult <= 1.0D) {
            // si l'enchant ne donne aucun bonus, on regarde quand même le skin
            enchMult = 1.0D;
        }

        // 🔹 Mult skin (job-xp-multiplier)
        double skinMult = 1.0D;
        SkinManager skinManager = plugin.getSkinManager();
        if (skinManager != null) {
            double m = skinManager.getJobXpMultiplier(profile);
            if (m > 0.0D) {
                skinMult = m;
            }
        }

        // 🔹 Multiplicateur total
        double totalMult = enchMult * skinMult;
        if (totalMult <= 1.0D) {
            // aucun boost réel => pas besoin de toucher à l'event
            return;
        }

        double newExp = baseExp * totalMult;
        event.setExp(newExp);

        if (debug) {
            plugin.getLogger().info(
                    "[JobsHook] " + player.getName()
                            + " : Jobs XP " + baseExp + " -> " + newExp
                            + " (enchMult=" + String.format(java.util.Locale.US, "%.3f", enchMult)
                            + ", skinMult=" + String.format(java.util.Locale.US, "%.3f", skinMult)
                            + ", total=" + String.format(java.util.Locale.US, "%.3f", totalMult)
                            + ")"
            );
        }
    }

}
