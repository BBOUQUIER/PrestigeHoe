package fr.batistou15.prestigehoe.boost;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class BoostUseListener implements Listener {

    private final PrestigeHoePlugin plugin;

    public BoostUseListener(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();

        // On ne garde que les clics droits
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        BoostManager boostManager = plugin.getBoostManager();
        if (boostManager == null) {
            return;
        }

        // On tente de lire l'id de boost dans le PDC
        String boostId = boostManager.getBoostIdFromItem(item);

        // Petit log debug pour voir ce qu'il se passe
        plugin.getLogger().info("[Boosts] Interact par "
                + player.getName()
                + " action=" + action
                + " item=" + item.getType()
                + " boostId=" + boostId);

        if (boostId == null || boostId.isEmpty()) {
            // Pas un item boost -> on ne touche à rien
            return;
        }

        // On consomme et on active le boost
        boolean activated = boostManager.consumeAndActivateBoost(player, item);

        plugin.getLogger().info("[Boosts] consumeAndActivateBoost("
                + boostId + ") => " + activated);

        if (activated) {
            // On annule l'action vanilla (éviter de placer un bloc, etc.)
            event.setCancelled(true);
        }
    }
}
