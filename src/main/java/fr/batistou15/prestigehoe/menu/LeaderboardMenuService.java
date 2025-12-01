package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.leaderboard.LeaderboardService;
import fr.batistou15.prestigehoe.leaderboard.LeaderboardService.LeaderboardEntry;
import fr.batistou15.prestigehoe.menu.MenuManager.LeaderboardTemplateConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class LeaderboardMenuService {

    private final PrestigeHoePlugin plugin;
    private final MenuItemFactory itemFactory;
    private final MenuPlaceholderService placeholderService;
    private final LeaderboardService leaderboardService;

    // Slots d'affichage : pyramide
    private static final int[] DISPLAY_SLOTS = {
            13,
            21, 22, 23,
            29, 30, 31, 32, 33,
            37, 38, 39, 40, 41, 42, 43
    };

    // Template pour les cases vides (empty-slot-item dans leaderboards.yml)
    private final MenuIconConfig emptySlotIcon;
    private final String emptySlotName;
    private final List<String> emptySlotLore;

    public LeaderboardMenuService(PrestigeHoePlugin plugin,
                                  MenuItemFactory itemFactory,
                                  MenuPlaceholderService placeholderService,
                                  LeaderboardService leaderboardService) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.placeholderService = placeholderService;
        this.leaderboardService = leaderboardService;

        // Chargement du empty-slot-item depuis leaderboards.yml
        MenuIconConfig icon = null;
        String name = "&7#%lb_rank% &8Aucun joueur";
        List<String> lore = Collections.singletonList("&7Personne n'occupe encore ce rang.");

        try {
            File file = new File(plugin.getDataFolder(), "leaderboards.yml");
            if (file.exists()) {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                ConfigurationSection root = cfg.getConfigurationSection("leaderboards");
                if (root != null) {
                    ConfigurationSection emptySec = root.getConfigurationSection("empty-slot-item");
                    if (emptySec != null) {
                        ConfigurationSection iconSec = emptySec.getConfigurationSection("icon");
                        if (iconSec != null) {
                            String matName = iconSec.getString("material", "PLAYER_HEAD");
                            int cmd = iconSec.getInt("custom-model-data", 0);
                            String itemsAdderId = iconSec.getString("itemsadder-id", "");
                            String oraxenId = iconSec.getString("oraxen-id", "");
                            String skullOwner = iconSec.getString("skull-owner", "");
                            String skullTexture = iconSec.getString("skull-texture", "");

                            icon = new MenuIconConfig(matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture);
                        }

                        name = emptySec.getString("name", name);
                        List<String> loreTmp = emptySec.getStringList("lore");
                        if (loreTmp != null && !loreTmp.isEmpty()) {
                            lore = new ArrayList<>(loreTmp);
                        } else {
                            lore = Collections.emptyList();
                        }
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "[LeaderboardMenu] Erreur lors du chargement de empty-slot-item", ex);
        }

        if (icon == null) {
            icon = new MenuIconConfig("PLAYER_HEAD", 0, "", "", "", "");
        }

        this.emptySlotIcon = icon;
        this.emptySlotName = name;
        this.emptySlotLore = lore;
    }

    /**
     * Remplit le menu avec le leaderboard (type + page) sélectionné pour le joueur.
     *
     * @param inv       inventaire du menu
     * @param holder    holder
     * @param viewer    joueur qui ouvre le menu
     * @param templates map typeId -> template (prestige, essence, crops_total, ...)
     */
    public void fillLeaderboards(Inventory inv,
                                 MenuHolder holder,
                                 Player viewer,
                                 Map<String, LeaderboardTemplateConfig> templates) {
        if (inv == null || viewer == null || templates == null || templates.isEmpty()) {
            return;
        }

        MenuManager menuManager = plugin.getMenuManager();

        // 1) type sélectionné
        String selectedType = menuManager.getSelectedLeaderboardType(viewer);
        if (selectedType == null || selectedType.isEmpty()) {
            selectedType = "prestige";
        }
        String selectedKey = selectedType.toLowerCase(Locale.ROOT);

        // 2) template correspondant
        LeaderboardTemplateConfig tpl = templates.get(selectedKey);
        if (tpl == null) {
            tpl = templates.values().iterator().next();
            selectedType = tpl.getTypeId();
            selectedKey = selectedType.toLowerCase(Locale.ROOT);
        }

        // 3) données du top complet pour ce type
        List<LeaderboardEntry> allEntries = leaderboardService.getTop(selectedType);
        String typeDisplayName = leaderboardService.getTypeDisplayName(selectedType);

        // 4) pagination
        int page = menuManager.getSelectedLeaderboardPage(viewer);
        int maxPages = leaderboardService.getMaxPages();
        if (maxPages <= 0) maxPages = 1;
        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        int perPage = DISPLAY_SLOTS.length;
        int startIndex = (page - 1) * perPage;

        List<LeaderboardEntry> pageEntries;
        if (startIndex >= allEntries.size()) {
            pageEntries = Collections.emptyList();
        } else {
            int endIndex = Math.min(startIndex + perPage, allEntries.size());
            pageEntries = allEntries.subList(startIndex, endIndex);
        }

        // 5) on remplit les slots (avec joueur ou case vide)
        for (int i = 0; i < DISPLAY_SLOTS.length; i++) {
            int slot = DISPLAY_SLOTS[i];
            if (slot < 0 || slot >= inv.getSize()) {
                continue;
            }

            int rank = startIndex + i + 1;

            Map<String, String> ph = new HashMap<>(placeholderService.buildBasePlaceholders(viewer));
            ph.put("%lb_type%", selectedType);
            ph.put("%lb_display_name%", typeDisplayName != null ? typeDisplayName : selectedType);
            ph.put("%lb_rank%", String.valueOf(rank));
            ph.put("%lb_page%", String.valueOf(page));
            ph.put("%lb_max_pages%", String.valueOf(maxPages));

            ItemStack stack;

            if (i < pageEntries.size()) {
                // Il y a un joueur pour ce rang
                LeaderboardEntry entry = pageEntries.get(i);
                if (entry == null) continue;

                String playerName = entry.getPlayerName();
                String valueFormatted = leaderboardService.formatValue(selectedType, entry.getValue());

                ph.put("%lb_player%", playerName);
                ph.put("%lb_player_name%", playerName);
                ph.put("%lb_player_uuid%", entry.getPlayerUuid().toString());
                ph.put("%lb_value%", valueFormatted);
                ph.put("%lb_value_raw%", String.valueOf(entry.getValue()));

                String idLower = selectedType.toLowerCase(Locale.ROOT);
                if ("prestige".equals(idLower)) {
                    ph.put("%lb_prestige%", valueFormatted);
                } else if ("essence".equals(idLower)) {
                    ph.put("%lb_essence%", valueFormatted);
                } else if ("crops_total".equals(idLower)) {
                    ph.put("%lb_crops_total%", valueFormatted);
                }

                MenuIconConfig iconCfg = tpl.getIconConfig();
                MenuItemConfig itemCfg = new MenuItemConfig(
                        "lb_" + idLower + "_" + rank,
                        slot,
                        iconCfg,
                        tpl.getName(),
                        tpl.getLore(),
                        MenuAction.NONE,
                        null
                );

                stack = itemFactory.buildItem(itemCfg, viewer, ph);
            } else {
                // Pas de joueur à ce rang → item vide configurable
                MenuItemConfig emptyCfg = new MenuItemConfig(
                        "lb_empty_" + selectedKey + "_" + rank,
                        slot,
                        emptySlotIcon,
                        emptySlotName,
                        emptySlotLore,
                        MenuAction.NONE,
                        null
                );
                stack = itemFactory.buildItem(emptyCfg, viewer, ph);
            }

            if (stack != null) {
                inv.setItem(slot, stack);
            }
        }
    }
}
