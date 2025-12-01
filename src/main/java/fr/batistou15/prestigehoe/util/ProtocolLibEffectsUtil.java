package fr.batistou15.prestigehoe.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.UUID;

public final class ProtocolLibEffectsUtil {

    private ProtocolLibEffectsUtil() {
    }

    /**
     * Vérifie si ProtocolLib est disponible ET si on peut obtenir un ProtocolManager.
     */
    public static boolean isProtocolLibAvailable() {
        try {
            if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
                return false;
            }
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            return pm != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Effet TNT + explosion 100% côté client pour UN joueur.
     *
     * - Si ProtocolLib est dispo -> TNT fake SPAWN + ENTITY_DESTROY.
     * - Sinon -> fallback Bukkit : particules + sons uniquement pour le joueur.
     */
    public static void playClientSideTntExplosion(
            PrestigeHoePlugin plugin,
            Player player,
            Location loc,
            long delayTicks
    ) {
        if (plugin == null || player == null || loc == null) return;

        // 🔁 Fallback Bukkit si ProtocolLib absent
        if (!isProtocolLibAvailable()) {
            // Fumée + son de TNT primée
            player.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            player.spawnParticle(Particle.CLOUD, loc, 15, 0.4, 0.4, 0.4, 0.01);

            // Explosion après le délai
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
                player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            }, delayTicks);
            return;
        }

        ProtocolManager pm;
        try {
            pm = ProtocolLibrary.getProtocolManager();
        } catch (Throwable t) {
            // Si ça foire à chaud, on repasse sur le fallback Bukkit
            player.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
                player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            }, delayTicks);
            return;
        }

        int entityId = (int) (System.nanoTime() & 0x7FFFFFFF);
        UUID uuid = UUID.randomUUID();

        try {
            // 1) Spawn d'une TNT fake côté client
            PacketContainer spawn = pm.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            spawn.getIntegers().write(0, entityId); // entityId
            spawn.getUUIDs().write(0, uuid);

            spawn.getDoubles()
                    .write(0, loc.getX())
                    .write(1, loc.getY())
                    .write(2, loc.getZ());

            // Utilisation directe du EntityType Bukkit
            spawn.getEntityTypeModifier().write(0, EntityType.TNT);

            pm.sendServerPacket(player, spawn);

            // Son de TNT primée + un peu de fumée
            player.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            player.spawnParticle(Particle.CLOUD, loc, 10, 0.3, 0.3, 0.3, 0.01);

            // 2) Après le délai → destruction de l'entité fake + explosion locale Bukkit
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Packet ENTITY_DESTROY
                    PacketContainer destroy = pm.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                    destroy.getIntLists().write(0, Collections.singletonList(entityId));
                    pm.sendServerPacket(player, destroy);
                } catch (Exception ex) {
                    plugin.getLogger().warning("[PrestigeHoe] Erreur envoi packet ENTITY_DESTROY TNT: " + ex.getMessage());
                }

                // Explosion locale (Bukkit API, côté client uniquement)
                player.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
                player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            }, delayTicks);
        } catch (Exception ex) {
            plugin.getLogger().warning("[PrestigeHoe] Erreur création/envoi packets TNT (ProtocolLib): " + ex.getMessage());
            // Fallback simple
            player.playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.spawnParticle(Particle.EXPLOSION, loc, 1, 0, 0, 0, 0);
                player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
            }, delayTicks);
        }
    }
}
