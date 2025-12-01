package fr.batistou15.prestigehoe.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.batistou15.prestigehoe.PrestigeHoePlugin;

import java.io.File;
import java.sql.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class SqliteDataStorage implements DataStorage {

    private final PrestigeHoePlugin plugin;
    private final Gson gson;
    private final String url;
    private Connection connection;

    public SqliteDataStorage(PrestigeHoePlugin plugin, File dbFile) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().create();
        this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        init();
    }

    private void init() {
        try {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {
            }

            getConnection(); // force la création
            try (Statement st = connection.createStatement()) {
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS ph_players (" +
                                "uuid VARCHAR(36) PRIMARY KEY," +
                                "data TEXT NOT NULL" +
                                ");"
                );
            }
            plugin.getLogger().info("[SQLite] Table ph_players initialisée.");
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "[SQLite] Erreur d'initialisation", ex);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    @Override
    public PlayerProfile loadProfile(UUID uuid) {
        if (uuid == null) return null;

        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT data FROM ph_players WHERE uuid = ?"
        )) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String json = rs.getString("data");
                if (json == null || json.isEmpty()) {
                    return null;
                }
                PlayerProfile profile = gson.fromJson(json, PlayerProfile.class);
                if (profile != null && profile.getUuid() == null) {
                    profile.setUuid(uuid);
                }
                return profile;
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "[SQLite] Erreur loadProfile pour " + uuid, ex);
            return null;
        }
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        if (profile == null || profile.getUuid() == null) return;

        plugin.getLogger().info("[SQLITE-STORAGE] saveProfile appelé pour " + profile.getUuid());
        String json = gson.toJson(profile);
        String uuidStr = profile.getUuid().toString();

        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO ph_players (uuid, data) VALUES (?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET data = excluded.data"
        )) {
            ps.setString(1, uuidStr);
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "[SQLite] Erreur saveProfile pour " + uuidStr, ex);
        }
    }

    @Override
    public void saveAll(Map<UUID, PlayerProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return;
        for (PlayerProfile profile : profiles.values()) {
            saveProfile(profile);
        }
    }

    @Override
    public Collection<PlayerProfile> loadAllProfiles() {
        Map<UUID, PlayerProfile> result = new HashMap<>();

        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT uuid, data FROM ph_players"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    String json = rs.getString("data");
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        PlayerProfile profile = gson.fromJson(json, PlayerProfile.class);
                        if (profile == null) continue;
                        if (profile.getUuid() == null) {
                            profile.setUuid(uuid);
                        }
                        result.put(uuid, profile);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "[SQLite] Erreur loadAllProfiles", ex);
        }

        return result.values();
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
