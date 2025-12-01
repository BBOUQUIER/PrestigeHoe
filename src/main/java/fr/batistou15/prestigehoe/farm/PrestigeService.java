package fr.batistou15.prestigehoe.farm;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PrestigeService {

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final HoeItemManager hoeItemManager;
    private final HoeDisplayService displayService;
    private final HoeProgressionService progressionService;

    public PrestigeService(PrestigeHoePlugin plugin,
                           ConfigManager configManager,
                           PlayerDataManager playerDataManager,
                           HoeItemManager hoeItemManager,
                           HoeDisplayService displayService,
                           HoeProgressionService progressionService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.hoeItemManager = hoeItemManager;
        this.displayService = displayService;
        this.progressionService = progressionService;
    }

    public void attemptPrestige(Player player) {
        if (player == null) {
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || !hoeItemManager.isPrestigeHoe(hand)) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cTu dois tenir ta §ePrestigeHoe §cen main pour prestiger.");
            return;
        }

        if (!hoeItemManager.isOwnedBy(hand, player)) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cCette PrestigeHoe ne t'appartient pas.");
            return;
        }

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cImpossible de charger ton profil, réessaie plus tard.");
            return;
        }

        HoeData hoe = profile.getHoeData();
        if (hoe == null) {
            hoe = new HoeData();
            profile.setHoeData(hoe);
        }

        int currentLevel = hoe.getLevel();
        int maxLevel = progressionService.getMaxHoeLevel(hoe);

        if (currentLevel < maxLevel) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cTu dois atteindre le niveau max §e(" + maxLevel + ") §cpour prestiger ta houe.");
            return;
        }

        int oldPrestige = hoe.getPrestige();
        int newPrestige = oldPrestige + 1;
        hoe.setPrestige(newPrestige);

        hoe.setLevel(1);
        hoe.setXp(0.0);

        long tokensReward = 1L;
        profile.addPrestigeTokens(tokensReward);

        displayService.updateHoeDisplay(player, hoe);

        if (plugin.getNotificationService() != null) {
            plugin.getNotificationService().notifyPrestigeChange(player, hoe);
        }
    }

    public void attemptBuyPrestigePerk(Player player, String perkId) {
        if (player == null || perkId == null || perkId.isEmpty()) return;

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cImpossible de charger ton profil, réessaie plus tard.");
            return;
        }

        FileConfiguration shopCfg = configManager.getPrestigeShopConfig();
        ConfigurationSection root = shopCfg.getConfigurationSection("prestige_shop.perks");
        if (root == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cLa boutique de prestige n'est pas configurée.");
            return;
        }

        ConfigurationSection sec = root.getConfigurationSection(perkId);
        if (sec == null || !sec.getBoolean("enabled", true)) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cCe perk de prestige est invalide ou désactivé.");
            return;
        }

        int maxLevel = sec.getInt("max-level", 1);
        int costTokens = sec.getInt("cost-tokens", 1);

        int currentLevel = profile.getPrestigePerkLevel(perkId);
        if (maxLevel > 0 && currentLevel >= maxLevel) {
            MessageUtil.send(player, "errors.prestige-perk-max-level");
            return;
        }

        long tokens = profile.getPrestigeTokens();
        if (tokens < costTokens) {
            MessageUtil.send(player, "errors.not-enough-prestige-tokens");
            return;
        }

        profile.addPrestigeTokens(-costTokens);

        int newLevel = currentLevel + 1;
        if (maxLevel > 0 && newLevel > maxLevel) {
            newLevel = maxLevel;
        }
        profile.setPrestigePerkLevel(perkId, newLevel);

        String displayName = sec.getString("display-name", perkId);
        if (displayName == null || displayName.isEmpty()) {
            displayName = perkId;
        }

        MessageUtil.sendPlain(player,
                MessageUtil.getPrefix() + "&aTu as acheté &e" + displayName + " &a(niveau &e" + newLevel + "&a).");
    }

    public void attemptResetPrestigePerk(Player player, String perkId) {
        if (player == null || perkId == null || perkId.isEmpty()) {
            return;
        }

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cImpossible de charger ton profil, réessaie plus tard.");
            return;
        }

        FileConfiguration shopCfg = configManager.getPrestigeShopConfig();
        ConfigurationSection root = shopCfg.getConfigurationSection("prestige_shop.perks");
        if (root == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cLa boutique de prestige n'est pas configurée.");
            return;
        }

        ConfigurationSection sec = root.getConfigurationSection(perkId);
        if (sec == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cPerk inconnu: &e" + perkId);
            return;
        }

        int currentLevel = profile.getPrestigePerkLevel(perkId);
        if (currentLevel <= 0) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cTu n'as aucun niveau pour ce perk (&e" + perkId + "&c).");
            return;
        }

        profile.setPrestigePerkLevel(perkId, 0);

        String displayName = sec.getString("display-name", perkId);
        if (displayName == null || displayName.isEmpty()) {
            displayName = perkId;
        }

        MessageUtil.sendPlain(player,
                MessageUtil.getPrefix() + "&aLe perk &e" + displayName + " &aa été réinitialisé (&eniveau 0&a).");
    }
}
