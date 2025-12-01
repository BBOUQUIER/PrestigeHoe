package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.config.ConfigManager;

public interface HoeEnchant {

    /**
     * ID de l'enchant : doit correspondre à la clé dans enchants.yml
     * ex : "fortune", "autosell", "explosive", etc.
     */
    String getId();

    /**
     * Recharger la config de cet enchant depuis les fichiers .yml.
     */
    void reload(ConfigManager configManager);
}
