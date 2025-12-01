package fr.batistou15.prestigehoe.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MysqlDataStorage implements DataStorage {

    private final PrestigeHoePlugin plugin;
    private final Gson gson;

    private final String url;
    private final String user;
    private final String password;
    private final String tableName;

    private Connection connection;

    public MysqlDataStorage(PrestigeHoePlugin plugin, ConfigurationSection mysqlSection) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().create();

        String host = mysqlSection.getString("host", "127.0.0.1");
        int port = mysqlSection.getInt("port", 3306);
        String database = mysqlSection.getString("database", "prestigehoe");
        this.user = mysqlSection.getString("user", "root");
        this.password = mysqlSection.getString("password", "password");
        boolean useSsl = mysqlSection.getBoolean("use-ssl", false);
        String prefix = mysqlSection.getString("table-prefix", "ph_");

        this.tableName = prefix + "players";

        String sslParam = useSsl ? "true" : "false";
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=" + sslParam + "&serverTimezone=UTC";

        init();
    }

    private void init() {
        try {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().severe("[MySQL] Driver MySQL introuvable ! Assure-toi de l'avoir ajouté au plugin.");
            }

            getConnection(); // force la connexion
            try (Statement st = connection.createStatement()) {
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                                "uuid VARCHAR(36) NOT NULL PRIMARY KEY," +
                                "data LONGTEXT NOT NULL" +
                                ") ENGINE=InnoDB;"
                );
            }
            plugin.getLogger().info("[MySQL] Table " + tableName + " initialisée.");
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "[MySQL] Erreur d'initialisation", ex);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    @Override
    public PlayerProfile loadProfile(UUID uuid) {
        if (uuid == null) return null;

        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT data FROM " + tableName + " WHERE uuid = ?"
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
                    "[MySQL] Erreur loadProfile pour " + uuid, ex);
            return null;
        }
    }

    @Override
    public void saveProfile(PlayerProfile profile) {
        if (profile == null || profile.getUuid() == null) return;

        String json = gson.toJson(profile);
        String uuidStr = profile.getUuid().toString();

        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO " + tableName + " (uuid, data) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE data = VALUES(data)"
        )) {
            ps.setString(1, uuidStr);
            ps.setString(2, json);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "[MySQL] Erreur saveProfile pour " + uuidStr, ex);
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
                "SELECT uuid, data FROM " + tableName
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
                    "[MySQL] Erreur loadAllProfiles", ex);
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
