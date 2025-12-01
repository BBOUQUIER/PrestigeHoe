package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class AutosellEnchant implements HoeEnchant {

    public static final String ID = "autosell";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    // notifications (pas encore utilisées, mais lues pour cohérence)
    private boolean notifEnabled;
    private String notifDisplayMode;
    private String notifMessage;

    public AutosellEnchant(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return ID;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getDefaultLevel() {
        return defaultLevel;
    }

    public int getLevel(HoeData hoe) {
        if (hoe == null) return 0;
        return hoe.getEnchantLevel(ID, defaultLevel);
    }

    public boolean hasAutosell(HoeData hoe) {
        if (!enabled || hoe == null) return false;
        return getLevel(hoe) > 0;
    }

    @Override
    public void reload(ConfigManager configManager) {
        FileConfiguration cfg = configManager.getEnchantsConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("enchants." + ID);

        if (sec == null) {
            plugin.getLogger().warning("[AutosellEnchant] Section enchants." + ID + " introuvable dans enchants.yml");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        maxLevel = sec.getInt("level.max-level", 100);
        defaultLevel = sec.getInt("level.default-level", 1);

        // notifications
        ConfigurationSection notifSec = sec.getConfigurationSection("notifications");
        if (notifSec != null) {
            notifEnabled = notifSec.getBoolean("enabled", false);
            notifDisplayMode = notifSec.getString("display-mode", "CHAT");
            notifMessage = notifSec.getString("on-proc-message", "");
        } else {
            notifEnabled = false;
            notifDisplayMode = "CHAT";
            notifMessage = "";
        }

        plugin.getLogger().info(String.format(
                "[PrestigeHoe] [AutosellEnchant] enabled=%s, maxLevel=%d, defaultLevel=%d",
                enabled, maxLevel, defaultLevel
        ));
    }
}
