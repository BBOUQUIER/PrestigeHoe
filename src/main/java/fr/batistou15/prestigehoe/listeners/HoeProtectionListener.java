package fr.batistou15.prestigehoe.listeners;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
public class HoeProtectionListener implements Listener {

    private final PrestigeHoePlugin plugin;
    private final HoeItemManager hoeItemManager;
    private final ConfigManager configManager;;

    public HoeProtectionListener(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        this.hoeItemManager = plugin.getHoeItemManager();
        this.configManager = plugin.getConfigManager();
    }

    private FileConfiguration cfg() {
        return configManager.getMainConfig();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!cfg().getBoolean("hoe.prevent-drop", true)) return;

        ItemStack item = event.getItemDrop().getItemStack();
        if (hoeItemManager.isPrestigeHoe(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        // Protection activée ?
        if (!cfg().getBoolean("hoe.prevent-offhand", true)) return;

        ItemStack main = event.getMainHandItem();
        ItemStack off = event.getOffHandItem();

        // Si aucune des deux mains n'a une PrestigeHoe, on ne fait rien
        if (!hoeItemManager.isPrestigeHoe(main) && !hoeItemManager.isPrestigeHoe(off)) {
            return;
        }

        // On empêche la hoe d'aller en offhand
        event.setCancelled(true);

        // Si un menu off-hand est configuré, on l'ouvre
        String menuId = cfg().getString("hoe-menus.off-hand", "");
        if (menuId != null && !menuId.isEmpty()) {
            plugin.getMenuManager().openMenu(event.getPlayer(), menuId);
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!cfg().getBoolean("hoe.prevent-container-move", true)) return;

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;

        InventoryType clickedType = clicked.getType();
        boolean clickedIsPlayerInv = clickedType == InventoryType.PLAYER;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // 1️⃣ Gestion spéciale : NUMBER_KEY (touches 1-9 pour la hotbar)
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton(); // 0-8
            if (hotbarSlot >= 0) {
                ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(hotbarSlot);

                // Si on clique dans un inventaire NON joueur (coffre, enderchest, etc.)
                // et que l'item de la hotbar est une PrestigeHoe -> on bloque
                if (!clickedIsPlayerInv && hotbarItem != null && hotbarItem.getType() != Material.AIR
                        && hoeItemManager.isPrestigeHoe(hotbarItem)) {
                    event.setCancelled(true);
                    return;
                }

                // Par sécurité : si l'item dans le coffre est une PrestigeHoe
                // et qu'on essaye de swap avec la hotbar -> on bloque aussi
                if (!clickedIsPlayerInv && current != null && current.getType() != Material.AIR
                        && hoeItemManager.isPrestigeHoe(current)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // 2️⃣ SHIFT-CLICK depuis l'inventaire du joueur -> l'item part vers le "top inventory"
        if (event.isShiftClick() && clickedIsPlayerInv) {
            if (current != null && current.getType() != Material.AIR && hoeItemManager.isPrestigeHoe(current)) {
                event.setCancelled(true);
                return;
            }
        }

        // 3️⃣ Clic normal : on tient la houe sur le curseur et on clique dans un inventaire non joueur (coffre, etc.)
        if (!clickedIsPlayerInv) {
            if (cursor != null && cursor.getType() != Material.AIR && hoeItemManager.isPrestigeHoe(cursor)) {
                event.setCancelled(true);
                return;
            }
        }

        // 4️⃣ Sécurité supplémentaire :
        // si une PrestigeHoe se retrouve déjà dans un inventaire non joueur, on bloque toute interaction dessus
        if (!clickedIsPlayerInv) {
            if (current != null && current.getType() != Material.AIR && hoeItemManager.isPrestigeHoe(current)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!cfg().getBoolean("hoe.prevent-container-move", true)) return;

        ItemStack oldCursor = event.getOldCursor();
        if (oldCursor == null || oldCursor.getType() == Material.AIR) return;
        if (!hoeItemManager.isPrestigeHoe(oldCursor)) return;

        // Taille de l'inventaire du haut (coffre / enderchest / etc.)
        int topSize = event.getView().getTopInventory().getSize();

        // Si une des cases visées par le drag est dans le "top inventory", on annule
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
