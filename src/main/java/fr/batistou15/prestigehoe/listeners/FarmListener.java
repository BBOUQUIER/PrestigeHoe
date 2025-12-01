package fr.batistou15.prestigehoe.listeners;

import fr.batistou15.prestigehoe.farm.FarmService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

public class FarmListener implements Listener {

    private final FarmService farmService;

    public FarmListener(FarmService farmService) {
        this.farmService = farmService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        farmService.handleBlockBreak(event);
    }
    /**
     * Force l’instant-break pour toutes les cultures gérées par PrestigeHoe
     * quand le joueur utilise la PrestigeHoe.
     *
     * → Résultat : les melons / citrouilles / cacao / cultures custom
     *    se cassent en 1 tap comme le blé.
     */
    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (tool == null || tool.getType() == Material.AIR) return;
        if (!farmService.getHoeItemManager().isPrestigeHoe(tool)) return;

        Block block = event.getBlock();

        // On n'insta-break que les blocs reconnus comme "crops" dans crops.yml
        if (farmService.getCropManager().getDefinition(block).isEmpty()) {
            return;
        }

        // Ici on sait que :
        //  - le joueur tient la PrestigeHoe
        //  - le bloc est une crop définie dans crops.yml (blé, melon, tomate custom, etc.)
        event.setInstaBreak(true);
    }
}
