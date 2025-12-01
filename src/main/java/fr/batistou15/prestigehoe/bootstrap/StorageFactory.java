package fr.batistou15.prestigehoe.bootstrap;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.DataStorage;
import fr.batistou15.prestigehoe.data.JsonDataStorage;
import fr.batistou15.prestigehoe.data.MysqlDataStorage;
import fr.batistou15.prestigehoe.data.SqliteDataStorage;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.util.Locale;
import java.util.logging.Logger;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static DataStorage create(PrestigeHoePlugin plugin, ConfigManager configManager) {
        var mainCfg = configManager.getMainConfig();
        ConfigurationSection storageSec = mainCfg.getConfigurationSection("storage");

        String type = "JSON";
        if (storageSec != null) {
            type = storageSec.getString("type", "JSON");
        }
        if (type == null) {
            type = "JSON";
        }

        type = type.toUpperCase(Locale.ROOT);
        Logger logger = plugin.getLogger();
        logger.info("[Storage] Type demandé dans config: " + type);

        switch (type) {
            case "SQLITE":
                return createSqlite(plugin, storageSec, logger);
            case "MYSQL":
                return createMysql(plugin, storageSec, logger);
            default:
                logger.info("[Storage] Utilisation du stockage JSON (par joueur). Type=" + type);
                return new JsonDataStorage(plugin);
        }
    }

    private static DataStorage createSqlite(PrestigeHoePlugin plugin, ConfigurationSection storageSec, Logger logger) {
        if (storageSec == null) {
            logger.warning("[Storage] Section storage absente, fallback JSON.");
            return new JsonDataStorage(plugin);
        }

        ConfigurationSection sqliteSec = storageSec.getConfigurationSection("sqlite");
        if (sqliteSec == null) {
            logger.warning("[Storage] Section storage.sqlite absente, fallback JSON.");
            return new JsonDataStorage(plugin);
        }

        String fileName = sqliteSec.getString("file", "prestigehoe.db");
        File dbFile = new File(plugin.getDataFolder(), fileName);

        logger.info("[Storage] Utilisation de SQLite (" + dbFile.getName() + ").");
        return new SqliteDataStorage(plugin, dbFile);
    }

    private static DataStorage createMysql(PrestigeHoePlugin plugin, ConfigurationSection storageSec, Logger logger) {
        if (storageSec == null) {
            logger.warning("[Storage] Section storage absente, fallback JSON.");
            return new JsonDataStorage(plugin);
        }

        ConfigurationSection mysqlSec = storageSec.getConfigurationSection("mysql");
        if (mysqlSec == null) {
            logger.warning("[Storage] Section storage.mysql absente, fallback JSON.");
            return new JsonDataStorage(plugin);
        }

        logger.info("[Storage] Utilisation de MySQL.");
        return new MysqlDataStorage(plugin, mysqlSec);
    }
}
