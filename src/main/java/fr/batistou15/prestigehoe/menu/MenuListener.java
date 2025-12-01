package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public class MenuListener implements Listener {

    private final PrestigeHoePlugin plugin;

    public MenuListener(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        if (!(clickedInv.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= clickedInv.getSize()) {
            return;
        }

        MenuManager menuManager = plugin.getMenuManager();
        MenuItemConfig itemCfg = menuManager.getItem(holder.getMenuId(), rawSlot);
        if (itemCfg == null) {
            return;
        }

        MenuAction action = itemCfg.getAction();
        String value = itemCfg.getActionValue();

        switch (action) {
            case NONE -> {
            }

            case CLOSE -> player.closeInventory();

            case OPEN_MENU -> {
                if (value != null && !value.isEmpty()) {
                    menuManager.openMenu(player, value);
                }
            }

            case OPEN_LEADERBOARD -> {
                if (value != null && !value.isEmpty()) {
                    // prestige / essence / crops_total
                    menuManager.openLeaderboardMenu(player, value);
                }
            }

            case LEADERBOARD_PREVIOUS_PAGE -> {
                menuManager.changeLeaderboardPage(player, -1);
                menuManager.openMenu(player, holder.getMenuId());
            }

            case LEADERBOARD_NEXT_PAGE -> {
                menuManager.changeLeaderboardPage(player, +1);
                menuManager.openMenu(player, holder.getMenuId());
            }

            case UPGRADE_ENCHANT -> menuManager.handleEnchantUpgradeClick(player, value);

            case DISENCHANT_ENCHANT -> menuManager.handleEnchantDisenchantClick(player, value);

            case OPEN_DISENCHANT_MENU -> {
                String selected = menuManager.getSelectedEnchant(player);
                if (selected == null || selected.isEmpty()) {
                    MessageUtil.sendPlain(player,
                            MessageUtil.getPrefix() + "§cAucun enchant sélectionné.");
                    return;
                }
                menuManager.openEnchantDisenchantMenu(player, selected);
            }

            case PRESTIGE_UP -> menuManager.handlePrestigeClick(player);

            case PRESTIGE_SHOP_BUY -> {
                if (value != null && !value.isEmpty()) {
                    menuManager.handlePrestigeShopClick(player, value);
                }
            }

            // Toggles settings
            case TOGGLE_RECAP,
                    TOGGLE_NOTIF_CHAT,
                    TOGGLE_NOTIF_ENCHANT_PROC,
                    TOGGLE_NOTIF_LEVELUP,
                    TOGGLE_NOTIF_ACTIONBAR,
                    TOGGLE_NOTIF_TITLE -> {
                menuManager.handleSettingsToggle(player, action);
                menuManager.openMenu(player, holder.getMenuId());
            }

            case TOGGLE_ENCHANT_NOTIF -> {
                String enchantId = menuManager.getSelectedEnchant(player);
                if (enchantId != null && !enchantId.isEmpty()) {
                    menuManager.toggleEnchantNotif(player, enchantId);
                    menuManager.openEnchantUpgradeMenu(player, enchantId);
                }
            }
        }
    }
}
