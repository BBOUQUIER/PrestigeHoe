package fr.batistou15.prestigehoe.farm;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.crop.CropManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.enchant.EnchantManager;
import fr.batistou15.prestigehoe.formula.FormulaEngine;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.hooks.EconomyHook;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.event.block.BlockBreakEvent;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService.BonusContext;
import org.bukkit.entity.Player;

public class FarmService {

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final HoeItemManager hoeItemManager;
    private final CropManager cropManager;
    private final EconomyHook economyHook;
    private final EnchantManager enchantManager;
    private final FormulaEngine formulaEngine;

    // Services "métier"
    private final HoeProgressionService progressionService;
    private final HoeDisplayService displayService;
    private final RewardFormulaService rewardFormulaService;
    private final EnchantUpgradeService enchantUpgradeService;
    private final PrestigeService prestigeService;
    private final FarmExecutionService farmExecutionService;
    private final PrestigeBonusService prestigeBonusService;

    public FarmService(PrestigeHoePlugin plugin,
                       ConfigManager configManager,
                       PlayerDataManager playerDataManager,
                       HoeItemManager hoeItemManager,
                       CropManager cropManager,
                       EconomyHook economyHook,
                       EnchantManager enchantManager,
                       FormulaEngine formulaEngine,PrestigeBonusService prestigeBonusService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.hoeItemManager = hoeItemManager;
        this.cropManager = cropManager;
        this.economyHook = economyHook;
        this.enchantManager = enchantManager;
        this.formulaEngine = formulaEngine;
        this.prestigeBonusService = prestigeBonusService;

        // Initialisation des services
        this.progressionService = new HoeProgressionService(formulaEngine, configManager);
        this.displayService = new HoeDisplayService(configManager, hoeItemManager, playerDataManager, progressionService);
        this.rewardFormulaService = new RewardFormulaService(formulaEngine,prestigeBonusService);
        this.enchantUpgradeService = new EnchantUpgradeService(configManager, enchantManager, hoeItemManager, playerDataManager, displayService);
        this.prestigeService = new PrestigeService(plugin, configManager, playerDataManager, hoeItemManager, displayService, progressionService);
        this.farmExecutionService = new FarmExecutionService(
                plugin,
                configManager,
                playerDataManager,
                hoeItemManager,
                cropManager,
                economyHook,
                enchantManager,
                rewardFormulaService,
                displayService,
                progressionService
        );
    }

    // =========================================================
    //                   GESTION DU FARM
    // =========================================================

    public void handleBlockBreak(BlockBreakEvent event) {
        farmExecutionService.handleBlockBreak(event);
    }

    // =========================================================
    //              XP HOE & NIVEAUX (FORMULES)
    // =========================================================

    public double getXpRequiredForLevel(HoeData hoe) {
        return progressionService.getXpRequiredForLevel(hoe);
    }

    public int getMaxHoeLevel(HoeData hoe) {
        return progressionService.getMaxHoeLevel(hoe);
    }

    // =========================================================
    //                   AFFICHAGE DE LA HOE
    // =========================================================

    public void updateHoeDisplay(Player player, HoeData hoe) {
        displayService.updateHoeDisplay(player, hoe);
    }

    // =========================================================
    //              UPGRADE / DISENCHANT D’ENCHANT
    // =========================================================

    public void attemptBulkUpgradeEnchant(Player player, String enchantId, int requestedLevels) {
        int levelsBought = enchantUpgradeService.attemptBulkUpgradeEnchant(player, enchantId, requestedLevels);
        if (levelsBought <= 0 || enchantId == null) {
            return;
        }

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null || profile.getHoeData() == null) return;
        HoeData hoe = profile.getHoeData();

        var enchCfg = configManager.getEnchantsConfig();
        String path = "enchants." + enchantId + ".display-name";
        String displayName = enchCfg.getString(path, enchantId);
        if (displayName == null || displayName.isEmpty()) {
            displayName = enchantId;
        }

        if (plugin.getNotificationService() != null) {
            int newLevel = hoe.getEnchantLevel(enchantId, 0);
            plugin.getNotificationService().notifyEnchantUpgrade(
                    player,
                    hoe,
                    enchantId,
                    displayName,
                    newLevel
            );
        }
    }

    public void attemptBulkDisenchantEnchant(Player player, String enchantId, int requestedLevels) {
        enchantUpgradeService.attemptBulkDisenchantEnchant(player, enchantId, requestedLevels);
    }

    public void attemptDisenchantEnchant(Player player, String enchantId) {
        enchantUpgradeService.attemptDisenchantEnchant(player, enchantId);
    }

    public void attemptUpgradeEnchant(Player player, String enchantId) {
        enchantUpgradeService.attemptUpgradeEnchant(player, enchantId);
    }

    public void reloadConfig() {
        // Pour l’instant, FarmService lit directement dans configManager & formulaEngine
    }

    // =========================================================
    //                 PRESTIGE & PRESTIGE SHOP
    // =========================================================

    public void attemptPrestige(Player player) {
        prestigeService.attemptPrestige(player);
    }

    public void attemptBuyPrestigePerk(Player player, String perkId) {
        prestigeService.attemptBuyPrestigePerk(player, perkId);
    }

    public void attemptResetPrestigePerk(Player player, String perkId) {
        prestigeService.attemptResetPrestigePerk(player, perkId);
    }

    // =========================================================
    //                   GETTERS UTILITAIRES
    // =========================================================

    public HoeItemManager getHoeItemManager() {
        return hoeItemManager;
    }

    public CropManager getCropManager() {
        return cropManager;
    }
}
