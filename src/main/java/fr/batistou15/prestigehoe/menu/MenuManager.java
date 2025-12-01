package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.leaderboard.LeaderboardService;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public class MenuManager {

    private final PrestigeHoePlugin plugin;

    private final Map<String, MenuConfig> menus = new HashMap<>();
    private final Map<String, MenuItemConfig> enchantMenuTemplates = new HashMap<>();
    private final Map<String, MenuItemConfig> prestigeShopMenuTemplates = new HashMap<>();

    // SKINS
    private final Map<String, SkinTemplateConfig> skinMenuTemplates = new HashMap<>();

    // CROPS
    private final Map<String, CropTemplateConfig> cropMenuTemplates = new HashMap<>();

    // LEADERBOARDS : menu-id -> (typeId -> template)
    private final Map<String, Map<String, LeaderboardTemplateConfig>> leaderboardMenuTemplates = new HashMap<>();

    private final Map<UUID, String> selectedEnchantByPlayer = new HashMap<>();

    // Type de leaderboard sélectionné par joueur (prestige / essence / crops_total)
    private final Map<UUID, String> selectedLeaderboardTypeByPlayer = new HashMap<>();

    // Page de leaderboard sélectionnée par joueur (1..maxPages)
    private final Map<UUID, Integer> selectedLeaderboardPageByPlayer = new HashMap<>();

    private String upgradeMenuId = "upgrade";
    private String disenchantMenuId = "disenchant";

    private final MenuPlaceholderService placeholderService;
    private final MenuItemFactory itemFactory;
    private final EnchantMenuService enchantMenuService;
    private final PrestigeShopMenuService prestigeShopMenuService;

    private final SkinMenuService skinMenuService;
    private final CropsMenuService cropsMenuService;

    private final LeaderboardService leaderboardService;
    private final LeaderboardMenuService leaderboardMenuService;

    public MenuManager(PrestigeHoePlugin plugin) {
        this.plugin = plugin;

        this.placeholderService = new MenuPlaceholderService(plugin);
        SkullUtil skullUtil = new SkullUtil(plugin);
        this.itemFactory = new MenuItemFactory(plugin, placeholderService, skullUtil);
        this.enchantMenuService = new EnchantMenuService(plugin, itemFactory);
        this.prestigeShopMenuService = new PrestigeShopMenuService(plugin, itemFactory, placeholderService);

        // SKINS
        this.skinMenuService = new SkinMenuService(
                plugin,
                plugin.getSkinManager(),
                itemFactory,
                placeholderService
        );

        // CROPS
        this.cropsMenuService = new CropsMenuService(
                plugin,
                itemFactory,
                placeholderService
        );

        // LEADERBOARDS
        this.leaderboardService = new LeaderboardService(plugin);
        this.leaderboardMenuService = new LeaderboardMenuService(
                plugin,
                itemFactory,
                placeholderService,
                leaderboardService
        );

        reloadMenus();
    }

    /**
     * Recharge tous les menus depuis plugins/PrestigeHoe/menus/*.yml
     */
    public void reloadMenus() {
        menus.clear();
        enchantMenuTemplates.clear();
        prestigeShopMenuTemplates.clear();
        skinMenuTemplates.clear();
        cropMenuTemplates.clear();
        leaderboardMenuTemplates.clear();
        selectedEnchantByPlayer.clear();
        selectedLeaderboardTypeByPlayer.clear();
        selectedLeaderboardPageByPlayer.clear();
        upgradeMenuId = "upgrade";
        disenchantMenuId = "disenchant";

        File menusFolder = new File(plugin.getDataFolder(), "menus");
        if (!menusFolder.exists() && !menusFolder.mkdirs()) {
            plugin.getLogger().warning("[Menu] Impossible de créer le dossier menus/");
            return;
        }

        File[] files = menusFolder.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                String id = cfg.getString("id");
                if (id == null || id.isEmpty()) {
                    plugin.getLogger().warning("[Menu] Fichier " + file.getName() + " sans id, ignoré.");
                    continue;
                }

                String lowerId = id.toLowerCase(Locale.ROOT);

                if (file.getName().equalsIgnoreCase("UpgradeMenu.yml")) {
                    upgradeMenuId = lowerId;
                }
                if (file.getName().equalsIgnoreCase("DisenchantMenu.yml")) {
                    disenchantMenuId = lowerId;
                }

                String title = cfg.getString("title", id);
                int size = cfg.getInt("size", 54);
                if (size <= 0 || size % 9 != 0) {
                    plugin.getLogger().warning("[Menu] Taille invalide pour " + id + " (" + size + "), fallback 54.");
                    size = 54;
                }

                Map<Integer, MenuItemConfig> items = new HashMap<>();

                // -------- items statiques --------
                ConfigurationSection itemsSection = cfg.getConfigurationSection("items");
                if (itemsSection != null) {
                    for (String itemKey : itemsSection.getKeys(false)) {
                        ConfigurationSection itemSec = itemsSection.getConfigurationSection(itemKey);
                        if (itemSec == null) continue;

                        List<Integer> slots = new ArrayList<>();
                        if (itemSec.contains("slot")) {
                            slots.add(itemSec.getInt("slot"));
                        }
                        if (itemSec.contains("slots")) {
                            slots.addAll(itemSec.getIntegerList("slots"));
                        }
                        if (slots.isEmpty()) continue;

                        ConfigurationSection iconSec = itemSec.getConfigurationSection("icon");
                        if (iconSec == null) {
                            plugin.getLogger().warning("[Menu] Item " + itemKey + " dans " + file.getName() + " sans section icon.");
                            continue;
                        }

                        String matName = iconSec.getString("material", "BARRIER");
                        int cmd = iconSec.getInt("custom-model-data", 0);
                        String itemsAdderId = iconSec.getString("itemsadder-id", "");
                        String oraxenId = iconSec.getString("oraxen-id", "");
                        String skullOwner = iconSec.getString("skull-owner", "");
                        String skullTexture = iconSec.getString("skull-texture", "");

                        MenuIconConfig iconConfig = new MenuIconConfig(
                                matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture
                        );

                        String name = itemSec.getString("name", "");
                        List<String> lore = itemSec.getStringList("lore");

                        String actionRaw = itemSec.getString("action", "NONE");
                        MenuAction action = MenuAction.NONE;
                        String actionValue = null;

                        if (actionRaw != null) {
                            String upper = actionRaw.toUpperCase(Locale.ROOT);

                            if (upper.startsWith("OPEN_MENU")) {
                                action = MenuAction.OPEN_MENU;
                                int idx = actionRaw.indexOf(':');
                                if (idx != -1 && idx + 1 < actionRaw.length()) {
                                    actionValue = actionRaw.substring(idx + 1).trim();
                                }

                            } else if (upper.startsWith("UPGRADE_ENCHANT")) {
                                action = MenuAction.UPGRADE_ENCHANT;
                                int idx = actionRaw.indexOf(':');
                                if (idx != -1 && idx + 1 < actionRaw.length()) {
                                    actionValue = actionRaw.substring(idx + 1).trim();
                                }

                            } else if (upper.startsWith("DISENCHANT_ENCHANT")) {
                                action = MenuAction.DISENCHANT_ENCHANT;
                                int idx = actionRaw.indexOf(':');
                                if (idx != -1 && idx + 1 < actionRaw.length()) {
                                    actionValue = actionRaw.substring(idx + 1).trim();
                                }

                            } else if (upper.equals("OPEN_DISENCHANT_MENU")) {
                                action = MenuAction.OPEN_DISENCHANT_MENU;

                            } else if (upper.equals("PRESTIGE_UP")) {
                                action = MenuAction.PRESTIGE_UP;

                            } else if (upper.equals("PRESTIGE_SHOP_BUY")) {
                                action = MenuAction.PRESTIGE_SHOP_BUY;

                            } else if (upper.startsWith("OPEN_LEADERBOARD")) {
                                // ex: OPEN_LEADERBOARD:prestige
                                action = MenuAction.OPEN_LEADERBOARD;
                                int idx = actionRaw.indexOf(':');
                                if (idx != -1 && idx + 1 < actionRaw.length()) {
                                    actionValue = actionRaw.substring(idx + 1).trim();
                                }

                            } else if (upper.equals("LEADERBOARD_PREVIOUS_PAGE")) {
                                action = MenuAction.LEADERBOARD_PREVIOUS_PAGE;

                            } else if (upper.equals("LEADERBOARD_NEXT_PAGE")) {
                                action = MenuAction.LEADERBOARD_NEXT_PAGE;

                            } else if (upper.equals("TOGGLE_RECAP")) {
                                action = MenuAction.TOGGLE_RECAP;

                            } else if (upper.equals("TOGGLE_NOTIF_CHAT")) {
                                action = MenuAction.TOGGLE_NOTIF_CHAT;

                            } else if (upper.equals("TOGGLE_NOTIF_ENCHANT_PROC")) {
                                action = MenuAction.TOGGLE_NOTIF_ENCHANT_PROC;

                            } else if (upper.equals("TOGGLE_NOTIF_LEVELUP")) {
                                action = MenuAction.TOGGLE_NOTIF_LEVELUP;

                            } else if (upper.equals("TOGGLE_NOTIF_ACTIONBAR")) {
                                action = MenuAction.TOGGLE_NOTIF_ACTIONBAR;

                            } else if (upper.equals("TOGGLE_NOTIF_TITLE")) {
                                action = MenuAction.TOGGLE_NOTIF_TITLE;

                            } else if (upper.equals("TOGGLE_ENCHANT_NOTIF")) {
                                action = MenuAction.TOGGLE_ENCHANT_NOTIF;

                            } else if (upper.equals("CLOSE")) {
                                action = MenuAction.CLOSE;

                            } else {
                                action = MenuAction.NONE;
                            }
                        }

                        for (int slot : slots) {
                            if (slot < 0 || slot >= size) continue;
                            MenuItemConfig mic = new MenuItemConfig(
                                    itemKey,
                                    slot,
                                    iconConfig,
                                    name,
                                    lore,
                                    action,
                                    actionValue
                            );
                            items.put(slot, mic);
                        }
                    }
                }

                // -------- Template enchants --------
                ConfigurationSection enchantsSection = cfg.getConfigurationSection("enchants");
                if (enchantsSection != null && enchantsSection.getBoolean("auto-fill-from-enchants-config", false)) {
                    ConfigurationSection templateSec = enchantsSection.getConfigurationSection("template");
                    if (templateSec != null) {
                        ConfigurationSection iconSecTpl = templateSec.getConfigurationSection("icon");
                        if (iconSecTpl != null) {
                            String matName = iconSecTpl.getString("material", "ENCHANTED_BOOK");
                            int cmd = iconSecTpl.getInt("custom-model-data", 0);
                            String itemsAdderId = iconSecTpl.getString("itemsadder-id", "");
                            String oraxenId = iconSecTpl.getString("oraxen-id", "");
                            String skullOwner = iconSecTpl.getString("skull-owner", "");
                            String skullTexture = iconSecTpl.getString("skull-texture", "");

                            MenuIconConfig iconCfg = new MenuIconConfig(
                                    matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture
                            );

                            String tplName = templateSec.getString("name", "");
                            List<String> tplLore = templateSec.getStringList("lore");

                            MenuItemConfig templateItem = new MenuItemConfig(
                                    "enchant_template",
                                    -1,
                                    iconCfg,
                                    tplName,
                                    tplLore,
                                    MenuAction.NONE,
                                    null
                            );

                            enchantMenuTemplates.put(lowerId, templateItem);
                        }
                    }
                }

                // -------- Template Prestige-Shop --------
                ConfigurationSection pShopSection = cfg.getConfigurationSection("prestige-shop");
                if (pShopSection != null && pShopSection.getBoolean("auto-fill-from-prestige-shop-config", false)) {
                    ConfigurationSection templateSec = pShopSection.getConfigurationSection("template");
                    if (templateSec != null) {
                        ConfigurationSection iconSecTpl = templateSec.getConfigurationSection("icon");
                        if (iconSecTpl != null) {
                            String matName = iconSecTpl.getString("material", "EMERALD");
                            int cmd = iconSecTpl.getInt("custom-model-data", 0);
                            String itemsAdderId = iconSecTpl.getString("itemsadder-id", "");
                            String oraxenId = iconSecTpl.getString("oraxen-id", "");
                            String skullOwner = iconSecTpl.getString("skull-owner", "");
                            String skullTexture = iconSecTpl.getString("skull-texture", "");

                            MenuIconConfig iconCfg = new MenuIconConfig(
                                    matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture
                            );

                            String tplName = templateSec.getString("name", "");
                            List<String> tplLore = templateSec.getStringList("lore");

                            MenuItemConfig templateItem = new MenuItemConfig(
                                    "prestige_perk_template",
                                    -1,
                                    iconCfg,
                                    tplName,
                                    tplLore,
                                    MenuAction.NONE,
                                    null
                            );

                            prestigeShopMenuTemplates.put(lowerId, templateItem);
                        }
                    }
                }

                // -------- Layout + templates spéciaux --------
                ConfigurationSection layoutSec = cfg.getConfigurationSection("layout");
                ConfigurationSection skinTplSec = cfg.getConfigurationSection("skin-template");
                ConfigurationSection cropTplSec = cfg.getConfigurationSection("crop-template");
                ConfigurationSection lbTplRoot = cfg.getConfigurationSection("leaderboard-template");

                // SKINS
                if (layoutSec != null
                        && layoutSec.getBoolean("auto-fill-from-skins-config", false)
                        && skinTplSec != null) {

                    int startSlot = layoutSec.getInt("start-slot", 10);
                    int perRow = layoutSec.getInt("per-row", 7);

                    ConfigurationSection iconSecTpl = skinTplSec.getConfigurationSection("icon");
                    if (iconSecTpl != null) {
                        String matName = iconSecTpl.getString("material", "DIAMOND_HOE");
                        int cmd = iconSecTpl.getInt("custom-model-data", 0);
                        String itemsAdderId = iconSecTpl.getString("itemsadder-id", "");
                        String oraxenId = iconSecTpl.getString("oraxen-id", "");
                        String skullOwner = iconSecTpl.getString("skull-owner", "");
                        String skullTexture = iconSecTpl.getString("skull-texture", "");

                        MenuIconConfig iconCfg = new MenuIconConfig(
                                matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture
                        );

                        String tplName = skinTplSec.getString("name", "&f%skin_display_name%");
                        List<String> tplLore = skinTplSec.getStringList("lore");

                        SkinTemplateConfig tpl = new SkinTemplateConfig(
                                iconCfg,
                                tplName,
                                tplLore,
                                startSlot,
                                perRow
                        );
                        skinMenuTemplates.put(lowerId, tpl);
                    }
                }

                // CROPS
                if (layoutSec != null
                        && layoutSec.getBoolean("auto-fill-from-crops-config", false)
                        && cropTplSec != null) {

                    int startSlot = layoutSec.getInt("start-slot", 10);
                    int perRow = layoutSec.getInt("per-row", 7);

                    ConfigurationSection iconSecTpl = cropTplSec.getConfigurationSection("icon");
                    if (iconSecTpl != null) {
                        String matName = iconSecTpl.getString("material", "WHEAT");
                        int cmd = iconSecTpl.getInt("custom-model-data", 0);
                        String itemsAdderId = iconSecTpl.getString("itemsadder-id", "");
                        String oraxenId = iconSecTpl.getString("oraxen-id", "");
                        String skullOwner = iconSecTpl.getString("skull-owner", "");
                        String skullTexture = iconSecTpl.getString("skull-texture", "");

                        MenuIconConfig iconCfg = new MenuIconConfig(
                                matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture
                        );

                        String tplName = cropTplSec.getString("name", "&f%crop_display_name%");
                        List<String> tplLore = cropTplSec.getStringList("lore");

                        CropTemplateConfig tpl = new CropTemplateConfig(
                                iconCfg,
                                tplName,
                                tplLore,
                                startSlot,
                                perRow
                        );
                        cropMenuTemplates.put(lowerId, tpl);
                    }
                }

                // LEADERBOARDS
                if (layoutSec != null
                        && layoutSec.getBoolean("auto-fill-from-leaderboards-config", false)
                        && lbTplRoot != null) {

                    Map<String, LeaderboardTemplateConfig> lbMap = new HashMap<>();

                    for (String lbId : lbTplRoot.getKeys(false)) {
                        ConfigurationSection lbSec = lbTplRoot.getConfigurationSection(lbId);
                        if (lbSec == null) continue;

                        int startSlot = lbSec.getInt("start-slot", 13);
                        int perRow = lbSec.getInt("per-row", 1);

                        ConfigurationSection iconSecTpl = lbSec.getConfigurationSection("icon");
                        if (iconSecTpl == null) continue;

                        String matName = iconSecTpl.getString("material", "PLAYER_HEAD");
                        int cmd = iconSecTpl.getInt("custom-model-data", 0);
                        String itemsAdderId = iconSecTpl.getString("itemsadder-id", "");
                        String oraxenId = iconSecTpl.getString("oraxen-id", "");
                        String skullOwner = iconSecTpl.getString("skull-owner", "%lb_player_name%");
                        String skullTexture = iconSecTpl.getString("skull-texture", "");

                        MenuIconConfig iconCfg = new MenuIconConfig(
                                matName, cmd, itemsAdderId, oraxenId, skullOwner, skullTexture
                        );

                        String tplName = lbSec.getString("name", "&7#%lb_rank% %lb_player_name%");
                        List<String> tplLore = lbSec.getStringList("lore");

                        // lbId = "prestige", "essence", "crops_total"
                        LeaderboardTemplateConfig tpl = new LeaderboardTemplateConfig(
                                iconCfg,
                                tplName,
                                tplLore,
                                startSlot,
                                perRow,
                                lbId
                        );

                        lbMap.put(lbId.toLowerCase(Locale.ROOT), tpl);
                    }

                    if (!lbMap.isEmpty()) {
                        leaderboardMenuTemplates.put(lowerId, lbMap);
                    }
                }

                MenuConfig menuConfig = new MenuConfig(id, title, size, items);
                menus.put(lowerId, menuConfig);
                plugin.getLogger().info("[Menu] Chargé le menu '" + id + "' depuis " + file.getName());
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "[Menu] Erreur lors du chargement de " + file.getName(), e);
            }
        }
    }

    public MenuPlaceholderService getPlaceholderService() {
        return placeholderService;
    }

    public SkinMenuService getSkinMenuService() {
        return skinMenuService;
    }

    // ========= Ouverture de menu =========

    public void toggleEnchantProcNotification(Player player, String enchantId) {
        if (player == null || enchantId == null || enchantId.isEmpty()) return;

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) return;

        boolean currently = profile.isEnchantProcMessageEnabled(enchantId);
        profile.setEnchantProcMessageEnabled(enchantId, !currently);

        openEnchantUpgradeMenu(player, enchantId);
    }

    public void openMenu(Player player, String menuId) {
        openMenu(player, menuId, null);
    }

    public void openMenu(Player player,
                         String menuId,
                         Map<String, String> extraPlaceholders) {
        if (player == null) return;
        if (menuId == null || menuId.isEmpty()) {
            menuId = "main";
        }

        MenuConfig menu = menus.get(menuId.toLowerCase(Locale.ROOT));
        if (menu == null) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cMenu introuvable: §e" + menuId);
            return;
        }

        String rawTitle = menu.getTitle();
        if (rawTitle == null || rawTitle.isEmpty()) {
            rawTitle = menu.getId();
        }

        Map<String, String> titlePlaceholders = placeholderService.buildBasePlaceholders(player);
        if (extraPlaceholders != null && !extraPlaceholders.isEmpty()) {
            titlePlaceholders.putAll(extraPlaceholders);
        }
        rawTitle = placeholderService.applyPlaceholders(rawTitle, titlePlaceholders);
        String title = MessageUtil.color(rawTitle);

        MenuHolder holder = new MenuHolder(menu.getId());
        Inventory inv = Bukkit.createInventory(holder, menu.getSize(), title);
        holder.setInventory(inv);

        // 1) Items statiques
        for (Map.Entry<Integer, MenuItemConfig> entry : menu.getItems().entrySet()) {
            int slot = entry.getKey();
            MenuItemConfig cfg = entry.getValue();
            ItemStack stack = itemFactory.buildItem(cfg, player, extraPlaceholders);
            if (stack != null) {
                inv.setItem(slot, stack);
            }
        }

        String lowerId = menu.getId().toLowerCase(Locale.ROOT);

        // 2) Enchants
        MenuItemConfig enchantTemplate = enchantMenuTemplates.get(lowerId);
        if (enchantTemplate != null) {
            enchantMenuService.fillEnchantIcons(inv, holder, player, enchantTemplate);
        }

        // 3) Prestige Shop
        MenuItemConfig shopTemplate = prestigeShopMenuTemplates.get(lowerId);
        if (shopTemplate != null) {
            prestigeShopMenuService.fillPrestigeShopIcons(inv, holder, player, shopTemplate);
        }

        // 4) Skins
        SkinTemplateConfig skinTpl = skinMenuTemplates.get(lowerId);
        if (skinTpl != null) {
            skinMenuService.fillSkinIcons(inv, holder, player, skinTpl);
        }

        // 5) Crops
        CropTemplateConfig cropTpl = cropMenuTemplates.get(lowerId);
        if (cropTpl != null) {
            cropsMenuService.fillCropIcons(inv, holder, player, cropTpl);
        }

        // 6) Leaderboards
        Map<String, LeaderboardTemplateConfig> lbTpls = leaderboardMenuTemplates.get(lowerId);
        if (lbTpls != null && !lbTpls.isEmpty()) {
            leaderboardMenuService.fillLeaderboards(inv, holder, player, lbTpls);
        }

        player.openInventory(inv);
    }

    // ========= Toggles & gestion enchant =========

    public void toggleEnchantNotif(Player player, String enchantId) {
        if (player == null || enchantId == null || enchantId.isEmpty()) return;

        var profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) return;

        boolean before = profile.isEnchantProcMessageEnabled(enchantId);
        boolean after = !before;

        profile.setEnchantProcMessageEnabled(enchantId, after);

        String state = after ? "§aON" : "§cOFF";
        player.sendMessage("§6[PrestigeHoe] §eMessages de proc pour §d" + enchantId + "§e : " + state);
    }

    /**
     * Gestion centralisée des toggles du menu settings.
     */
    public void handleSettingsToggle(Player player, MenuAction action) {
        if (player == null || action == null) return;

        var profile = plugin.getPlayerDataManager().getProfile(player);
        if (profile == null) return;

        switch (action) {
            case TOGGLE_RECAP -> {
                boolean before = profile.isRecapEnabled();
                boolean after = !before;
                profile.setRecapEnabled(after);
                String state = after ? "§aON" : "§cOFF";
                player.sendMessage("§6[PrestigeHoe] §eRecap automatique : " + state);
            }
            case TOGGLE_NOTIF_CHAT -> {
                boolean before = profile.isChatNotificationsEnabled();
                boolean after = !before;
                profile.setChatNotificationsEnabled(after);
                String state = after ? "§aON" : "§cOFF";
                player.sendMessage("§6[PrestigeHoe] §eNotifications chat : " + state);
            }
            case TOGGLE_NOTIF_ENCHANT_PROC -> {
                boolean before = profile.isEnchantProcMessagesEnabledGlobal();
                boolean after = !before;
                profile.setEnchantProcMessagesEnabledGlobal(after);
                String state = after ? "§aON" : "§cOFF";
                player.sendMessage("§6[PrestigeHoe] §eMessages de proc d'enchant : " + state);
            }
            case TOGGLE_NOTIF_LEVELUP -> {
                boolean before = profile.isLevelUpMessageEnabled();
                boolean after = !before;
                profile.setLevelUpMessageEnabled(after);
                String state = after ? "§aON" : "§cOFF";
                player.sendMessage("§6[PrestigeHoe] §eMessages de level-up : " + state);
            }
            case TOGGLE_NOTIF_ACTIONBAR -> {
                boolean before = profile.isActionBarNotificationsEnabled();
                boolean after = !before;
                profile.setActionBarNotificationsEnabled(after);
                String state = after ? "§aON" : "§cOFF";
                player.sendMessage("§6[PrestigeHoe] §eNotifications ActionBar : " + state);
            }
            case TOGGLE_NOTIF_TITLE -> {
                boolean before = profile.isTitleNotificationsEnabled();
                boolean after = !before;
                profile.setTitleNotificationsEnabled(after);
                String state = after ? "§aON" : "§cOFF";
                player.sendMessage("§6[PrestigeHoe] §eNotifications Title : " + state);
            }
            default -> {
            }
        }
    }

    // ========= Accès statique aux menus / items =========

    public MenuItemConfig getItem(String menuId, int slot) {
        if (menuId == null) return null;
        MenuConfig menu = menus.get(menuId.toLowerCase(Locale.ROOT));
        if (menu == null) return null;
        return menu.getItem(slot);
    }

    public Map<String, MenuConfig> getMenus() {
        return Collections.unmodifiableMap(menus);
    }

    // ========= Gestion enchant sélectionné =========

    public void setSelectedEnchant(Player player, String enchantId) {
        if (player == null || enchantId == null || enchantId.isEmpty()) return;
        selectedEnchantByPlayer.put(player.getUniqueId(), enchantId.toLowerCase(Locale.ROOT));
    }

    public String getSelectedEnchant(Player player) {
        if (player == null) return null;
        return selectedEnchantByPlayer.get(player.getUniqueId());
    }

    // ========= Leaderboard sélectionné =========

    /**
     * Définit le type de leaderboard sélectionné pour un joueur.
     * typeId = "prestige", "essence", "crops_total"
     */
    public void setSelectedLeaderboardType(Player player, String typeId) {
        if (player == null || typeId == null || typeId.isEmpty()) return;
        selectedLeaderboardTypeByPlayer.put(player.getUniqueId(), typeId.toLowerCase(Locale.ROOT));
    }

    /**
     * Récupère le type de leaderboard sélectionné pour un joueur.
     * Si rien n'est encore défini, on retourne "prestige" par défaut.
     */
    public String getSelectedLeaderboardType(Player player) {
        if (player == null) return "prestige";
        return selectedLeaderboardTypeByPlayer.getOrDefault(player.getUniqueId(), "prestige");
    }

    /**
     * Page de leaderboard sélectionnée pour un joueur (1..maxPages).
     * Si rien n'est défini, on retourne 1.
     */
    public int getSelectedLeaderboardPage(Player player) {
        if (player == null) return 1;
        return selectedLeaderboardPageByPlayer.getOrDefault(player.getUniqueId(), 1);
    }

    /**
     * Alias rétro-compatible si tu l'utilises ailleurs.
     */
    public int getLeaderboardPage(Player player) {
        return getSelectedLeaderboardPage(player);
    }

    /**
     * Définit explicitement la page de leaderboard pour un joueur (clamp entre 1 et maxPages).
     */
    public void setSelectedLeaderboardPage(Player player, int page) {
        if (player == null) return;

        int maxPages = leaderboardService != null ? leaderboardService.getMaxPages() : 1;
        if (maxPages <= 0) maxPages = 1;

        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        selectedLeaderboardPageByPlayer.put(player.getUniqueId(), page);
    }

    /**
     * Change la page de leaderboard (delta = +1 / -1), avec wrap entre 1 et maxPages.
     */
    public void changeLeaderboardPage(Player player, int delta) {
        if (player == null || delta == 0) return;

        int current = getSelectedLeaderboardPage(player);
        int maxPages = leaderboardService != null ? leaderboardService.getMaxPages() : 1;
        if (maxPages <= 0) maxPages = 1;

        int newPage = current + delta;

        // Wrap : 1 -> -1 => maxPages, maxPages -> +1 => 1
        if (newPage < 1) {
            newPage = maxPages;
        } else if (newPage > maxPages) {
            newPage = 1;
        }

        selectedLeaderboardPageByPlayer.put(player.getUniqueId(), newPage);
    }

    /**
     * Méthode utilitaire appelée par le listener quand on clique sur
     * un bouton "OPEN_LEADERBOARD:xxx".
     *
     * Exemple dans LeaderboardsMenu.yml :
     *   action: "OPEN_LEADERBOARD:essence"
     */
    public void openLeaderboardMenu(Player player, String typeId) {
        if (player == null) return;
        if (typeId == null || typeId.isEmpty()) {
            typeId = "prestige";
        }

        setSelectedLeaderboardType(player, typeId);
        setSelectedLeaderboardPage(player, 1); // reset à la page 1 quand on change de type

        // on suppose que le menu s'appelle "leaderboards"
        openMenu(player, "leaderboards");
    }

    // ========= Menus d'upgrade / disenchant =========

    public void openEnchantUpgradeMenu(Player player, String enchantId) {
        if (player == null || enchantId == null || enchantId.isEmpty()) return;

        setSelectedEnchant(player, enchantId);

        Map<String, String> extra = enchantMenuService.buildUpgradePlaceholders(player, enchantId);
        if (extra == null) {
            extra = new HashMap<>();
        }

        PlayerProfile profile = plugin.getPlayerDataManager().getProfile(player);
        boolean notifOn = true;
        if (profile != null) {
            notifOn = profile.isEnchantProcMessageEnabled(enchantId);
        }
        String notifStatus = notifOn ? "§aON" : "§cOFF";

        extra.put("%enchant_notif_status%", notifStatus);

        openMenu(player, upgradeMenuId, extra);
    }

    public void openEnchantDisenchantMenu(Player player, String enchantId) {
        if (player == null || enchantId == null || enchantId.isEmpty()) return;

        setSelectedEnchant(player, enchantId);

        Map<String, String> extra = enchantMenuService.buildDisenchantPlaceholders(player, enchantId);
        openMenu(player, disenchantMenuId, extra);
    }

    public void handleEnchantUpgradeClick(Player player, String rawValue) {
        if (player == null) return;

        String enchantId = getSelectedEnchant(player);
        if (enchantId == null || enchantId.isEmpty()) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cAucun enchant sélectionné.");
            return;
        }

        int requestedLevels;
        if (rawValue == null || rawValue.isEmpty()) {
            requestedLevels = 1;
        } else if (rawValue.equalsIgnoreCase("MAX") || rawValue.equalsIgnoreCase("AFFORD")) {
            requestedLevels = 0;
        } else {
            try {
                requestedLevels = Integer.parseInt(rawValue);
            } catch (NumberFormatException ex) {
                requestedLevels = 1;
            }
        }

        if (plugin.getFarmService() != null) {
            plugin.getFarmService().attemptBulkUpgradeEnchant(player, enchantId, requestedLevels);
        }

        Map<String, String> extra = enchantMenuService.buildUpgradePlaceholders(player, enchantId);
        openMenu(player, upgradeMenuId, extra);
    }

    public void handleEnchantDisenchantClick(Player player, String rawValue) {
        if (player == null) return;

        String enchantId = getSelectedEnchant(player);
        if (enchantId == null || enchantId.isEmpty()) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cAucun enchant sélectionné.");
            return;
        }

        int requestedLevels;
        if (rawValue == null || rawValue.isEmpty()) {
            requestedLevels = 1;
        } else if (rawValue.equalsIgnoreCase("MAX")) {
            requestedLevels = 0;
        } else {
            try {
                requestedLevels = Integer.parseInt(rawValue);
            } catch (NumberFormatException ex) {
                requestedLevels = 1;
            }
        }

        if (plugin.getFarmService() != null) {
            plugin.getFarmService().attemptBulkDisenchantEnchant(player, enchantId, requestedLevels);
        }

        Map<String, String> extra = enchantMenuService.buildDisenchantPlaceholders(player, enchantId);
        openMenu(player, disenchantMenuId, extra);
    }

    // ========= Prestige shop & prestige =========

    public void handlePrestigeShopClick(Player player, String perkId) {
        if (player == null || perkId == null || perkId.isEmpty()) return;

        if (plugin.getFarmService() != null) {
            plugin.getFarmService().attemptBuyPrestigePerk(player, perkId);
        }

        openMenu(player, "prestige_shop");
    }

    public void handlePrestigeClick(Player player) {
        if (player == null) return;

        if (plugin.getFarmService() != null) {
            plugin.getFarmService().attemptPrestige(player);
        }

        openMenu(player, "prestige");
    }

    // =========== SKINS template ===========

    public static class SkinTemplateConfig {
        private final MenuIconConfig iconConfig;
        private final String name;
        private final List<String> lore;
        private final int startSlot;
        private final int perRow;

        public SkinTemplateConfig(MenuIconConfig iconConfig,
                                  String name,
                                  List<String> lore,
                                  int startSlot,
                                  int perRow) {
            this.iconConfig = iconConfig;
            this.name = name;
            this.lore = lore != null ? new ArrayList<>(lore) : Collections.emptyList();
            this.startSlot = startSlot;
            this.perRow = perRow;
        }

        public MenuIconConfig getIconConfig() {
            return iconConfig;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }

        public int getStartSlot() {
            return startSlot;
        }

        public int getPerRow() {
            return perRow;
        }
    }

    // =========== CROPS template ===========

    public static class CropTemplateConfig {
        private final MenuIconConfig iconConfig;
        private final String name;
        private final List<String> lore;
        private final int startSlot;
        private final int perRow;

        public CropTemplateConfig(MenuIconConfig iconConfig,
                                  String name,
                                  List<String> lore,
                                  int startSlot,
                                  int perRow) {
            this.iconConfig = iconConfig;
            this.name = name;
            this.lore = lore != null ? new ArrayList<>(lore) : Collections.emptyList();
            this.startSlot = startSlot;
            this.perRow = perRow;
        }

        public MenuIconConfig getIconConfig() {
            return iconConfig;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }

        public int getStartSlot() {
            return startSlot;
        }

        public int getPerRow() {
            return perRow;
        }
    }

    // =========== LEADERBOARD template ===========

    public static class LeaderboardTemplateConfig {
        private final MenuIconConfig iconConfig;
        private final String name;
        private final List<String> lore;
        private final int startSlot;
        private final int perRow;
        private final String typeId;

        public LeaderboardTemplateConfig(MenuIconConfig iconConfig,
                                         String name,
                                         List<String> lore,
                                         int startSlot,
                                         int perRow,
                                         String typeId) {
            this.iconConfig = iconConfig;
            this.name = name;
            this.lore = lore != null ? new ArrayList<>(lore) : Collections.emptyList();
            this.startSlot = startSlot;
            this.perRow = perRow;
            this.typeId = typeId.toLowerCase(Locale.ROOT);
        }

        public MenuIconConfig getIconConfig() {
            return iconConfig;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }

        public int getStartSlot() {
            return startSlot;
        }

        public int getPerRow() {
            return perRow;
        }

        /**
         * Type logique du leaderboard : "prestige", "essence", "crops_total"
         */
        public String getTypeId() {
            return typeId;
        }
    }
}
