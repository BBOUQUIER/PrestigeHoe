package fr.batistou15.prestigehoe.farm;

import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.enchant.EnchantManager;
import fr.batistou15.prestigehoe.enchant.HoeEnchant;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class EnchantUpgradeService {

    private final ConfigManager configManager;
    private final EnchantManager enchantManager;
    private final HoeItemManager hoeItemManager;
    private final PlayerDataManager playerDataManager;
    private final HoeDisplayService displayService;

    public EnchantUpgradeService(ConfigManager configManager,
                                 EnchantManager enchantManager,
                                 HoeItemManager hoeItemManager,
                                 PlayerDataManager playerDataManager,
                                 HoeDisplayService displayService) {
        this.configManager = configManager;
        this.enchantManager = enchantManager;
        this.hoeItemManager = hoeItemManager;
        this.playerDataManager = playerDataManager;
        this.displayService = displayService;
    }

    /**
     * @return nombre de niveaux effectivement achetés (0 si rien).
     */
    public int attemptBulkUpgradeEnchant(Player player, String enchantId, int requestedLevels) {
        if (enchantId == null) return 0;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR || !hoeItemManager.isPrestigeHoe(item)) {
            MessageUtil.send(player, "errors.not-holding-hoe");
            return 0;
        }

        if (!hoeItemManager.isOwnedBy(item, player)) {
            MessageUtil.send(player, "errors.not-owner");
            return 0;
        }

        PlayerProfile profile = playerDataManager.getProfile(player);
        HoeData hoe = profile.getHoeData();
        if (hoe == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cAucune donnée de houe trouvée.");
            return 0;
        }

        HoeEnchant enchant = enchantManager.getEnchant(enchantId);
        if (enchant == null) {
            MessageUtil.send(player, "errors.invalid-enchant");
            return 0;
        }

        int currentLevel = hoe.getEnchantLevel(enchantId);
        int maxLevel = enchantManager.getMaxLevel(enchantId);
        if (currentLevel >= maxLevel) {
            MessageUtil.send(player, "errors.at-max-level");
            return 0;
        }

        int requiredHoeLevel = enchantManager.getRequiredHoeLevel(enchantId);
        if (hoe.getLevel() < requiredHoeLevel) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cTu dois avoir une houe niveau &e" + requiredHoeLevel
                            + " &c(minimum) pour améliorer cet enchant.");
            return 0;
        }

        int requiredPrestige = enchantManager.getRequiredPrestige(enchantId);
        if (hoe.getPrestige() < requiredPrestige) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cTu dois avoir un prestige &e" + requiredPrestige
                            + " &c(minimum) pour cet enchant.");
            return 0;
        }

        int maxByConfig = maxLevel - currentLevel;
        if (requestedLevels <= 0) {
            requestedLevels = maxByConfig;
        }
        if (requestedLevels <= 0) {
            MessageUtil.send(player, "errors.at-max-level");
            return 0;
        }

        if (requestedLevels > maxByConfig) {
            requestedLevels = maxByConfig;
        }

        double essenceBalance = profile.getEssence();
        double totalCost = 0.0;
        int levelsBought = 0;
        int level = currentLevel;

        for (int i = 0; i < requestedLevels; i++) {
            double cost = enchantManager.getEssenceCostForNextLevel(enchantId, level);
            if (cost <= 0) break;

            if (essenceBalance < totalCost + cost) {
                break;
            }

            totalCost += cost;
            levelsBought++;
            level++;
        }

        if (levelsBought <= 0) {
            MessageUtil.send(player, "errors.not-enough-essence");
            return 0;
        }

        if (totalCost > 0) {
            profile.removeEssence(totalCost);
        }

        int newLevel = currentLevel + levelsBought;
        hoe.setEnchantLevel(enchantId, newLevel);

        displayService.updateHoeDisplay(player, hoe);

        return levelsBought;
    }

    public void attemptBulkDisenchantEnchant(Player player, String enchantId, int requestedLevels) {
        if (enchantId == null) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR || !hoeItemManager.isPrestigeHoe(item)) {
            MessageUtil.send(player, "errors.not-holding-hoe");
            return;
        }

        if (!hoeItemManager.isOwnedBy(item, player)) {
            MessageUtil.send(player, "errors.not-owner");
            return;
        }

        PlayerProfile profile = playerDataManager.getProfile(player);
        HoeData hoe = profile.getHoeData();
        if (hoe == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cAucune donnée de houe trouvée.");
            return;
        }

        var enchant = enchantManager.getEnchant(enchantId);
        if (enchant == null) {
            MessageUtil.send(player, "errors.invalid-enchant");
            return;
        }

        int currentLevel = hoe.getEnchantLevel(enchantId);
        if (currentLevel <= 0) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cTu n'as aucun niveau de cet enchant à retirer.");
            return;
        }

        if (requestedLevels <= 0) {
            requestedLevels = currentLevel;
        }
        if (requestedLevels > currentLevel) {
            requestedLevels = currentLevel;
        }

        double refundPercent = configManager.getMainConfig()
                .getDouble("disenchant.essence-refund-percent", 50.0);

        double totalCost = 0.0;
        int levelsRemoved = 0;
        int level = currentLevel;

        for (int i = 0; i < requestedLevels; i++) {
            if (level <= 0) break;

            int fromLevel = level - 1;
            double cost = enchantManager.getEssenceCostForNextLevel(enchantId, fromLevel);
            if (cost <= 0) break;

            totalCost += cost;
            levelsRemoved++;
            level--;
        }

        if (levelsRemoved <= 0) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cImpossible de retirer des niveaux pour cet enchant.");
            return;
        }

        double refund = totalCost * (refundPercent / 100.0);
        if (refund > 0) {
            profile.addEssence(refund);
        }

        int newLevel = currentLevel - levelsRemoved;
        hoe.setEnchantLevel(enchantId, newLevel);

        displayService.updateHoeDisplay(player, hoe);

        FileConfiguration enchCfg = configManager.getEnchantsConfig();
        String path = "enchants." + enchantId + ".display-name";
        String displayName = enchCfg.getString(path, enchantId);
        if (displayName == null || displayName.isEmpty()) {
            displayName = enchantId;
        }

        String formattedRefund = NumberFormatUtil.formatShort(refund);
        MessageUtil.sendPlain(player,
                MessageUtil.getPrefix() + "&aTu as retiré &e" + levelsRemoved
                        + " &aniveaux de &e" + displayName
                        + " &aet reçu &b" + formattedRefund + " &aEssence.");
    }

    public void attemptDisenchantEnchant(Player player, String enchantId) {
        attemptBulkDisenchantEnchant(player, enchantId, 1);
    }

    public void attemptUpgradeEnchant(Player player, String enchantId) {
        attemptBulkUpgradeEnchant(player, enchantId, 1);
    }
}
