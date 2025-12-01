package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.menu.MenuManager.SkinTemplateConfig;
import fr.batistou15.prestigehoe.skin.SkinDefinition;
import fr.batistou15.prestigehoe.skin.SkinManager;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

public class SkinMenuService {

    public static final String SKIN_MENU_ID = "skins";

    private final NamespacedKey SKIN_PDC_KEY;

    private final PrestigeHoePlugin plugin;
    private final SkinManager skinManager;
    private final MenuItemFactory itemFactory;
    private final MenuPlaceholderService placeholderService;

    public SkinMenuService(PrestigeHoePlugin plugin,
                           SkinManager skinManager,
                           MenuItemFactory itemFactory,
                           MenuPlaceholderService placeholderService) {
        this.plugin = plugin;
        this.skinManager = skinManager;
        this.itemFactory = itemFactory;
        this.placeholderService = placeholderService;
        this.SKIN_PDC_KEY = new NamespacedKey(plugin, "skin_id");
    }

    public NamespacedKey getSkinPdcKey() {
        return SKIN_PDC_KEY;
    }

    /**
     * Remplit le menu "skins" avec toutes les skins connues.
     *
     * On utilise :
     *  - SkinMenu.yml : uniquement pour l'icône et le nom de base (skin-template)
     *  - skins.yml    : pour le lore locked/unlocked, les boosts et le statut Équipé OUI/NON
     */
    public void fillSkinIcons(Inventory inv,
                              MenuHolder holder,
                              Player player,
                              SkinTemplateConfig tpl) {

        if (skinManager == null) return;

        PlayerDataManager dataMgr = plugin.getPlayerDataManager();
        if (dataMgr == null) return;

        PlayerProfile profile = dataMgr.getProfile(player);
        if (profile == null) return;

        // Skin active dans le profil
        String activeSkinId = profile.getActiveSkinId();
        if (activeSkinId == null || activeSkinId.isEmpty()) {
            activeSkinId = "default";
        }

        // On charge skins.yml une seule fois pour récupérer les sections "skins.<id>.lore"
        File skinsFile = new File(plugin.getDataFolder(), "skins.yml");
        YamlConfiguration skinsCfg = null;
        ConfigurationSection skinsRoot = null;

        if (skinsFile.exists()) {
            skinsCfg = YamlConfiguration.loadConfiguration(skinsFile);
            skinsRoot = skinsCfg.getConfigurationSection("skins");
        }

        int baseSlot = tpl.getStartSlot();
        int perRow = Math.max(1, tpl.getPerRow());

        int index = 0;

        for (SkinDefinition def : skinManager.getAllSkins().values()) {
            if (def == null || !def.isEnabled()) continue;

            String skinId = def.getId();
            if (skinId == null || skinId.isEmpty()) continue;

            // Position en grille (start-slot + per-row)
            int rowOffset = index / perRow;
            int colOffset = index % perRow;
            int slot = baseSlot + rowOffset * 9 + colOffset;
            index++;

            if (slot < 0 || slot >= inv.getSize()) continue;

            boolean isUnlocked = profile.isSkinUnlocked(skinId);
            boolean isActive = activeSkinId.equalsIgnoreCase(skinId);

            // Statut textuel si tu l'utilises encore dans le nom / ailleurs
            String status = isActive
                    ? "§aÉquipé"
                    : (isUnlocked ? "§eDébloqué" : "§cNon débloqué");

            // Placeholder généraux pour le template (nom, etc.)
            Map<String, String> ph = placeholderService.buildBasePlaceholders(player);
            ph.put("%skin_id%", skinId);
            ph.put("%skin_display_name%", def.getDisplayName() != null ? def.getDisplayName() : skinId);
            ph.put("%skin_status%", status);

            // Requirements / Cost (depuis SkinDefinition, pas la config brute)
            SkinDefinition.Requirements req = def.getRequirements();
            SkinDefinition.Cost cost = def.getCost();

            int minPrestige = req != null ? req.getMinPrestige() : 0;
            long minCrops = req != null ? req.getMinTotalCrops() : 0L;
            String reqPerm = (req != null && req.getRequiredPermission() != null)
                    ? req.getRequiredPermission()
                    : "";

            double costMoney = cost != null ? cost.getMoney() : 0.0D;
            double costEssence = cost != null ? cost.getEssence() : 0.0D;

            ph.put("%skin_req_prestige%", String.valueOf(minPrestige));
            ph.put("%skin_req_crops%", String.valueOf(minCrops));
            ph.put("%skin_req_permission%", (reqPerm.isEmpty() ? "Aucune" : reqPerm));

            ph.put("%skin_cost_money%", formatDouble(costMoney));
            ph.put("%skin_cost_essence%", formatDouble(costEssence));
            // Ces deux-là ne servent plus vraiment, mais on les laisse pour compat.
            ph.put("%skin_cost_items%", "");
            ph.put("%skin_cost_commands%", "");

            // Matériau de l'icône dans le menu
            Material iconMat = def.getIconMaterial() != null
                    ? def.getIconMaterial()
                    : Material.DIAMOND_HOE;

            MenuIconConfig iconCfg = new MenuIconConfig(
                    iconMat.name(),
                    0,
                    "",
                    "",
                    "",
                    ""
            );

            // On utilise le nom/lore "brut" du template, mais on va ÉCRASER le lore ensuite
            MenuItemConfig itemCfg = new MenuItemConfig(
                    "skin_" + skinId.toLowerCase(Locale.ROOT),
                    slot,
                    iconCfg,
                    tpl.getName(),
                    tpl.getLore(),
                    MenuAction.NONE,
                    null
            );

            ItemStack stack = itemFactory.buildItem(itemCfg, player, ph);
            if (stack == null) continue;

            ItemMeta meta = stack.getItemMeta();
            if (meta == null) continue;

            // Lore dynamique depuis skins.yml (lore.locked / lore.unlocked)
            ConfigurationSection skinSec = (skinsRoot != null)
                    ? skinsRoot.getConfigurationSection(skinId)
                    : null;

            List<String> lore = buildSkinLore(def, skinSec, isUnlocked, isActive);
            meta.setLore(lore);

            // Glow si skin équipé
            if (isActive) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // Tag PDC: id du skin sur l'item du menu (pour le listener)
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(SKIN_PDC_KEY, PersistentDataType.STRING, skinId);

            stack.setItemMeta(meta);
            inv.setItem(slot, stack);
        }
    }

    /**
     * Construit le lore d'un skin à partir de skins.yml :
     *  - "lore.locked" si non débloqué
     *  - "lore.unlocked" si déjà débloqué
     *
     * Placeholders gérés dans ce lore :
     *  - %skin_id%, %skin_name%
     *  - %equipped% (&aOUI / &cNON)
     *  - %min_prestige%, %min_total_crops%, %required_permission%
     *  - %money_cost%, %essence_cost%
     *  - %money_mult%, %essence_mult%, %hoe_xp_mult%, %player_xp_mult%, %job_xp_mult%, %enchant_proc_mult%
     */
    private List<String> buildSkinLore(SkinDefinition def,
                                       ConfigurationSection skinSec,
                                       boolean isUnlocked,
                                       boolean isActive) {

        List<String> template;

        if (skinSec != null) {
            String path = isUnlocked ? "lore.unlocked" : "lore.locked";
            template = skinSec.getStringList(path);
        } else {
            template = Collections.emptyList();
        }

        List<String> out = new ArrayList<>();

        // Fallback si aucun lore configuré dans skins.yml
        if (template == null || template.isEmpty()) {
            if (isUnlocked) {
                template = List.of(
                        "&7Skin débloqué.",
                        "&7Équipé: %equipped%"
                );
            } else {
                template = List.of(
                        "&cSkin non débloqué.",
                        "&7Coût: &e%money_cost% &7/ &b%essence_cost%"
                );
            }
        }

        SkinDefinition.Requirements req = def.getRequirements();
        SkinDefinition.Cost cost = def.getCost();

        for (String raw : template) {
            String s = raw;

            // Skin id / nom
            s = s.replace("%skin_id%", def.getId());
            s = s.replace("%skin_name%", def.getDisplayName() != null ? def.getDisplayName() : def.getId());

            // Équipé ?
            s = s.replace("%equipped%", isActive ? "&aOUI" : "&cNON");

            // Requirements
            if (req != null) {
                s = s.replace("%min_prestige%", String.valueOf(req.getMinPrestige()));
                s = s.replace("%min_total_crops%", String.valueOf(req.getMinTotalCrops()));
                s = s.replace("%required_permission%",
                        req.getRequiredPermission() == null ? "" : req.getRequiredPermission());
            } else {
                s = s.replace("%min_prestige%", "0");
                s = s.replace("%min_total_crops%", "0");
                s = s.replace("%required_permission%", "");
            }

            // Cost
            if (cost != null) {
                s = s.replace("%money_cost%", String.valueOf(cost.getMoney()));
                s = s.replace("%essence_cost%", String.valueOf(cost.getEssence()));
            } else {
                s = s.replace("%money_cost%", "0");
                s = s.replace("%essence_cost%", "0");
            }

            // Boosts globaux
            s = s.replace("%money_mult%", String.valueOf(def.getMoneyMultiplier()));
            s = s.replace("%essence_mult%", String.valueOf(def.getEssenceMultiplier()));
            s = s.replace("%hoe_xp_mult%", String.valueOf(def.getXpHoeMultiplier()));
            s = s.replace("%player_xp_mult%", String.valueOf(def.getXpPlayerMultiplier()));
            s = s.replace("%job_xp_mult%", String.valueOf(def.getJobXpMultiplier()));
            s = s.replace("%enchant_proc_mult%", String.valueOf(def.getEnchantProcMultiplier()));

            out.add(MessageUtil.color(s));
        }

        // ID en bas (debug)
        out.add(MessageUtil.color("&8ID: " + def.getId().toLowerCase(Locale.ROOT)));

        return out;
    }

    /**
     * Ancienne méthode de clic directe (si tu l'utilises encore quelque part).
     * Ta logique principale de click est maintenant dans SkinMenuListener.
     */
    public void handleSkinClick(Player player, String skinId) {
        if (player == null || skinId == null || skinId.isEmpty()) return;
        if (skinManager == null) return;

        SkinDefinition def = skinManager.getSkin(skinId);
        if (def == null || !def.isEnabled()) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cCe skin n'existe plus ou est désactivé.");
            return;
        }

        PlayerDataManager dataMgr = plugin.getPlayerDataManager();
        if (dataMgr == null) return;

        PlayerProfile profile = dataMgr.getProfile(player);
        if (profile == null) return;

        // On empêche d'équiper un skin non débloqué via cette méthode
        if (!profile.isSkinUnlocked(def.getId())) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cTu n'as pas encore débloqué ce skin.");
            return;
        }

        String current = profile.getActiveSkinId();
        if (current != null && current.equalsIgnoreCase(skinId)) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§eCe skin est déjà §aéquipé§e.");
            return;
        }

        profile.setActiveSkinId(skinId);

        HoeItemManager hoeItemManager = plugin.getHoeItemManager();
        if (hoeItemManager != null) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (hoeItemManager.isPrestigeHoe(inHand)) {
                hoeItemManager.applySkin(inHand, skinId);
            }
        }

        String displayName = def.getDisplayName() != null ? def.getDisplayName() : skinId;
        MessageUtil.sendPlain(player,
                MessageUtil.getPrefix() + "§aSkin équipé : §e" + displayName + "§a.");

        plugin.getMenuManager().openMenu(player, SKIN_MENU_ID);
    }

    // Petit helper pour formatter l'argent / essence
    private String formatDouble(double value) {
        if (value <= 0.0D) return "0";
        long rounded = Math.round(value);
        return String.valueOf(rounded);
    }
}
