package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de debug pour les enchants à proc.
 * - Toggle par joueur (via commande)
 * - Affiche les infos quand un enchant PROC
 */
public class EnchantDebugService {

    public static final String DEBUG_PERMISSION = "prestigehoe.admin.debug";

    private final PrestigeHoePlugin plugin;
    private final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();

    public EnchantDebugService(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Est-ce que le joueur a le debug actif ET la permission ?
     */
    public boolean isDebugging(Player player) {
        if (player == null) return false;

        if (!player.hasPermission(DEBUG_PERMISSION)) {
            debugPlayers.remove(player.getUniqueId());
            return false;
        }

        return debugPlayers.contains(player.getUniqueId());
    }

    /**
     * Toggle du mode debug pour ce joueur.
     */
    public void toggle(Player player) {
        if (player == null) return;

        if (!player.hasPermission(DEBUG_PERMISSION)) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cTu n'as pas la permission d'utiliser le mode debug enchants.");
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean now;

        if (debugPlayers.contains(uuid)) {
            debugPlayers.remove(uuid);
            now = false;
        } else {
            debugPlayers.add(uuid);
            now = true;
        }

        String state = now ? "&aACTIVÉ" : "&cDÉSACTIVÉ";
        MessageUtil.sendPlain(player,
                MessageUtil.getPrefix() + "&7Mode debug enchants: " + state + "&7.");
    }

    /**
     * Appelé quand un enchant PROC, pour afficher les infos en chat si debug actif.
     */
    public void sendProcDebug(Player player,
                              String enchantId,
                              int level,
                              EnchantProcHelper.ProcData procData) {
        if (!isDebugging(player)) return;
        if (enchantId == null || procData == null) return;

        String msg = String.format(Locale.US,
                "&8[&dDEBUG-ENCHANT&8] &f%s &7niv &e%d "
                        + "&7base=&e%.4f &7total=&a%.4f",
                enchantId,
                level,
                procData.baseCurrent,
                procData.totalCurrent
        );

        MessageUtil.sendPlain(player, MessageUtil.color(msg));
    }
}
