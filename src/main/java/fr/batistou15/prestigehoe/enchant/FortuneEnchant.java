package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class FortuneEnchant implements HoeEnchant {

    public static final String ID = "fortune";

    private final PrestigeHoePlugin plugin;

    private boolean enabled;
    private int maxLevel;
    private int defaultLevel;

    public FortuneEnchant(PrestigeHoePlugin plugin) {
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

    @Override
    public void reload(ConfigManager configManager) {
        FileConfiguration cfg = configManager.getEnchantsConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("enchants." + ID);

        if (sec == null) {
            plugin.getLogger().warning("[FortuneEnchant] Section enchants." + ID + " introuvable dans enchants.yml");
            enabled = false;
            maxLevel = 0;
            defaultLevel = 0;
            return;
        }

        enabled = sec.getBoolean("enabled", true);
        maxLevel = sec.getInt("level.max-level", 100);
        defaultLevel = sec.getInt("level.default-level", 1); // si tu ajoutes ça dans ton yaml, sinon 1 par défaut

        // settings éventuellement présents mais pas utilisés directement (on fait Fortune à la vanilla)
        // chance / notifications ne sont pas utiles ici (pas de "proc" unique)

        plugin.getLogger().info(String.format(
                "[PrestigeHoe] [FortuneEnchant] enabled=%s, maxLevel=%d, defaultLevel=%d",
                enabled, maxLevel, defaultLevel
        ));
    }
}
