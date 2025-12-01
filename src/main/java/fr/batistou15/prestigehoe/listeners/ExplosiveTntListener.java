package fr.batistou15.prestigehoe.listeners;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public class ExplosiveTntListener implements Listener {

    public static final String TAG = "prestigehoe_explosive";

    private final PrestigeHoePlugin plugin;

    public ExplosiveTntListener(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof TNTPrimed)) return;
        if (!entity.getScoreboardTags().contains(TAG)) return;

        // Empêche la destruction de blocs et les drops
        event.blockList().clear();
        event.setYield(0f);
    }
}
