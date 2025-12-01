package fr.batistou15.prestigehoe;

import fr.batistou15.prestigehoe.boost.BoostManager;
import fr.batistou15.prestigehoe.bootstrap.CommandRegistrar;
import fr.batistou15.prestigehoe.bootstrap.ListenerRegistrar;
import fr.batistou15.prestigehoe.bootstrap.ResourceBootstrapper;
import fr.batistou15.prestigehoe.bootstrap.StorageFactory;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.crop.CropManager;
import fr.batistou15.prestigehoe.data.*;
import fr.batistou15.prestigehoe.enchant.EnchantDebugService;
import fr.batistou15.prestigehoe.enchant.EnchantManager;
import fr.batistou15.prestigehoe.farm.FarmService;
import fr.batistou15.prestigehoe.formula.FormulaEngine;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.hooks.EconomyHook;
import fr.batistou15.prestigehoe.hooks.JobsHook;
import fr.batistou15.prestigehoe.notification.NotificationService;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;
import fr.batistou15.prestigehoe.recap.RecapService;
import fr.batistou15.prestigehoe.skin.SkinManager;
import fr.batistou15.prestigehoe.util.ProtocolLibEffectsUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

public class PrestigeHoePlugin extends JavaPlugin {

    private static PrestigeHoePlugin instance;

    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private HoeItemManager hoeItemManager;
    private EconomyHook economyHook;
    private CropManager cropManager;
    private EnchantManager enchantManager;
    private FormulaEngine formulaEngine;
    private NotificationService notificationService;
    private RecapService recapService;
    private MenuManager menuManager;
    private FarmService farmService;
    private PrestigeBonusService prestigeBonusService;
    private EnchantDebugService enchantDebugService;
    private JobsHook jobsHook;
    private SkinManager skinManager;
    private DataStorage dataStorage;
    private BukkitTask autoSaveTask;
    private BoostManager boostManager;
    @Override
    public void onEnable() {
        instance = this;

        // 🔍 Debug des dépendances au tout début
        debugDependencies();

        // S'assurer que le dossier plugins/PrestigeHoe existe
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Impossible de créer le dossier de données du plugin.");
        }

        // ======= Ressources par défaut (.yml) =======
        String[] baseResources = {
                "config.yml",
                "messages.yml",
                "enchants.yml",
                "crops.yml",
                "skins.yml",
                "prestige.yml",
                "prestige_shop.yml",
                "grades.yml",
                "boosts.yml",
                "leaderboards.yml",
                "events.yml",
                "guis.yml"
        };
        for (String resource : baseResources) {
            saveDefaultResourceIfNotExists(resource);
        }

        // Menus
        String[] menuResources = {
                "menus/Main.yml",
                "menus/Enchants.yml",
                "menus/UpgradeMenu.yml",
                "menus/DisenchantMenu.yml",
                "menus/PrestigeMenu.yml",
                "menus/PrestigeShopMenu.yml",
                "menus/Skinmenu.yml",
                "menus/LeaderboardsMenu.yml",
                "menus/CropsMenu.yml",
                "menus/Settings.yml"
        };
        for (String resource : menuResources) {
            saveDefaultResourceIfNotExists(resource);
        }

        // ======= Configs =======
        this.configManager = new ConfigManager(this);
        this.configManager.reloadAll();

        // === Storage ===
        this.dataStorage = StorageFactory.create(this, configManager);
        this.playerDataManager = new PlayerDataManager(this, dataStorage);
        // 🔁 Auto-save périodique basé sur config.yml
        setupAutoSaveTask();
        // ======= Managers / Services backend =======
        this.formulaEngine = new FormulaEngine(configManager);

        this.economyHook = new EconomyHook(this);
        this.economyHook.setup();
        // SkinManager
        this.skinManager = new SkinManager(this);

        this.hoeItemManager = new HoeItemManager(this);
        this.cropManager = new CropManager(this, configManager);
        this.enchantManager = new EnchantManager(this, configManager);
        // Boosts (items temporaires)
        this.boostManager = new BoostManager(this, configManager);
        // Service des multiplicateurs de prestige / perks
        this.prestigeBonusService = new PrestigeBonusService(this, configManager);

        // Service de farm (utilise maintenant PrestigeBonusService)
        this.farmService = new FarmService(
                this,
                configManager,
                playerDataManager,
                hoeItemManager,
                cropManager,
                economyHook,
                enchantManager,
                formulaEngine,
                prestigeBonusService
        );

        this.notificationService = new NotificationService(this, configManager);
        this.recapService = new RecapService(this, configManager, playerDataManager);
        this.enchantDebugService = new EnchantDebugService(this);

        // Hook Jobs (facultatif)
// Hook Jobs (facultatif, activable via config)
        FileConfiguration mainCfg = configManager.getMainConfig();
        boolean jobsEnabled = mainCfg.getBoolean("hooks.jobs.enabled", false);
        boolean jobsDebug = mainCfg.getBoolean("hooks.jobs.debug", false);

        this.jobsHook = new JobsHook(this, jobsEnabled, jobsDebug);
        this.jobsHook.setup();

        // Menus
        this.menuManager = new MenuManager(this);

        // ======= Listeners & commandes =======
        ListenerRegistrar listenerRegistrar = new ListenerRegistrar(this);
        listenerRegistrar.registerCoreListeners(
                playerDataManager,
                hoeItemManager,
                menuManager,
                farmService,
                prestigeBonusService,
                skinManager
        );

        new CommandRegistrar(this).registerCommands();

        getLogger().info("PrestigeHoe est activé.");
    }

    @Override
    public void onDisable() {
        // Annule l'auto-save programmé
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        // Fermeture propre des connexions SQL si besoin
        if (dataStorage instanceof SqliteDataStorage sqlite) {
            sqlite.close();
        }
        if (dataStorage instanceof MysqlDataStorage mysql) {
            mysql.close();
        }
        if (recapService != null) {
            recapService.stopAll();
        }
        getLogger().info("PrestigeHoe est désactivé.");
    }

    public static PrestigeHoePlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public HoeItemManager getHoeItemManager() {
        return hoeItemManager;
    }

    public EconomyHook getEconomyHook() {
        return economyHook;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public CropManager getCropManager() {
        return cropManager;
    }
    public BoostManager getBoostManager() {
        return boostManager;
    }
    public EnchantDebugService getEnchantDebugService() {
        return enchantDebugService;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public FarmService getFarmService() {
        return farmService;
    }

    public FormulaEngine getFormulaEngine() {
        return formulaEngine;
    }

    public EnchantManager getEnchantManager() {
        return enchantManager;
    }

    public RecapService getRecapService() {
        return recapService;
    }
    public SkinManager getSkinManager() {
        return skinManager;
    }
    public PrestigeBonusService getPrestigeBonusService() {
        return prestigeBonusService;
    }

    public JobsHook getJobsHook() {
        return jobsHook;
    }

    /**
     * 🔁 Rechargement complet de toutes les configs + systèmes dépendants
     */
    public void reloadAllConfigs() {
        // 1) Recharger tous les .yml
        this.configManager.reloadAll();
        // 2) Recharger les menus
        if (this.menuManager != null) {
            this.menuManager.reloadMenus();
        }

        // 3) Recharger les crops
        if (this.cropManager != null) {
            this.cropManager.reload();
        }

        // 4) Recharger les enchants
        if (this.enchantManager != null) {
            this.enchantManager.reloadAll();
        }

        // 4) Skins
        if (this.skinManager != null) {
            this.skinManager.reload();
        }

        // 5) Hook FarmService (si on ajoute un jour des caches / formules dynamiques)
        if (this.farmService != null) {
            this.farmService.reloadConfig();
        }

        // 6) Recap (intervalle, activé, etc.)
        if (this.recapService != null) {
            this.recapService.reload();
        }

        // 7) Boosts (items temporaires)
        if (this.boostManager != null) {
            this.boostManager.reload();
        }

        // 8) Mettre à jour le display des hoes pour tous les joueurs connectés
        if (this.playerDataManager != null && this.farmService != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                PlayerProfile profile = playerDataManager.getProfile(player);
                if (profile == null) continue;

                HoeData hoeData = profile.getHoeData();
                if (hoeData == null) continue;

                this.farmService.updateHoeDisplay(player, hoeData);
            }
        }

        // 9) Relancer la tâche d'auto-save périodique (au cas où l'intervalle a changé)
        setupAutoSaveTask();
    }

        type = type.toUpperCase(Locale.ROOT);
        getLogger().info("[Storage] Type demandé dans config: " + type);

        switch (type) {
            case "SQLITE": {
                if (storageSec == null) {
                    getLogger().warning("[Storage] Section storage absente, fallback JSON.");
                    dataStorage = new JsonDataStorage(this);
                    break;
                }

                ConfigurationSection sqliteSec = storageSec.getConfigurationSection("sqlite");
                if (sqliteSec == null) {
                    getLogger().warning("[Storage] Section storage.sqlite absente, fallback JSON.");
                    dataStorage = new JsonDataStorage(this);
                    break;
                }

                String fileName = sqliteSec.getString("file", "prestigehoe.db");
                File dbFile = new File(getDataFolder(), fileName);

                dataStorage = new SqliteDataStorage(this, dbFile);
                getLogger().info("[Storage] Utilisation de SQLite (" + dbFile.getName() + ").");
                break;
            }

            case "MYSQL": {
                if (storageSec == null) {
                    getLogger().warning("[Storage] Section storage absente, fallback JSON.");
                    dataStorage = new JsonDataStorage(this);
                    break;
                }

                ConfigurationSection mysqlSec = storageSec.getConfigurationSection("mysql");
                if (mysqlSec == null) {
                    getLogger().warning("[Storage] Section storage.mysql absente, fallback JSON.");
                    dataStorage = new JsonDataStorage(this);
                    break;
                }

                dataStorage = new MysqlDataStorage(this, mysqlSec);
                getLogger().info("[Storage] Utilisation de MySQL.");
                break;
            }

            // JSON + tout autre type inconnu => fallback JSON
            default: {
                dataStorage = new JsonDataStorage(this);
                getLogger().info("[Storage] Utilisation du stockage JSON (par joueur). Type=" + type);
                break;
            }
        }
    }
    public void setupAutoSaveTask() {
        // Annule l'ancienne tâche si elle existe
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        if (configManager == null) {
            return;
        }

        var mainCfg = configManager.getMainConfig();
        var storageSec = mainCfg.getConfigurationSection("storage");
        if (storageSec == null) {
            getLogger().warning("[Storage] Section storage absente, auto-save désactivé.");
            return;
        }

        var autoSec = storageSec.getConfigurationSection("auto-save");
        boolean enabled = autoSec != null && autoSec.getBoolean("enabled", true);
        int intervalSeconds = autoSec != null ? autoSec.getInt("interval-seconds", 300) : 300;

        if (!enabled || intervalSeconds <= 0) {
            getLogger().info("[Storage] Auto-save désactivé (enabled=" + enabled + ", interval=" + intervalSeconds + ").");
            return;
        }

        // Petite sécurité : éviter les intervalles ridicules
        if (intervalSeconds < 30) {
            getLogger().warning("[Storage] interval-seconds trop bas (" + intervalSeconds + "s), utilisation de 30s minimum.");
            intervalSeconds = 30;
        }

        long periodTicks = intervalSeconds * 20L;

        autoSaveTask = getServer().getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    try {
                        if (playerDataManager != null) {
                            playerDataManager.saveAll();
                            getLogger().fine("[Storage] Auto-save périodique exécuté.");
                        }
                    } catch (Exception ex) {
                        getLogger().log(Level.WARNING, "[Storage] Erreur lors de l'auto-save périodique", ex);
                    }
                },
                periodTicks,
                periodTicks
        );

        getLogger().info("[Storage] Auto-save activé toutes les " + intervalSeconds + " secondes.");
    }
    /**
     * Alias utilisé par la commande /prestigehoe reload
     */
    public void reloadPluginConfigs() {
        reloadAllConfigs();
    }

    /**
     * 🔍 Affiche dans la console l'état des dépendances importantes.
     */
    private void debugDependencies() {
        PluginManager pm = getServer().getPluginManager();

        getLogger().info("========== [PrestigeHoe] Détection des dépendances ==========");

        logDependency(pm, "Vault");
        logDependency(pm, "PlaceholderAPI");
        logDependency(pm, "ProtocolLib");
        logDependency(pm, "WorldGuard");
        logDependency(pm, "ItemsAdder");
        // 🆕 Jobs
        logDependency(pm, "Jobs");

        // 🔁 Check spécifique ProtocolLib (lib chargée + ProtocolManager OK)
        boolean protocolLibUsable = ProtocolLibEffectsUtil.isProtocolLibAvailable();
        if (protocolLibUsable) {
            getLogger().info("[Deps] ProtocolLib est disponible et utilisable (ProtocolManager OK).");
        } else {
            getLogger().warning("[Deps] ProtocolLib est absent OU inutilisable (pas de ProtocolManager).");
        }

        getLogger().info("==============================================================");
    }

    /**
     * Log simple : présent/absent + version si présent.
     */
    private void logDependency(PluginManager pm, String pluginName) {
        Plugin dep = pm.getPlugin(pluginName);
        if (dep != null && dep.isEnabled()) {
            getLogger().info("[Deps] " + pluginName + " trouvé (v" + dep.getDescription().getVersion() + ")");
        } else {
            getLogger().warning("[Deps] " + pluginName + " manquant ou désactivé.");
        }
    }
}
