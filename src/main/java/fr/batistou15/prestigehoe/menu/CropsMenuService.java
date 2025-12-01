package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.menu.MenuManager.CropTemplateConfig;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CropsMenuService {

    private final PrestigeHoePlugin plugin;
    private final MenuItemFactory itemFactory;
    private final MenuPlaceholderService placeholderService;

    public CropsMenuService(PrestigeHoePlugin plugin,
                            MenuItemFactory itemFactory,
                            MenuPlaceholderService placeholderService) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.placeholderService = placeholderService;
    }

    /**
     * Remplit le menu "crops" à partir de crops.yml et du template défini dans le menu.
     *
     * Placeholders utilisables dans le template :
     *  - %crop_id%
     *  - %crop_name% / %crop_display_name%
     *  - %crop_material%
     *  - %crop_category%
     *
     *  - %crop_price%
     *  - %crop_essence%
     *  - %crop_xp_hoe%
     *  - %crop_xp_player%
     *
     *  - %crop_req_hoe_level%
     *  - %crop_req_prestige%
     *
     *  - %crop_status% (Disponible / Bloquée en fonction du niveau/prestige du joueur)
     */
    public void fillCropIcons(Inventory inv,
                              MenuHolder holder,
                              Player player,
                              CropTemplateConfig tpl) {

        if (player == null || tpl == null) {
            return;
        }

        // Profil + hoe pour récupérer le niveau / prestige
        PlayerDataManager dataMgr = plugin.getPlayerDataManager();
        if (dataMgr == null) return;

        PlayerProfile profile = dataMgr.getProfile(player);
        if (profile == null) return;

        HoeData hoe = profile.getHoeData();
        if (hoe == null) {
            hoe = new HoeData();
        }

        int hoeLevel = hoe.getLevel();
        int prestige = hoe.getPrestige();

        // Chargement de crops.yml
        File cropsFile = new File(plugin.getDataFolder(), "crops.yml");
        if (!cropsFile.exists()) {
            plugin.getLogger().warning("[CropsMenu] crops.yml introuvable, impossible de remplir le menu des cultures.");
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(cropsFile);
        ConfigurationSection root = cfg.getConfigurationSection("crops");
        if (root == null) {
            plugin.getLogger().warning("[CropsMenu] Section 'crops' absente dans crops.yml");
            return;
        }

        int baseSlot = tpl.getStartSlot();
        int perRow = Math.max(1, tpl.getPerRow());

        int index = 0;

        for (String cropId : root.getKeys(false)) {
            ConfigurationSection cSec = root.getConfigurationSection(cropId);
            if (cSec == null) continue;

            String displayName = cSec.getString("display-name", cropId);
            String materialName = cSec.getString("material", "WHEAT");
            String category = cSec.getString("category", "default");

            double price = cSec.getDouble("price", 0.0D);
            double essence = cSec.getDouble("essence", 0.0D);
            double xpHoe = cSec.getDouble("xp-hoe", 0.0D);
            double xpPlayer = cSec.getDouble("xp-player", 0.0D);

            // Requirements
            int reqHoeLevel = 0;
            int reqPrestige = 0;

            ConfigurationSection reqSec = cSec.getConfigurationSection("requirements");
            if (reqSec != null) {
                reqHoeLevel = reqSec.getInt("hoe-level-min", 0);
                reqPrestige = reqSec.getInt("prestige-min", 0);
            }

            boolean meetsHoe = hoeLevel >= reqHoeLevel;
            boolean meetsPrestige = prestige >= reqPrestige;

            String status;
            if (meetsHoe && meetsPrestige) {
                status = "§aDisponible";
            } else {
                status = "§cBloquée";
            }

            // Calcul slot en grille
            int rowOffset = index / perRow;
            int colOffset = index % perRow;
            int slot = baseSlot + rowOffset * 9 + colOffset;
            index++;

            if (slot < 0 || slot >= inv.getSize()) {
                continue;
            }

            // Placeholders pour cet item
            Map<String, String> ph = new HashMap<>(placeholderService.buildBasePlaceholders(player));

            ph.put("%crop_id%", cropId);
            ph.put("%crop_name%", displayName);
            ph.put("%crop_display_name%", displayName);
            ph.put("%crop_material%", materialName);
            ph.put("%crop_category%", category);

            ph.put("%crop_price%", formatNumber(price));
            ph.put("%crop_essence%", formatNumber(essence));
            ph.put("%crop_xp_hoe%", formatNumber(xpHoe));
            ph.put("%crop_xp_player%", formatNumber(xpPlayer));

            ph.put("%crop_req_hoe_level%", String.valueOf(reqHoeLevel));
            ph.put("%crop_req_prestige%", String.valueOf(reqPrestige));
            ph.put("%crop_status%", status);

            // Base icon depuis le template
            MenuIconConfig iconCfg = tpl.getIconConfig();
            if (iconCfg == null) {
                // fallback si jamais : on se base sur la matière de la crop
                iconCfg = new MenuIconConfig(
                        materialName,
                        0,
                        "",
                        "",
                        "",
                        ""
                );
            }

            MenuItemConfig itemCfg = new MenuItemConfig(
                    "crop_" + cropId.toLowerCase(Locale.ROOT),
                    slot,
                    iconCfg,
                    tpl.getName(),
                    tpl.getLore(),
                    MenuAction.NONE,
                    null
            );

            ItemStack stack = itemFactory.buildItem(itemCfg, player, ph);
            if (stack == null) continue;

            // On force le visuel de la culture (au cas où le template utilise un autre material)
            Material mat = Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
            if (mat != null && mat.isItem()) {
                stack.setType(mat);
            }

            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                // Si un jour tu veux ajouter un tag PDC crop_id pour les clics, c'est ici.
                stack.setItemMeta(meta);
            }

            inv.setItem(slot, stack);
        }
    }

    private String formatNumber(double value) {
        if (value <= 0.0D) {
            return "0";
        }
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
