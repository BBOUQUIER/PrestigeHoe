package fr.batistou15.prestigehoe.menu;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MenuItemFactory {

    private final PrestigeHoePlugin plugin;
    private final MenuPlaceholderService placeholderService;
    private final SkullUtil skullUtil;

    public MenuItemFactory(PrestigeHoePlugin plugin,
                           MenuPlaceholderService placeholderService,
                           SkullUtil skullUtil) {
        this.plugin = plugin;
        this.placeholderService = placeholderService;
        this.skullUtil = skullUtil;
    }

    public ItemStack buildItem(MenuItemConfig cfg, Player viewer) {
        return buildItem(cfg, viewer, null);
    }

    public ItemStack buildItem(MenuItemConfig cfg,
                               Player viewer,
                               Map<String, String> extraPlaceholders) {
        MenuIconConfig iconCfg = cfg.getIcon();

        // 1) Tête custom éventuelle
        ItemStack item = skullUtil.buildSkull(iconCfg, viewer);

        // 2) Fallback material classique
        if (item == null) {
            Material mat = Material.matchMaterial(iconCfg.getMaterial());
            if (mat == null) {
                mat = Material.BARRIER;
            }
            item = new ItemStack(mat);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Placeholders de base
            Map<String, String> placeholders = placeholderService.buildBasePlaceholders(viewer);
            if (extraPlaceholders != null && !extraPlaceholders.isEmpty()) {
                placeholders.putAll(extraPlaceholders);
            }

            // CustomModelData
            if (iconCfg.getCustomModelData() > 0) {
                try {
                    meta.setCustomModelData(iconCfg.getCustomModelData());
                } catch (Throwable ignored) {
                }
            }

            // Nom
            String name = cfg.getName();
            if (name != null && !name.isEmpty()) {
                name = placeholderService.applyPlaceholders(name, placeholders);
                meta.setDisplayName(MessageUtil.color(name));
            }

            // Lore
            List<String> lore = cfg.getLore();
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) {
                    if (line == null) continue;
                    line = placeholderService.applyPlaceholders(line, placeholders);

                    String[] parts = line.split("\n");
                    for (String part : parts) {
                        colored.add(MessageUtil.color(part));
                    }
                }
                meta.setLore(colored);
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }

        return item;
    }
}
