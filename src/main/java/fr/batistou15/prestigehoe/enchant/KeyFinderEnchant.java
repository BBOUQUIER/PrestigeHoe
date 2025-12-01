package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;

/**
 * Enchant "key_finder" entièrement drivé par la config,
 * en réutilisant la logique de ConfigurableCustomProcEnchant
 * (settings.rewards, notifications.display-mode, etc.).
 */
public class KeyFinderEnchant extends ConfigurableCustomProcEnchant {

    public static final String ID = "key_finder";

    public KeyFinderEnchant(PrestigeHoePlugin plugin) {
        // On passe l'ID à la classe mère, comme pour custom_enchantX
        super(plugin, ID);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void reload(ConfigManager configManager) {
        // On laisse la classe mère faire tout le boulot
        super.reload(configManager);
    }
}
