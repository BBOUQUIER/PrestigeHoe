package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.skin.SkinDefinition;
import fr.batistou15.prestigehoe.skin.SkinManager;
import fr.batistou15.prestigehoe.util.MessageUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

public class SkinMenuListener implements Listener {

    private final PrestigeHoePlugin plugin;
    private final SkinManager skinManager;
    private final PlayerDataManager playerDataManager;
    private final HoeItemManager hoeItemManager;
    private final SkinMenuService skinMenuService;
    private final MenuManager menuManager;

    private Economy economy;

    public SkinMenuListener(PrestigeHoePlugin plugin,
                            SkinManager skinManager,
                            PlayerDataManager playerDataManager,
                            HoeItemManager hoeItemManager,
                            SkinMenuService skinMenuService) {
        this.plugin = plugin;
        this.skinManager = skinManager;
        this.playerDataManager = playerDataManager;
        this.hoeItemManager = hoeItemManager;
        this.skinMenuService = skinMenuService;
        this.menuManager = plugin.getMenuManager();

        setupEconomy();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        var rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.economy = rsp.getProvider();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) {
            return;
        }

        if (!(clickedInv.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (!holder.getMenuId().equalsIgnoreCase(SkinMenuService.SKIN_MENU_ID)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        if (!clicked.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String skinIdRaw = pdc.get(skinMenuService.getSkinPdcKey(), PersistentDataType.STRING);
        if (skinIdRaw == null || skinIdRaw.isEmpty()) {
            return; // bouton retour / filler
        }

        // On normalise l'id
        String skinId = skinIdRaw.toLowerCase(Locale.ROOT);

        SkinDefinition def = skinManager.getSkin(skinId);
        if (def == null || !def.isEnabled()) {
            MessageUtil.sendPlain(player, MessageUtil.getPrefix() + "§cCe skin est indisponible.");
            return;
        }

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null) {
            return;
        }

        // Requirements depuis SkinDefinition (permission ici)
        SkinDefinition.Requirements req = def.getRequirements();
        if (req != null) {
            String reqPerm = req.getRequiredPermission();
            if (reqPerm != null && !reqPerm.isEmpty() && !player.hasPermission(reqPerm)) {
                MessageUtil.sendPlain(player,
                        MessageUtil.getPrefix() + "§cTu n'as pas la permission requise pour ce skin.");
                return;
            }
        }

        boolean alreadyUnlocked = profile.isSkinUnlocked(def.getId());

        // Déjà débloqué → on l’équipe juste
        if (alreadyUnlocked) {
            profile.setActiveSkinId(def.getId());
            equipSkin(player, profile, def.getId(), def);

            if (menuManager != null) {
                menuManager.openMenu(player, SkinMenuService.SKIN_MENU_ID);
            }
            return;
        }

        // Sinon : skin verrouillé → on applique les coûts (money/essence/items/commands)
        ConfigurationSection skinSec = getSkinConfigSection(def.getId());
        if (!checkAndPayCost(player, profile, def, skinSec)) {
            return; // pas assez de ressources
        }

        // Déblocage + equip
        profile.unlockSkin(def.getId());
        profile.setActiveSkinId(def.getId());
        equipSkin(player, profile, def.getId(), def);

        MessageUtil.sendPlain(player,
                MessageUtil.getPrefix() + "§aTu as débloqué et équipé le skin §e" + def.getDisplayName() + "§a.");

        if (menuManager != null) {
            menuManager.openMenu(player, SkinMenuService.SKIN_MENU_ID);
        }
    }

    /**
     * Récupère la section "skins.<skinId>" de skins.yml.
     */
    private ConfigurationSection getSkinConfigSection(String skinId) {
        File skinsFile = new File(plugin.getDataFolder(), "skins.yml");
        if (!skinsFile.exists()) {
            return null;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(skinsFile);
        return cfg.getConfigurationSection("skins." + skinId);
    }

    /**
     * Vérifie et applique les coûts du skin (money, essence, items, commands).
     */
    private boolean checkAndPayCost(Player player, PlayerProfile profile, SkinDefinition def, ConfigurationSection skinSec) {
        String prefix = MessageUtil.getPrefix();

        if (skinSec == null) {
            // Pas de config → gratuit
            return true;
        }

        ConfigurationSection costSec = skinSec.getConfigurationSection("cost");
        if (costSec == null) {
            return true; // aucun coût → gratuit
        }

        double moneyCost = costSec.getDouble("money", 0.0D);
        double essenceCost = costSec.getDouble("essence", 0.0D);
        List<Map<?, ?>> itemMaps = costSec.getMapList("items");
        List<String> commands = costSec.getStringList("commands");

        if (itemMaps == null) itemMaps = Collections.emptyList();
        if (commands == null) commands = Collections.emptyList();

        // --- Vérif des ressources ---

        // Money (Vault)
        if (moneyCost > 0.0D) {
            if (economy == null) {
                MessageUtil.sendPlain(player,
                        prefix + "§cAucun système d'économie (Vault) détecté, impossible de payer ce skin.");
                return false;
            }
            if (!economy.has(player, moneyCost)) {
                MessageUtil.sendPlain(player,
                        prefix + "§cTu n'as pas assez d'argent pour ce skin (§e" +
                                formatAmount(moneyCost) + "§c).");
                return false;
            }
        }

        // Essence : vérif du solde
        if (essenceCost > 0.0D) {
            double essenceBalance = profile.getEssence(); // getter existant côté profil

            // petite marge pour éviter les soucis de double
            if (essenceBalance + 1e-6 < essenceCost) {
                MessageUtil.sendPlain(player,
                        prefix + "§cTu n'as pas assez d'Essence pour ce skin (§e"
                                + formatAmount(essenceCost) + "§c).");
                return false;
            }
        }

        // Items
        if (!itemMaps.isEmpty()) {
            if (!hasRequiredItems(player, itemMaps)) {
                MessageUtil.sendPlain(player,
                        prefix + "§cIl te manque des items pour débloquer ce skin.");
                return false;
            }
        }

        // --- Tout est OK → on retire / exécute ---

        if (moneyCost > 0.0D && economy != null) {
            economy.withdrawPlayer(player, moneyCost);
        }

        if (essenceCost > 0.0D) {
            profile.removeEssence(essenceCost);
        }

        if (!itemMaps.isEmpty()) {
            removeRequiredItems(player, itemMaps);
        }

        if (!commands.isEmpty()) {
            runUnlockCommands(player, commands);
        }

        return true;
    }

    /**
     * Vérifie si le joueur possède tous les items requis.
     */
    private boolean hasRequiredItems(Player player, List<Map<?, ?>> itemMaps) {
        PlayerInventory inv = player.getInventory();

        for (Map<?, ?> map : itemMaps) {
            Object matObj = map.get("material");
            Object amtObj = map.get("amount");
            if (matObj == null || amtObj == null) continue;

            String matName = String.valueOf(matObj);
            int amount;
            try {
                amount = Integer.parseInt(String.valueOf(amtObj));
            } catch (NumberFormatException ex) {
                continue;
            }

            Material mat = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
            if (mat == null) continue;

            int found = 0;
            for (ItemStack content : inv.getContents()) {
                if (content == null || content.getType() != mat) continue;
                found += content.getAmount();
                if (found >= amount) break;
            }

            if (found < amount) {
                return false;
            }
        }

        return true;
    }

    /**
     * Retire les items requis de l'inventaire.
     */
    private void removeRequiredItems(Player player, List<Map<?, ?>> itemMaps) {
        PlayerInventory inv = player.getInventory();

        for (Map<?, ?> map : itemMaps) {
            Object matObj = map.get("material");
            Object amtObj = map.get("amount");
            if (matObj == null || amtObj == null) continue;

            String matName = String.valueOf(matObj);
            int amount;
            try {
                amount = Integer.parseInt(String.valueOf(amtObj));
            } catch (NumberFormatException ex) {
                continue;
            }

            Material mat = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
            if (mat == null) continue;

            int toRemove = amount;

            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType() != mat) continue;

                int stackAmount = stack.getAmount();
                if (stackAmount <= toRemove) {
                    inv.setItem(slot, null);
                    toRemove -= stackAmount;
                } else {
                    stack.setAmount(stackAmount - toRemove);
                    inv.setItem(slot, stack);
                    toRemove = 0;
                }

                if (toRemove <= 0) {
                    break;
                }
            }
        }
    }

    /**
     * Exécute les commandes de déblocage (console, avec %player%).
     */
    private void runUnlockCommands(Player player, List<String> commands) {
        for (String cmd : commands) {
            if (cmd == null || cmd.isEmpty()) continue;
            String parsed = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    /**
     * Applique le skin sur la houe en main.
     */
    private void equipSkin(Player player, PlayerProfile profile, String skinId, SkinDefinition def) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cTu dois tenir ta PrestigeHoe en main pour équiper un skin.");
            return;
        }

        if (!hoeItemManager.isPrestigeHoe(hand)) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "§cTu dois tenir ta PrestigeHoe en main pour équiper un skin.");
            return;
        }

        // Applique le skin sur l'item (change le custom model data / tag, etc.)
        hoeItemManager.applySkin(hand, skinId);

        MessageUtil.sendPlain(player,
                MessageUtil.getPrefix() + "§aSkin §e" + def.getDisplayName() + " §aéquipé.");
    }

    private String formatAmount(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        } else {
            return String.format(Locale.US, "%.2f", value);
        }
    }
}
