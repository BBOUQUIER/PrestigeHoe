package fr.batistou15.prestigehoe.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.batistou15.prestigehoe.PrestigeHoePlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Stockage JSON LOCAL.
 * Un fichier par joueur : plugins/PrestigeHoe/data/players/<uuid>.json
 */
public class JsonDataStorage implements DataStorage {

    private final PrestigeHoePlugin plugin;
    private final Gson gson;
    private final File playersFolder;

    public JsonDataStorage(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer le dossier data !");
        }

        this.playersFolder = new File(dataFolder, "players");
        if (!playersFolder.exists() && !playersFolder.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer le dossier data/players !");
        }
    }

    private File getPlayerFile(UUID uuid) {
        return new File(playersFolder, uuid.toString() + ".json");
    }

    @Override
    public PlayerProfile loadProfile(UUID uuid) {
        File file = getPlayerFile(uuid);
        if (!file.exists()) {
            // Aucun fichier => on laisse le PlayerDataManager créer un nouveau profil
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            PlayerProfile profile = gson.fromJson(reader, PlayerProfile.class);
            if (profile == null) {
                plugin.getLogger().warning("Profil JSON vide/corrompu pour " + uuid + ", retour null.");
                return null;
            }
            if (profile.getUuid() == null) {
                profile.setUuid(uuid);
            }
            return profile;
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors du chargement du profil pour " + uuid + " : " + e.getMessage());
            return null;
        }
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        if (profile == null || profile.getUuid() == null) return;

        plugin.getLogger().info("[JSON-STORAGE] saveProfile appelé pour " + profile.getUuid());

        File file = getPlayerFile(profile.getUuid());
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(profile, writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde du profil pour " + profile.getUuid() + " : " + e.getMessage());
        }
    }

    @Override
    public void saveAll(Map<UUID, PlayerProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return;
        profiles.values().forEach(this::saveProfile);
    }

    @Override
    public Collection<PlayerProfile> loadAllProfiles() {
        Map<UUID, PlayerProfile> result = new HashMap<>();

        if (!playersFolder.exists()) {
            return result.values();
        }

        File[] files = playersFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files == null) {
            return result.values();
        }

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                PlayerProfile profile = gson.fromJson(reader, PlayerProfile.class);
                if (profile == null) continue;

                UUID uuid = profile.getUuid();
                if (uuid == null) {
                    // On tente de reconstruire l'UUID à partir du nom du fichier
                    String name = file.getName();
                    if (name.endsWith(".json")) {
                        name = name.substring(0, name.length() - 5);
                    }
                    try {
                        uuid = UUID.fromString(name);
                        profile.setUuid(uuid);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                }

                result.put(uuid, profile);
            } catch (IOException ex) {
                plugin.getLogger().severe("Erreur lors du chargement de " + file.getName() + " : " + ex.getMessage());
            }
        }

        return result.values();
    }
}
