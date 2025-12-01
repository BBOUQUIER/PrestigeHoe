package fr.batistou15.prestigehoe.bootstrap;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.boost.BoostUseListener;
import fr.batistou15.prestigehoe.farm.FarmService;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.listeners.*;
import fr.batistou15.prestigehoe.menu.MenuManager;
import fr.batistou15.prestigehoe.menu.MenuListener;
import fr.batistou15.prestigehoe.menu.SkinMenuListener;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;
import fr.batistou15.prestigehoe.skin.SkinManager;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.List;

public class ListenerRegistrar {

    private final JavaPlugin plugin;
    private final PluginManager pluginManager;

    public ListenerRegistrar(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        this.pluginManager = plugin.getServer().getPluginManager();
    }

    public void registerCoreListeners(PlayerDataManager playerDataManager,
                                      HoeItemManager hoeItemManager,
                                      MenuManager menuManager,
                                      FarmService farmService,
                                      PrestigeBonusService prestigeBonusService,
                                      SkinManager skinManager) {
        List<Listener> listeners = Arrays.asList(
                new PlayerConnectionListener(playerDataManager, hoeItemManager, plugin.getConfigManager()),
                new HoeProtectionListener(plugin),
                new FarmListener(farmService),
                new MenuListener(plugin),
                new HoeMenuOpenListener(plugin),
                new HoeSpeedListener(plugin, playerDataManager, hoeItemManager, prestigeBonusService),
                new BoostUseListener(plugin),
                new SkinMenuListener(plugin, skinManager, playerDataManager, hoeItemManager, menuManager.getSkinMenuService()),
                new ExplosiveTntListener(plugin)
        );

        listeners.forEach(listener -> pluginManager.registerEvents(listener, plugin));
    }
}
