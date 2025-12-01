package fr.batistou15.prestigehoe.listeners;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class UpgradeMenuListener implements Listener {

    private final PrestigeHoePlugin plugin;

    public UpgradeMenuListener(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Clic droit avec la PrestigeHoe -> ouvre le menu.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return;

        if (!plugin.getHoeItemManager().isPrestigeHoe(item)) {
            return;
        }

        event.setCancelled(true);

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        HoeData hoe = profile.getHoeData();
        if (hoe == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cAucune donnée de houe trouvée.");
            return;
        }


    }

    /**
     * Clic dans le menu -> tente un upgrade de l’enchant correspondant.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;


        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        String enchantId = null;

        switch (rawSlot) {
            case 10:
                enchantId = "fortune";
                break;
            case 12:
                enchantId = "autosell";
                break;
            case 14:
                enchantId = "moneybooster";
                break;
            case 16:
                enchantId = "essencebooster";
                break;
            case 22:
                enchantId = "tokenfinder";
                break;
            default:
                return;
        }

        Player player = (Player) event.getWhoClicked();

        // On délègue la logique d’upgrade au FarmService
        plugin.getFarmService().attemptUpgradeEnchant(player, enchantId);

        // On rouvre le menu mis à jour
        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        HoeData hoe = profile.getHoeData();
    }
}
