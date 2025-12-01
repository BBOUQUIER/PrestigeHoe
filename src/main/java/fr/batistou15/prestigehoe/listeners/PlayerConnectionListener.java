package fr.batistou15.prestigehoe.listeners;

import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final PlayerDataManager dataManager;
    private final HoeItemManager hoeItemManager;
    private final ConfigManager configManager;

    public PlayerConnectionListener(PlayerDataManager dataManager,
                                    HoeItemManager hoeItemManager,
                                    ConfigManager configManager) {
        this.dataManager = dataManager;
        this.hoeItemManager = hoeItemManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        dataManager.handleJoin(player);

        FileConfiguration cfg = configManager.getMainConfig();
        boolean autoGive = cfg.getBoolean("hoe.auto-give-on-first-join", true);

        if (autoGive && !player.hasPlayedBefore()) {
            if (!hoeItemManager.playerHasHoe(player)) {
                hoeItemManager.giveNewHoe(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dataManager.handleQuit(event.getPlayer());
    }
}
