package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class PrestigeShopMenuService {

    private final PrestigeHoePlugin plugin;
    private final MenuItemFactory itemFactory;
    private final MenuPlaceholderService placeholderService;

    public PrestigeShopMenuService(PrestigeHoePlugin plugin,
                                   MenuItemFactory itemFactory,
                                   MenuPlaceholderService placeholderService) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.placeholderService = placeholderService;
    }

    public void fillPrestigeShopIcons(Inventory inv,
                                      MenuHolder holder,
                                      Player viewer,
                                      MenuItemConfig template) {
        if (viewer == null) return;

        FileConfiguration shopCfg = plugin.getConfigManager().getPrestigeShopConfig();
        ConfigurationSection root = shopCfg.getConfigurationSection("prestige_shop.perks");
        if (root == null) {
            plugin.getLogger().warning("[PrestigeShopMenu] Section 'prestige_shop.perks' introuvable dans prestige_shop.yml");
            return;
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(viewer);
        HoeData hoe = profile != null ? profile.getHoeData() : null;
        if (hoe == null) {
            hoe = new HoeData();
        }

        boolean anyPerk = false;

        for (String perkId : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(perkId);
            if (sec == null) continue;
            if (!sec.getBoolean("enabled", true)) continue;

            anyPerk = true;

            int slot = sec.getInt("slot", -1);
            if (slot < 0 || slot >= inv.getSize()) continue;

            // Icône : override ou template
            MenuIconConfig iconCfg = template.getIcon();
            ConfigurationSection iconSec = sec.getConfigurationSection("icon");
            if (iconSec != null) {
                String matName = iconSec.getString("material", iconCfg.getMaterial());
                int cmd = iconSec.getInt("custom-model-data", iconCfg.getCustomModelData());
                String itemsAdderId = iconSec.getString("itemsadder-id", iconCfg.getItemsAdderId());
                String oraxenId = iconSec.getString("oraxen-id", iconCfg.getOraxenId());
                String skullOwner = iconSec.getString("skull-owner", iconCfg.getSkullOwner());
                String skullTexture = iconSec.getString("skull-texture", iconCfg.getSkullTexture());
                iconCfg = new MenuIconConfig(matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture);
            }

            MenuItemConfig perPerkConfig = new MenuItemConfig(
                    "perk_" + perkId,
                    slot,
                    iconCfg,
                    template.getName(),
                    template.getLore(),
                    MenuAction.NONE,
                    null
            );

            Map<String, String> extra = new HashMap<>();
            extra.put("%perk_id%", perkId);

            String displayName = sec.getString("display-name", perkId);
            extra.put("%perk_display_name%", displayName != null ? displayName : perkId);

            int currentLevel = profile != null ? profile.getPrestigePerkLevel(perkId) : 0;
            int maxLevel = sec.getInt("max-level", 1);
            extra.put("%perk_level_current%", String.valueOf(currentLevel));
            extra.put("%perk_level_max%", maxLevel > 0 ? String.valueOf(maxLevel) : "∞");

            int costTokens = sec.getInt("cost-tokens", 1);
            extra.put("%perk_cost_tokens%", String.valueOf(costTokens));

            // Description
            var descLines = sec.getStringList("description");
            if (descLines != null && !descLines.isEmpty()) {
                extra.put("%perk_description_lines%", String.join("\n", descLines));
            } else {
                extra.put("%perk_description_lines%", "");
            }

            // Valeurs (en %)
            double valuePerLevel = sec.getDouble("value-per-level", 0.0D); // ex: 0.05 = +5% par niveau
            double totalValue = valuePerLevel * currentLevel;
            double nextValue = (maxLevel <= 0 || currentLevel < maxLevel)
                    ? valuePerLevel * (currentLevel + 1)
                    : totalValue;

            extra.put("%perk_value_per_level_percent%", placeholderService.formatPercent(valuePerLevel));
            extra.put("%perk_value_total_percent%", placeholderService.formatPercent(totalValue));
            extra.put("%perk_value_next_percent%", placeholderService.formatPercent(nextValue));

            // Statut
            String status;
            if (maxLevel > 0 && currentLevel >= maxLevel) {
                status = "§aDébloqué (max)";
            } else if (currentLevel > 0) {
                status = "§aDébloqué (niv " + currentLevel + ")";
            } else {
                status = "§cNon débloqué";
            }
            extra.put("%perk_status%", status);

            ItemStack stack = itemFactory.buildItem(perPerkConfig, viewer, extra);
            inv.setItem(slot, stack);

            // Pour pouvoir gérer le clic dans MenuListener
            holder.setPrestigePerkForSlot(slot, perkId);
        }

        if (!anyPerk) {
            plugin.getLogger().warning("[PrestigeShopMenu] Aucun perk trouvé dans 'prestige_shop.perks' (tous disabled ?)");
        }
    }
}
