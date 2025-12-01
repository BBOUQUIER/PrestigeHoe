package fr.batistou15.prestigehoe.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class MenuHolder implements InventoryHolder {

    private final String menuId;
    private Inventory inventory;

    private final Map<Integer, String> enchantSlots = new HashMap<>();
    private final Map<Integer, String> prestigePerkSlots = new HashMap<>(); // ⬅️ nouveau

    public MenuHolder(String menuId) {
        this.menuId = menuId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getMenuId() {
        return menuId;
    }

    public void setEnchantForSlot(int slot, String enchantId) {
        enchantSlots.put(slot, enchantId);
    }

    public String getEnchantForSlot(int slot) {
        return enchantSlots.get(slot);
    }

    // === Prestige Shop ===
    public void setPrestigePerkForSlot(int slot, String perkId) {
        prestigePerkSlots.put(slot, perkId);
    }

    public String getPrestigePerkForSlot(int slot) {
        return prestigePerkSlots.get(slot);
    }
}
