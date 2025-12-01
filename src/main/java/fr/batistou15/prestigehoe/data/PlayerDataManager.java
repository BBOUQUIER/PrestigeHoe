package fr.batistou15.prestigehoe.data;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gère le cache des PlayerProfile en mémoire
 * et délègue le stockage à DataStorage (JSON / SQLite / MySQL).
 */
public class PlayerDataManager {

    private final PrestigeHoePlugin plugin;
    private final DataStorage storage;

    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(PrestigeHoePlugin plugin, DataStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public PlayerProfile getProfile(UUID uuid) {
        if (uuid == null) return null;

        return cache.computeIfAbsent(uuid, id -> {
            // Chargement depuis le storage
            PlayerProfile loaded = storage.loadProfile(id);

            if (loaded == null) {
                // Nouveau profil
                loaded = new PlayerProfile(id);

                // Recap par défaut selon config.yml
                boolean defaultRecap = plugin.getConfigManager()
                        .getMainConfig()
                        .getBoolean("recap.default-enabled", true);
                loaded.setRecapEnabled(defaultRecap);
            } else {
                // Sécurité : s'assurer que l'UUID est bien renseigné
                if (loaded.getUuid() == null) {
                    loaded.setUuid(id);
                }
            }

            return loaded;
        });
    }

    public PlayerProfile getProfile(Player player) {
        if (player == null) return null;
        return getProfile(player.getUniqueId());
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerProfile profile = getProfile(uuid);
        plugin.getLogger().fine("Profil chargé pour " + player.getName() + " (" + uuid + ")");
        // Ici plus tard : gestion first join, auto-give hoe, etc.
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerProfile profile = cache.get(uuid);
        if (profile != null) {
            storage.saveProfile(profile);
            cache.remove(uuid);
        }
    }

    /**
     * Retourne tous les profils connus:
     * - ceux en storage (hors-ligne)
     * - ceux en cache (en ligne) qui écrasent la version storage si besoin.
     *
     * Utilisé par le LeaderboardService.
     */
    public Collection<PlayerProfile> getAllProfiles() {
        Map<UUID, PlayerProfile> all = new HashMap<>();

        try {
            Collection<PlayerProfile> fromStorage = storage.loadAllProfiles();
            if (fromStorage != null) {
                for (PlayerProfile profile : fromStorage) {
                    if (profile == null || profile.getUuid() == null) continue;
                    all.put(profile.getUuid(), profile);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "[PlayerData] Erreur lors du chargement global des profils", ex);
        }

        // On écrase avec les profils en mémoire (plus à jour)
        all.putAll(cache);

        return all.values();
    }

    public void saveAll() {
        storage.saveAll(cache);
    }

}
