package fr.batistou15.prestigehoe.farm;

import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class HoeDisplayService {

    private final ConfigManager configManager;
    private final HoeItemManager hoeItemManager;
    private final PlayerDataManager playerDataManager;
    private final HoeProgressionService progressionService;

    public HoeDisplayService(ConfigManager configManager,
                             HoeItemManager hoeItemManager,
                             PlayerDataManager playerDataManager,
                             HoeProgressionService progressionService) {
        this.configManager = configManager;
        this.hoeItemManager = hoeItemManager;
        this.playerDataManager = playerDataManager;
        this.progressionService = progressionService;
    }

    public void updateHoeDisplay(Player player, HoeData hoe) {
        if (hoe == null || player == null) return;

        PlayerProfile profile = playerDataManager.getProfile(player);

        int level = hoe.getLevel();
        double currentXp = hoe.getXp();
        double requiredXp = progressionService.getXpRequiredForLevel(hoe);
        int prestige = hoe.getPrestige();
        int maxLevel = progressionService.getMaxHoeLevel(hoe);

        long totalCropsBroken = (profile != null) ? profile.getTotalCropsBroken() : 0L;
        double totalMoneyEarned = (profile != null) ? profile.getTotalMoneyEarned() : 0.0;
        double totalEssenceEarned = (profile != null) ? profile.getTotalEssenceEarned() : 0.0;

        FileConfiguration messages = configManager.getMessagesConfig();

        String rawTitle = messages.getString(
                "hoe.display-name",
                "&6PrestigeHoe &7(&eLvl %level%&7)"
        );

        List<String> rawLore = messages.getStringList("hoe.lore");
        if (rawLore == null || rawLore.isEmpty()) {
            rawLore = Arrays.asList(
                    "&7Propriétaire: &e%player%",
                    "&7Niveau: &e%level% &7/&e%max_level%",
                    "&7Prestige de la houe: &d%prestige%",
                    "&7XP: &e%xp_current% &7/&e%xp_required%",
                    "&7Crops cassées: &e%crop_break%",
                    "&7Argent farmé: &e%money_farm%",
                    "&7Essence farmée: &e%essence_farm%",
                    "&8----------------",
                    "%enchant_lines%"
            );
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("%player%", player.getName());
        ph.put("%level%", String.valueOf(level));
        ph.put("%max_level%", String.valueOf(maxLevel));
        ph.put("%prestige%", String.valueOf(prestige));

        ph.put("%xp%", NumberFormatUtil.formatShort(currentXp));
        ph.put("%xp_current%", NumberFormatUtil.formatShort(currentXp));
        ph.put("%xp_required%", NumberFormatUtil.formatShort(requiredXp));

        ph.put("%crop_break%", NumberFormatUtil.formatShort(totalCropsBroken));
        ph.put("%money_farm%", NumberFormatUtil.formatShort(totalMoneyEarned));
        ph.put("%essence_farm%", NumberFormatUtil.formatShort(totalEssenceEarned));

        ph.put("%fortune_level%", String.valueOf(hoe.getEnchantLevel("fortune", 0)));
        ph.put("%autosell_level%", String.valueOf(hoe.getEnchantLevel("autosell", 0)));
        ph.put("%essence_booster_level%", String.valueOf(hoe.getEnchantLevel("essence_booster", 0)));
        ph.put("%money_booster_level%", String.valueOf(hoe.getEnchantLevel("money_booster", 0)));
        ph.put("%token_finder_level%", String.valueOf(hoe.getEnchantLevel("token_finder", 0)));

        String displayName = applyPlaceholdersAndColor(rawTitle, ph);

        List<String> enchantLoreLines = buildEnchantLoreLines(hoe);

        List<String> finalLore = new ArrayList<>();
        for (String line : rawLore) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("%enchant_lines%")) {
                finalLore.addAll(enchantLoreLines);
                continue;
            }
            finalLore.add(applyPlaceholdersAndColor(line, ph));
        }

        var inv = player.getInventory();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            if (!hoeItemManager.isPrestigeHoe(item)) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            meta.setDisplayName(displayName);
            meta.setLore(finalLore);
            item.setItemMeta(meta);
        }
    }

    private String applyPlaceholdersAndColor(String input, Map<String, String> ph) {
        if (input == null) return "";
        String result = input;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return MessageUtil.color(result);
    }

    private List<String> buildEnchantLoreLines(HoeData hoe) {
        List<String> lines = new ArrayList<>();

        if (hoe == null) {
            return lines;
        }

        FileConfiguration enchCfg = configManager.getEnchantsConfig();
        FileConfiguration messages = configManager.getMessagesConfig();

        String format = messages.getString(
                "hoe.enchant-lines.format",
                "&7%enchant_name%: &e%level%"
        );

        ConfigurationSection enchSec = enchCfg.getConfigurationSection("enchants");
        if (enchSec == null) {
            return lines;
        }

        for (String id : enchSec.getKeys(false)) {
            int lvl = hoe.getEnchantLevel(id, 0);
            if (lvl <= 0) continue;

            String path = "enchants." + id + ".display-name";
            String displayName = enchCfg.getString(path, id);
            if (displayName == null || displayName.isEmpty()) {
                displayName = id;
            }

            String rawLine = format
                    .replace("%enchant_id%", id)
                    .replace("%enchant_name%", displayName)
                    .replace("%level%", String.valueOf(lvl));

            lines.add(MessageUtil.color(rawLine));
        }

        return lines;
    }
}
