package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;

/**
 * Enchant "spawner_finder" entièrement drivé par la config,
 * en réutilisant la logique de ConfigurableCustomProcEnchant.
 */
public class SpawnerFinderEnchant extends ConfigurableCustomProcEnchant {

    public static final String ID = "spawner_finder";

    public SpawnerFinderEnchant(PrestigeHoePlugin plugin) {
        super(plugin, ID);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void reload(ConfigManager configManager) {
        super.reload(configManager);
    }
}
