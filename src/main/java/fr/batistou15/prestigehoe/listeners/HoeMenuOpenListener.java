package fr.batistou15.prestigehoe.listeners;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.menu.MenuManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class HoeMenuOpenListener implements Listener {

    private final PrestigeHoePlugin plugin;
    private final MenuManager menuManager;
    private final HoeItemManager hoeItemManager;

    public HoeMenuOpenListener(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        this.menuManager = plugin.getMenuManager();
        this.hoeItemManager = plugin.getHoeItemManager();
    }

    private FileConfiguration cfg() {
        return plugin.getConfigManager().getMainConfig();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        // Quel item on utilise ?
        ItemStack item = (hand == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (item == null || item.getType() == Material.AIR || !hoeItemManager.isPrestigeHoe(item)) {
            return;
        }

        FileConfiguration config = cfg();
        String menuId = null;

        // MAIN HAND
        if (hand == EquipmentSlot.HAND) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                menuId = config.getString("hoe-menus.right-click", "");
            } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                menuId = config.getString("hoe-menus.left-click", "");
            }
        }
        // OFF-HAND : clic avec la hoe dans la main gauche
        else if (hand == EquipmentSlot.OFF_HAND) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK
                    || action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                menuId = config.getString("hoe-menus.off-hand", "");
            }
        }

        if (menuId != null && !menuId.isEmpty()) {
            event.setCancelled(true);
            menuManager.openMenu(player, menuId);
        }
    }
}
