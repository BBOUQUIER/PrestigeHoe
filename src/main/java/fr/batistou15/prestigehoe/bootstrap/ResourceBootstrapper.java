package fr.batistou15.prestigehoe.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Copies bundled configuration and menu resources to the plugin data folder when missing.
 */
public class ResourceBootstrapper {

    private static final String[] BASE_RESOURCES = {
            "config.yml",
            "messages.yml",
            "enchants.yml",
            "crops.yml",
            "skins.yml",
            "prestige.yml",
            "prestige_shop.yml",
            "grades.yml",
            "boosts.yml",
            "leaderboards.yml",
            "events.yml",
            "guis.yml"
    };

    private static final String[] MENU_RESOURCES = {
            "menus/Main.yml",
            "menus/Enchants.yml",
            "menus/UpgradeMenu.yml",
            "menus/DisenchantMenu.yml",
            "menus/PrestigeMenu.yml",
            "menus/PrestigeShopMenu.yml",
            "menus/Skinmenu.yml",
            "menus/LeaderboardsMenu.yml",
            "menus/CropsMenu.yml",
            "menus/Settings.yml"
    };

    private ResourceBootstrapper() {
        // utility
    }

    public static void copyDefaults(JavaPlugin plugin) {
        for (String resource : BASE_RESOURCES) {
            saveDefaultResourceIfMissing(plugin, resource);
        }
        for (String resource : MENU_RESOURCES) {
            saveDefaultResourceIfMissing(plugin, resource);
        }
    }

    private static void saveDefaultResourceIfMissing(JavaPlugin plugin, String resourcePath) {
        File outFile = new File(plugin.getDataFolder(), resourcePath);
        if (!outFile.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }
}
