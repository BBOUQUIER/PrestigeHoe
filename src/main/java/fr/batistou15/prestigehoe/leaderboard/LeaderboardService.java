package fr.batistou15.prestigehoe.leaderboard;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class LeaderboardService {

    // ========= POJOs =========

    public static class LeaderboardEntry {
        private final UUID playerUuid;
        private final String playerName;
        private final double value;

        public LeaderboardEntry(UUID playerUuid, String playerName, double value) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.value = value;
        }

        public UUID getPlayerUuid() {
            return playerUuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public double getValue() {
            return value;
        }
    }

    public static class LeaderboardTypeConfig {
        private final String id;
        private final String displayName;
        private final boolean enabled;
        private final int size;

        public LeaderboardTypeConfig(String id, String displayName, boolean enabled, int size) {
            this.id = id;
            this.displayName = displayName;
            this.enabled = enabled;
            this.size = size;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getSize() {
            return size;
        }
    }

    // ========= Champs =========

    private final PrestigeHoePlugin plugin;

    private final Map<String, LeaderboardTypeConfig> typeConfigs = new HashMap<>();
    private final Map<String, List<LeaderboardEntry>> cached = new ConcurrentHashMap<>();

    private int refreshIntervalSeconds = 60;
    private long lastRefreshMillis = 0L;

    // nombre max de pages global (pour tous les types)
    private int maxPages = 2;

    public LeaderboardService(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    // ========= Chargement leaderboards.yml =========

    public void reloadConfig() {
        typeConfigs.clear();
        cached.clear();
        refreshIntervalSeconds = 60;
        lastRefreshMillis = 0L;
        maxPages = 2; // valeur par défaut si non configurée

        try {
            File file = new File(plugin.getDataFolder(), "leaderboards.yml");
            if (!file.exists()) {
                plugin.getLogger().warning("[Leaderboard] leaderboards.yml introuvable, leaderboards désactivés.");
                return;
            }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = cfg.getConfigurationSection("leaderboards");
            if (root == null) {
                plugin.getLogger().warning("[Leaderboard] Section 'leaderboards' absente dans leaderboards.yml");
                return;
            }

            // intervalle de refresh
            refreshIntervalSeconds = root.getInt("refresh-interval-seconds", 60);

            // 🔢 nombre de pages max global (prestige / essence / crops_total)
            maxPages = root.getInt("max-pages", 2);
            if (maxPages < 1) {
                maxPages = 1;
            }

            ConfigurationSection sizeSec = root.getConfigurationSection("size");
            ConfigurationSection typesSec = root.getConfigurationSection("types");

            if (typesSec == null) {
                plugin.getLogger().warning("[Leaderboard] Section 'leaderboards.types' absente.");
                return;
            }

            for (String typeId : typesSec.getKeys(false)) {
                ConfigurationSection tSec = typesSec.getConfigurationSection(typeId);
                if (tSec == null) continue;

                boolean enabled = tSec.getBoolean("enabled", true);
                String displayName = tSec.getString("display-name", typeId);

                int size = 10;
                if (sizeSec != null) {
                    size = sizeSec.getInt(typeId, 10);
                }

                LeaderboardTypeConfig tcfg = new LeaderboardTypeConfig(
                        typeId.toLowerCase(Locale.ROOT),
                        displayName,
                        enabled,
                        size
                );
                typeConfigs.put(tcfg.getId(), tcfg);
            }

            plugin.getLogger().info("[Leaderboard] Types chargés: " + typeConfigs.keySet()
                    + " | max-pages = " + maxPages);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "[Leaderboard] Erreur lors du chargement de leaderboards.yml", ex);
        }
    }

    // ========= Refresh / calcul =========

    private boolean needsRefresh() {
        if (refreshIntervalSeconds <= 0) return true;
        long now = System.currentTimeMillis();
        return (now - lastRefreshMillis) >= (refreshIntervalSeconds * 1000L);
    }

    private void refreshAll() {
        lastRefreshMillis = System.currentTimeMillis();
        cached.clear();

        PlayerDataManager dataMgr = plugin.getPlayerDataManager();
        if (dataMgr == null) return;

        Collection<PlayerProfile> profiles;
        try {
            // ⚠️ PlayerDataManager doit avoir :
            // public Collection<PlayerProfile> getAllProfiles() { return cache.values(); }
            profiles = dataMgr.getAllProfiles();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Leaderboard] Impossible de récupérer la liste des profils.", t);
            return;
        }

        if (profiles == null || profiles.isEmpty()) return;

        for (LeaderboardTypeConfig typeCfg : typeConfigs.values()) {
            if (!typeCfg.isEnabled()) continue;

            String typeId = typeCfg.getId();
            List<LeaderboardEntry> list = new ArrayList<>();

            for (PlayerProfile profile : profiles) {
                if (profile == null || profile.getUuid() == null) continue;

                double value = computeMetric(typeId, profile);
                if (value <= 0.0D) continue; // ignore ceux à 0

                String name = resolveName(profile.getUuid());
                list.add(new LeaderboardEntry(profile.getUuid(), name, value));
            }

            if (list.isEmpty()) {
                cached.put(typeId, Collections.emptyList());
                continue;
            }

            // Tri : valeur décroissante, puis nom
            list.sort(Comparator
                    .comparingDouble(LeaderboardEntry::getValue).reversed()
                    .thenComparing(LeaderboardEntry::getPlayerName, String.CASE_INSENSITIVE_ORDER));

            // Tronquer à la taille max configurée pour ce type
            if (list.size() > typeCfg.getSize()) {
                list = new ArrayList<>(list.subList(0, typeCfg.getSize()));
            }

            cached.put(typeId, Collections.unmodifiableList(list));
        }
    }

    private String resolveName(UUID uuid) {
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            if (off != null && off.getName() != null) {
                return off.getName();
            }
        } catch (Throwable ignored) {
        }
        return uuid.toString();
    }

    private double computeMetric(String typeId, PlayerProfile profile) {
        String id = typeId.toLowerCase(Locale.ROOT);

        switch (id) {
            case "prestige" -> {
                HoeData hoe = profile.getHoeData();
                return hoe != null ? hoe.getPrestige() : 0.0D;
            }
            case "essence" -> {
                return profile.getEssence();
            }
            case "crops_total" -> {
                return profile.getTotalCropsBroken();
            }
            default -> {
                return 0.0D;
            }
        }
    }

    // ========= API publique =========

    public List<LeaderboardEntry> getTop(String typeId) {
        if (typeId == null) return Collections.emptyList();
        String id = typeId.toLowerCase(Locale.ROOT);

        LeaderboardTypeConfig cfg = typeConfigs.get(id);
        if (cfg == null || !cfg.isEnabled()) {
            return Collections.emptyList();
        }

        if (needsRefresh()) {
            refreshAll();
        }

        return cached.getOrDefault(id, Collections.emptyList());
    }

    public String getTypeDisplayName(String typeId) {
        if (typeId == null) return typeId;
        LeaderboardTypeConfig cfg = typeConfigs.get(typeId.toLowerCase(Locale.ROOT));
        if (cfg == null) return typeId;
        return cfg.getDisplayName();
    }

    public String formatValue(String typeId, double value) {
        String id = typeId != null ? typeId.toLowerCase(Locale.ROOT) : "";
        if ("prestige".equals(id)) {
            return String.valueOf((int) value);
        }
        if ("crops_total".equals(id)) {
            return NumberFormatUtil.formatShort((long) value);
        }
        if ("essence".equals(id)) {
            return NumberFormatUtil.formatShort(value);
        }
        return NumberFormatUtil.formatShort(value);
    }

    /**
     * Nombre max de pages global pour les leaderboards.
     * Utilisé par MenuManager.changeLeaderboardPage(...)
     */
    public int getMaxPages() {
        return Math.max(1, maxPages);
    }
}
