package fr.batistou15.prestigehoe.hoe;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.skin.SkinDefinition;
import fr.batistou15.prestigehoe.skin.SkinManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class HoeItemManager {

    private final PrestigeHoePlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final ConfigManager configManager;

    private final NamespacedKey hoeKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey ownerNameKey; // 👈 NEW
    private final NamespacedKey SKIN_ID_KEY;
    public HoeItemManager(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.configManager = plugin.getConfigManager();

        this.hoeKey = new NamespacedKey(plugin, "prestigehoe_hoe");
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.ownerNameKey = new NamespacedKey(plugin, "owner_name"); // 👈 NEW
        this.SKIN_ID_KEY = new NamespacedKey(plugin, "skin-id");
    }

    // =========================
    //   Vérifs / Propriétaire
    // =========================

    /**
     * Vérifie si un ItemStack est une PrestigeHoe.
     */
    public boolean isPrestigeHoe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte value = pdc.get(hoeKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    /**
     * Vérifie que la houe appartient bien à ce joueur.
     */
    public boolean isOwnedBy(ItemStack item, Player player) {
        if (!isPrestigeHoe(item)) return false;
        UUID owner = getOwnerUuid(item);
        if (owner == null) return false;
        return owner.equals(player.getUniqueId());
    }

    /**
     * UUID du propriétaire (ou null).
     */
    public UUID getOwnerUuid(ItemStack item) {
        if (!isPrestigeHoe(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String uuidStr = pdc.get(ownerKey, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;

        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Nom du propriétaire pour l’affichage (lore).
     * 1) essaie le champ owner_name
     * 2) sinon, résout via le UUID → OfflinePlayer.name
     */
    public String getOwnerName(ItemStack item) {
        if (!isPrestigeHoe(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1) nom stocké en dur
        String name = pdc.get(ownerNameKey, PersistentDataType.STRING);
        if (name != null && !name.isEmpty()) {
            return name;
        }

        // 2) fallback via UUID
        String uuidStr = pdc.get(ownerKey, PersistentDataType.STRING);
        if (uuidStr == null || uuidStr.isEmpty()) return null;

        try {
            UUID uuid = UUID.fromString(uuidStr);
            return plugin.getServer().getOfflinePlayer(uuid).getName();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }


    /**
     * Change le propriétaire d’une houe (utilisée par /prestigehoe setowner).
     */
    public void setOwner(ItemStack item, UUID ownerUuid, String ownerName) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (ownerUuid != null) {
            pdc.set(ownerKey, PersistentDataType.STRING, ownerUuid.toString());
        }
        if (ownerName != null && !ownerName.isEmpty()) {
            pdc.set(ownerNameKey, PersistentDataType.STRING, ownerName);
        }

        item.setItemMeta(meta);
    }

    /**
     * Vérifie si le joueur possède déjà une PrestigeHoe dans son inventaire.
     */
    public boolean playerHasHoe(Player player) {
        for (ItemStack content : player.getInventory().getContents()) {
            if (isPrestigeHoe(content)) {
                return true;
            }
        }
        return false;
    }

    // =========================
    //     Création / Update
    // =========================

    /**
     * Donne une nouvelle PrestigeHoe au joueur (utilisée pour first-join ou /prestigehoe give).
     */
    public void giveNewHoe(Player player) {
        PlayerProfile profile = playerDataManager.getProfile(player);
        HoeData hoeData = profile.getHoeData();

        initHoeDefaultsFromConfig(hoeData);

        ItemStack hoeItem = buildHoeItem(player, profile);
        player.getInventory().addItem(hoeItem);
    }

    /**
     * Met à jour le nom/lore de la houe appartenant au joueur dans son inventaire.
     * (Utilisé si tu veux rafraîchir côté HoeItemManager ; côté visuel principal
     * on utilise plutôt FarmService.updateHoeDisplay.)
     */
    public void updateHoeInInventory(Player player) {
        PlayerProfile profile = playerDataManager.getProfile(player);
        HoeData hoeData = profile.getHoeData();

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            if (!isPrestigeHoe(item)) continue;
            if (!isOwnedBy(item, player)) continue;

            ItemStack updated = applyMetaToExistingItem(item, player, profile, hoeData);
            contents[i] = updated;
        }
        player.getInventory().setContents(contents);
    }

    // ================== internes ==================

    private void initHoeDefaultsFromConfig(HoeData hoeData) {
        // Niveau / xp / prestige par défaut
        if (hoeData.getLevel() <= 0) {
            hoeData.setLevel(1);
        }
        if (hoeData.getPrestige() < 0) {
            hoeData.setPrestige(0);
        }
        // Enchants par défaut si aucun enchant enregistré
        if (hoeData.getEnchantLevels().isEmpty()) {
            FileConfiguration cfg = configManager.getMainConfig();
            ConfigurationSection sec = cfg.getConfigurationSection("hoe.default-enchants");
            if (sec != null) {
                for (String enchantId : sec.getKeys(false)) {
                    int level = sec.getInt(enchantId, 0);
                    if (level > 0) {
                        hoeData.setEnchantLevel(enchantId, level);
                    }
                }
            }
        }
    }

    /**
     * Crée un nouvel ItemStack de houe à partir des configs et des données du joueur.
     */
    private ItemStack buildHoeItem(Player player, PlayerProfile profile) {
        HoeData hoeData = profile.getHoeData();
        FileConfiguration cfg = configManager.getMainConfig();

        ConfigurationSection display = cfg.getConfigurationSection("hoe.display");
        String nameTemplate = "&6PrestigeHoe";
        List<String> loreTemplate = new ArrayList<>();

        if (display != null) {
            // le nom/lore viennent toujours de config.yml
            nameTemplate = display.getString("name", nameTemplate);
            loreTemplate = display.getStringList("lore");
        }

        // 1) Skin active depuis le profil
        String skinId = profile.getActiveSkinId();
        if (skinId == null || skinId.isEmpty()) {
            skinId = "default";
        }

        // 2) SkinManager via le plugin
        SkinManager skinManager = PrestigeHoePlugin.getInstance().getSkinManager();
        SkinDefinition skinDef = null;
        if (skinManager != null) {
            skinDef = skinManager.getSkinOrDefault(skinId);
        }

        // 3) Matériau de la hoe
        Material material;
        if (skinDef != null && skinDef.getHoeMaterial() != null) {
            material = skinDef.getHoeMaterial();
        } else {
            material = Material.DIAMOND_HOE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // CustomModelData depuis le skin si défini
        if (skinDef != null && skinDef.getHoeCustomModelData() > 0) {
            meta.setCustomModelData(skinDef.getHoeCustomModelData());
        }

        String name = applyPlaceholders(nameTemplate, player, profile);
        meta.setDisplayName(color(name));

        List<String> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            lore.add(color(applyPlaceholders(line, player, profile)));
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }

        // Tag PDC
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(hoeKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(ownerNameKey, PersistentDataType.STRING, player.getName());

        item.setItemMeta(meta);
        return item;
    }



    /**
     * Réutilise un ItemStack existant mais en mettant à jour nom/lore
     * (utilise toujours les placeholders generiques du config.yml).
     */
    private ItemStack applyMetaToExistingItem(ItemStack item, Player player, PlayerProfile profile, HoeData hoeData) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        FileConfiguration cfg = configManager.getMainConfig();
        ConfigurationSection display = cfg.getConfigurationSection("hoe.display");

        String nameTemplate = "&6PrestigeHoe";
        List<String> loreTemplate = new ArrayList<>();

        if (display != null) {
            nameTemplate = display.getString("name", nameTemplate);
            loreTemplate = display.getStringList("lore");
        }

        String name = applyPlaceholders(nameTemplate, player, profile);
        meta.setDisplayName(color(name));

        List<String> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            lore.add(color(applyPlaceholders(line, player, profile)));
        }
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }
    public String getSkinId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (!isPrestigeHoe(item)) return null;

        if (!item.hasItemMeta()) return null;
        var meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(SKIN_ID_KEY, PersistentDataType.STRING)) return null;
        return pdc.get(SKIN_ID_KEY, PersistentDataType.STRING);
    }
    public void applySkin(ItemStack item, String skinId) {
        if (item == null || item.getType().isAir()) return;
        if (!isPrestigeHoe(item)) return;

        if (!item.hasItemMeta()) {
            return;
        }

        var meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // On stocke l'ID dans le PDC (même si la skin n'existe plus, pour debug)
        if (skinId == null || skinId.isEmpty()) {
            pdc.remove(SKIN_ID_KEY);
            item.setItemMeta(meta);
            return;
        }

        pdc.set(SKIN_ID_KEY, PersistentDataType.STRING, skinId.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);

        // Ensuite on applique visuellement si la skin est connue
        SkinManager skinManager = PrestigeHoePlugin.getInstance().getSkinManager();
        if (skinManager == null) {
            return;
        }

        SkinDefinition def = skinManager.getSkin(skinId);
        if (def == null) {
            return;
        }

        // Apparence vanilla : material + custom model data
        if (def.getHoeMaterial() != null) {
            item.setType(def.getHoeMaterial());
        }

        if (item.hasItemMeta()) {
            var meta2 = item.getItemMeta();
            if (def.getHoeCustomModelData() > 0) {
                meta2.setCustomModelData(def.getHoeCustomModelData());
            }
            item.setItemMeta(meta2);
        }

        // TODO plus tard : support ItemsAdder / Oraxen si tu veux que certaines skins
        // soient purement via ces plugins plutôt que via custom-model-data.
    }

    private String applyPlaceholders(String input, Player player, PlayerProfile profile) {
        if (input == null) return "";

        HoeData hoe = profile.getHoeData();

        String out = input;
        out = out.replace("%player%", player.getName());
        out = out.replace("%level%", String.valueOf(hoe.getLevel()));
        out = out.replace("%xp%", String.format(Locale.US, "%.1f", hoe.getXp()));
        out = out.replace("%prestige%", String.valueOf(hoe.getPrestige()));
        out = out.replace("%essence%", String.format(Locale.US, "%.1f", profile.getEssence()));
        out = out.replace("%tokens%", String.valueOf(profile.getPrestigeTokens()));

        return out;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}
