package fr.batistou15.prestigehoe.enchant;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EnchantManager {

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;

    // Tous les enchants (core + customs)
    private final Map<String, HoeEnchant> enchants = new HashMap<>();

    // ============================
    //          CORE ENCHANTS
    // ============================

    private final FortuneEnchant fortuneEnchant;
    private final AutosellEnchant autosellEnchant;
    private final EssenceBoosterEnchant essenceBoosterEnchant;
    private final MoneyBoosterEnchant moneyBoosterEnchant;
    private final TokenFinderEnchant tokenFinderEnchant;
    private final KeyFinderEnchant keyFinderEnchant;
    private final SpawnerFinderEnchant spawnerFinderEnchant;
    private final HoeXpBoosterEnchant hoeXpBoosterEnchant;
    private final EssencePouchEnchant essencePouchEnchant;
    private final MoneyPouchEnchant moneyPouchEnchant;
    private final FuryEnchant furyEnchant;
    private final ProcBoosterEnchant procBoosterEnchant;
    private final PlayerXpBoosterEnchant playerXpBoosterEnchant;

    // Futurs enchants (quand les classes seront créées) :
    private final ExplosiveEnchant explosiveEnchant;
    private final LineBreakerEnchant lineBreakerEnchant;
    private final SpeedEnchant speedEnchant;
    private final JobXpBoosterEnchant jobXpBoosterEnchant;

    // Enchants custom_enchantX
    private final Map<String, ConfigurableCustomProcEnchant> customProcEnchants = new HashMap<>();

    public EnchantManager(PrestigeHoePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        // ============================
        //    Instanciation "core"
        // ============================

        this.fortuneEnchant = new FortuneEnchant(plugin);
        this.autosellEnchant = new AutosellEnchant(plugin);
        this.essenceBoosterEnchant = new EssenceBoosterEnchant(plugin);
        this.moneyBoosterEnchant = new MoneyBoosterEnchant(plugin);
        this.tokenFinderEnchant = new TokenFinderEnchant(plugin);
        this.keyFinderEnchant = new KeyFinderEnchant(plugin);
        this.spawnerFinderEnchant = new SpawnerFinderEnchant(plugin);
        this.hoeXpBoosterEnchant = new HoeXpBoosterEnchant(plugin);
        this.essencePouchEnchant = new EssencePouchEnchant(plugin);
        this.moneyPouchEnchant = new MoneyPouchEnchant(plugin);
        this.furyEnchant = new FuryEnchant(plugin);
        this.procBoosterEnchant = new ProcBoosterEnchant(plugin);

        // Futurs enchants (à décommenter quand les classes seront créées) :
        this.explosiveEnchant = new ExplosiveEnchant(plugin);
        this.lineBreakerEnchant = new LineBreakerEnchant(plugin);
        this.speedEnchant = new SpeedEnchant(plugin);
        this.jobXpBoosterEnchant = new JobXpBoosterEnchant(plugin);
        this.playerXpBoosterEnchant = new PlayerXpBoosterEnchant(plugin);

        // ============================
        //   Enregistrement des core
        // ============================

        register(fortuneEnchant);
        register(autosellEnchant);
        register(essenceBoosterEnchant);
        register(moneyBoosterEnchant);
        register(tokenFinderEnchant);
        register(keyFinderEnchant);
        register(spawnerFinderEnchant);
        register(hoeXpBoosterEnchant);
        register(essencePouchEnchant);
        register(moneyPouchEnchant);
        register(furyEnchant);
        register(procBoosterEnchant);

        // Futurs enchants :
        register(explosiveEnchant);
        register(lineBreakerEnchant);
        register(speedEnchant);
        register(jobXpBoosterEnchant);
        register(playerXpBoosterEnchant);

        // 🔄 Charge les custom_enchantX + reload tout
        reloadAll();
    }

    private void register(HoeEnchant enchant) {
        enchants.put(enchant.getId().toLowerCase(Locale.ROOT), enchant);
    }

    // =========================================================
    //                    RELOAD
    // =========================================================

    public void reloadAll() {
        // 1) Re-scan les custom_enchantX depuis enchants.yml
        loadCustomProcEnchantsFromConfig();

        // 2) Reload de TOUS les enchants (core + custom)
        for (HoeEnchant enchant : enchants.values()) {
            enchant.reload(configManager);
        }
        plugin.getLogger().info("[EnchantManager] Enchants rechargés (" + enchants.size() + ").");
    }

    private void loadCustomProcEnchantsFromConfig() {
        // Supprime les anciens customs de la map globale
        for (String id : customProcEnchants.keySet()) {
            enchants.remove(id);
        }
        customProcEnchants.clear();

        FileConfiguration enchCfg = configManager.getEnchantsConfig();
        ConfigurationSection root = enchCfg.getConfigurationSection("enchants");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            if (key == null) continue;

            String idLower = key.toLowerCase(Locale.ROOT);

            // On prend tout ce qui commence par "custom_enchant"
            if (!idLower.startsWith("custom_enchant")) continue;

            ConfigurableCustomProcEnchant ce = new ConfigurableCustomProcEnchant(plugin, key);
            customProcEnchants.put(idLower, ce);
            register(ce);
        }

        plugin.getLogger().info("[PrestigeHoe] Custom proc enchants chargés: " + customProcEnchants.keySet());
    }

    // =========================================================
    //                    ACCÈS "CORE"
    // =========================================================

    public FortuneEnchant getFortuneEnchant() {
        return fortuneEnchant;
    }

    public AutosellEnchant getAutosellEnchant() {
        return autosellEnchant;
    }

    public EssenceBoosterEnchant getEssenceBoosterEnchant() {
        return essenceBoosterEnchant;
    }

    public MoneyBoosterEnchant getMoneyBoosterEnchant() {
        return moneyBoosterEnchant;
    }

    public TokenFinderEnchant getTokenFinderEnchant() {
        return tokenFinderEnchant;
    }

    public KeyFinderEnchant getKeyFinderEnchant() {
        return keyFinderEnchant;
    }

    public SpawnerFinderEnchant getSpawnerFinderEnchant() {
        return spawnerFinderEnchant;
    }

    public HoeXpBoosterEnchant getHoeXpBoosterEnchant() {
        return hoeXpBoosterEnchant;
    }

    public EssencePouchEnchant getEssencePouchEnchant() {
        return essencePouchEnchant;
    }

    public MoneyPouchEnchant getMoneyPouchEnchant() {
        return moneyPouchEnchant;
    }

    public FuryEnchant getFuryEnchant() {
        return furyEnchant;
    }

    public ProcBoosterEnchant getProcBoosterEnchant() {
        return procBoosterEnchant;
    }

    // Futurs getters si besoin :
    public ExplosiveEnchant getExplosiveEnchant() { return explosiveEnchant; }
    public LineBreakerEnchant getLineBreakerEnchant() { return lineBreakerEnchant; }
    public SpeedEnchant getSpeedEnchant() { return speedEnchant; }
    public JobXpBoosterEnchant getJobXpBoosterEnchant() { return jobXpBoosterEnchant; }
    public PlayerXpBoosterEnchant getPlayerXpBoosterEnchant() { return playerXpBoosterEnchant; }

    // =========================================================
    //                    ACCÈS CUSTOM
    // =========================================================

    public Map<String, ConfigurableCustomProcEnchant> getCustomProcEnchants() {
        return customProcEnchants;
    }

    // =========================================================
    //                    ACCÈS GÉNÉRIQUE
    // =========================================================

    public HoeEnchant getEnchant(String id) {
        if (id == null) return null;
        return enchants.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> getRegisteredEnchantIds() {
        return enchants.keySet();
    }

    // =========================================================
    //              LECTURE ENCHANTS.YML POUR LES UPGRADES
    // =========================================================

    private ConfigurationSection getEnchantSection(String enchantId) {
        FileConfiguration cfg = configManager.getEnchantsConfig();
        return cfg.getConfigurationSection("enchants." + enchantId.toLowerCase(Locale.ROOT));
    }

    public int getMaxLevel(String enchantId) {
        ConfigurationSection sec = getEnchantSection(enchantId);
        if (sec == null) return 0;
        return sec.getInt("level.max-level", 0);
    }

    public int getRequiredHoeLevel(String enchantId) {
        ConfigurationSection sec = getEnchantSection(enchantId);
        if (sec == null) return 0;
        return sec.getInt("level.require-hoe-level", 0);
    }

    public int getRequiredPrestige(String enchantId) {
        ConfigurationSection sec = getEnchantSection(enchantId);
        if (sec == null) return 0;
        return sec.getInt("level.require-prestige", 0);
    }

    /**
     * Coût en Essence pour passer du niveau currentLevel au niveau (currentLevel+1).
     *
     * cost.essence.base-cost
     * cost.essence.increase-percent-per-level
     */
    public double getEssenceCostForNextLevel(String enchantId, int currentLevel) {
        ConfigurationSection sec = getEnchantSection(enchantId);
        if (sec == null) return 0.0;

        ConfigurationSection essenceSec = sec.getConfigurationSection("cost.essence");
        if (essenceSec == null) return 0.0;

        double baseCost = essenceSec.getDouble("base-cost", 0.0);
        double incPercent = essenceSec.getDouble("increase-percent-per-level", 0.0); // ex: 50.0

        if (baseCost <= 0) return 0.0;

        // Exemple simple : cost = base * (1 + inc%/100)^(currentLevel)
        double factor = 1.0 + (incPercent / 100.0);
        return baseCost * Math.pow(factor, currentLevel);
    }

    // =========================================================
    //              NIVEAUX PAR DÉFAUT SUR LA HOE
    // =========================================================

    public void applyDefaultEnchants(HoeData hoeData) {
        if (hoeData == null) return;

        if (fortuneEnchant != null && fortuneEnchant.isEnabled()) {
            hoeData.setEnchantLevel(FortuneEnchant.ID, fortuneEnchant.getDefaultLevel());
        }
        if (autosellEnchant != null && autosellEnchant.isEnabled()) {
            hoeData.setEnchantLevel(AutosellEnchant.ID, autosellEnchant.getDefaultLevel());
        }
        if (essenceBoosterEnchant != null && essenceBoosterEnchant.isEnabled()) {
            hoeData.setEnchantLevel(EssenceBoosterEnchant.ID, essenceBoosterEnchant.getDefaultLevel());
        }
        if (moneyBoosterEnchant != null && moneyBoosterEnchant.isEnabled()) {
            hoeData.setEnchantLevel(MoneyBoosterEnchant.ID, moneyBoosterEnchant.getDefaultLevel());
        }
        if (tokenFinderEnchant != null && tokenFinderEnchant.isEnabled()) {
            hoeData.setEnchantLevel(TokenFinderEnchant.ID, tokenFinderEnchant.getDefaultLevel());
        }
        if (hoeXpBoosterEnchant != null && hoeXpBoosterEnchant.isEnabled()) {
            hoeData.setEnchantLevel(HoeXpBoosterEnchant.ID, hoeXpBoosterEnchant.getDefaultLevel());
        }
        if (essencePouchEnchant != null && essencePouchEnchant.isEnabled()) {
            hoeData.setEnchantLevel(EssencePouchEnchant.ID, essencePouchEnchant.getDefaultLevel());
        }

        // Pour l’instant on ne met pas de niveaux par défaut aux autres enchants
        // (key_finder, spawner_finder, money_pouch, furie, etc.) pour te laisser le contrôle
        // uniquement via le menu d’upgrade.
    }
}
