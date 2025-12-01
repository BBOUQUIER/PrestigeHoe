package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class SkinMenuHolder implements InventoryHolder {

    private final PrestigeHoePlugin plugin;

    public SkinMenuHolder(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return null; // pas utilisé
    }

    public PrestigeHoePlugin getPlugin() {
        return plugin;
    }
}
