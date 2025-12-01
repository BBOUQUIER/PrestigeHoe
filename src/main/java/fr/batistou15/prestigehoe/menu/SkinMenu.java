package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.skin.SkinDefinition;
import fr.batistou15.prestigehoe.skin.SkinManager;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SkinMenu {

    public static final String TITLE = "§8Skins de houe";

    // Tag PDC pour stocker l'id de skin dans les items du menu
    public static final NamespacedKey SKIN_MENU_KEY =
            new NamespacedKey(PrestigeHoePlugin.getInstance(), "skin_menu_id");

    private final PrestigeHoePlugin plugin;
    private final SkinManager skinManager;
    private final PlayerDataManager playerDataManager;

    public SkinMenu(PrestigeHoePlugin plugin,
                    SkinManager skinManager,
                    PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.skinManager = skinManager;
        this.playerDataManager = playerDataManager;
    }

    public void open(Player player) {
        Inventory inv = createInventoryFor(player);
        player.openInventory(inv);
    }

    private Inventory createInventoryFor(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null) {
            player.sendMessage(MessageUtil.color("&cImpossible de charger ton profil PrestigeHoe."));
            return inv;
        }

        String activeId = profile.getActiveSkinId();
        if (activeId == null || activeId.isEmpty()) {
            activeId = "default";
        }

        int slot = 10;
        for (SkinDefinition def : skinManager.getAllSkins().values()) {
            if (!def.isEnabled()) continue;

            // Icône du skin
            Material iconMat = def.getIconMaterial();
            if (iconMat == null || !iconMat.isItem()) {
                iconMat = Material.DIAMOND_HOE;
            }

            ItemStack icon = new ItemStack(iconMat);
            ItemMeta meta = icon.getItemMeta();
            if (meta == null) continue;

            if (def.getHoeCustomModelData() > 0) {
                meta.setCustomModelData(def.getHoeCustomModelData());
            }

            // Nom
            String displayName = def.getDisplayName() != null
                    ? def.getDisplayName()
                    : def.getId();
            meta.setDisplayName(MessageUtil.color(displayName));

            boolean isUnlocked = profile.isSkinUnlocked(def.getId());
            boolean isActive = def.getId().equalsIgnoreCase(activeId);

            // Lore dynamique selon l'état (lock/unlock + boosts + équipé)
            List<String> lore = buildLore(def, profile, isUnlocked, isActive);
            meta.setLore(lore);

            // Glow si équipé
            if (isActive) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // On tag l'item avec l'id de skin pour le listener
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(SKIN_MENU_KEY, PersistentDataType.STRING, def.getId());

            icon.setItemMeta(meta);

            inv.setItem(slot, icon);

            slot++;
            // petit saut de ligne visuel toutes les 7 colonnes
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
            if (slot >= 54) break;
        }

        return inv;
    }

    /**
     * Construit le lore à partir des templates du skins.yml :
     * - locked : prérequis + coûts
     * - unlocked : boosts + "Équipé: OUI/NON"
     */
    private List<String> buildLore(SkinDefinition def,
                                   PlayerProfile profile,
                                   boolean isUnlocked,
                                   boolean isActive) {

        List<String> template = isUnlocked
                ? def.getUnlockedLoreTemplate()
                : def.getLockedLoreTemplate();

        List<String> out = new ArrayList<>();

        // Fallback si aucun lore configuré
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

            // Equipé ?
            s = s.replace("%equipped%", isActive ? "&aOUI" : "&cNON");

            // Requirements
            if (req != null) {
                s = s.replace("%min_prestige%", String.valueOf(req.getMinPrestige()));
                s = s.replace("%min_total_crops%", String.valueOf(req.getMinTotalCrops()));
                s = s.replace("%required_permission%", req.getRequiredPermission() == null ? "" : req.getRequiredPermission());
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

        // Toujours ajouter une ligne ID en bas (utile pour debug / config)
        out.add(MessageUtil.color("&8ID: " + def.getId().toLowerCase(Locale.ROOT)));

        return out;
    }

}
