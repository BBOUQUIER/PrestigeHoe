package fr.batistou15.prestigehoe.menu;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public class SkullUtil {

    private final PrestigeHoePlugin plugin;

    public SkullUtil(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Construit une tête custom à partir de skull-owner / skull-texture.
     * - skull-owner  => tête du joueur
     * - skull-texture:
     *    - ID numérique + HeadDatabase présent => tête HeadDatabase
     *    - URL (http...) => skin appliquée depuis l'URL
     *    - base64 (minecraft-heads.com) => décodé, URL extraite, skin appliquée
     */
    public ItemStack buildSkull(MenuIconConfig iconCfg, Player viewer) {
        String owner = safeTrim(iconCfg.getSkullOwner());
        String texture = safeTrim(iconCfg.getSkullTexture());

        // Rien de configuré => pas une tête
        if ((owner == null || owner.isEmpty()) && (texture == null || texture.isEmpty())) {
            return null;
        }

        // 1) skull-owner => tête de joueur
        if (owner != null && !owner.isEmpty()) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = skull.getItemMeta();
            if (!(meta instanceof SkullMeta skullMeta)) {
                return null;
            }

            // Cas spécial : "%player%" => tête du joueur qui voit le menu
            if (viewer != null && owner.equalsIgnoreCase("%player%")) {
                skullMeta.setOwnerProfile(viewer.getPlayerProfile());
                skull.setItemMeta(skullMeta);
                return skull;
            }

            // Cas normal : un pseudo ou un UUID
            OfflinePlayer offline = Bukkit.getOfflinePlayer(owner);
            if (offline != null) {
                skullMeta.setOwningPlayer(offline);
                skull.setItemMeta(skullMeta);
                return skull;
            }
            return null;
        }

        // 2) skull-texture renseigné
        if (texture != null && !texture.isEmpty()) {

            // 2.a) ID HeadDatabase (numérique) si le plugin est présent
            if (isNumeric(texture) && Bukkit.getPluginManager().getPlugin("HeadDatabase") != null) {
                ItemStack hdbHead = tryHeadDatabase(texture);
                if (hdbHead != null) {
                    return hdbHead;
                }
            }

            // 2.b) URL directe (au cas où tu mets directement une URL de skin)
            if (texture.startsWith("http://") || texture.startsWith("https://")) {
                return createHeadFromUrl(texture);
            }

            // 2.c) Base64 comme sur minecraft-heads.com
            if (looksLikeBase64(texture)) {
                ItemStack base64Head = createHeadFromBase64(texture);
                if (base64Head != null) {
                    return base64Head;
                }
            }
        }

        return null;
    }

    private String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private boolean isNumeric(String s) {
        return s != null && s.matches("\\d+");
    }

    /**
     * Heuristique simple pour dire "ça ressemble à un gros base64 de texture".
     */
    private boolean looksLikeBase64(String s) {
        return s != null && s.length() > 40 && s.matches("^[A-Za-z0-9+/=]+$");
    }

    /**
     * Essaye de récupérer une tête via HeadDatabase si présent.
     * On utilise la réflexion pour ne pas obliger à ajouter l'API en dépendance.
     */
    private ItemStack tryHeadDatabase(String id) {
        try {
            Plugin hdbPlugin = Bukkit.getPluginManager().getPlugin("HeadDatabase");
            if (hdbPlugin == null || !hdbPlugin.isEnabled()) {
                return null;
            }

            Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
            Object api = apiClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Method method = apiClass.getMethod("getItemHead", String.class);
            Object result = method.invoke(api, id);

            if (result instanceof ItemStack stack) {
                return stack.clone();
            }
        } catch (ClassNotFoundException ignored) {
            // API pas présent, on ignore
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Erreur lors de la récupération d'une tête HeadDatabase pour l'id " + id, t);
        }
        return null;
    }

    /**
     * Crée une tête à partir d'une URL de skin (textures.minecraft.net/...).
     */
    private ItemStack createHeadFromUrl(String url) {
        try {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = skull.getItemMeta();
            if (!(meta instanceof SkullMeta skullMeta)) {
                return skull;
            }

            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), null);
            PlayerTextures textures = profile.getTextures();

            URL skinUrl = new URL(url);
            textures.setSkin(skinUrl);
            profile.setTextures(textures);

            skullMeta.setOwnerProfile(profile);
            skull.setItemMeta(skullMeta);
            return skull;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Erreur lors de la création d'une tête custom à partir de l'URL : " + url, t);
            return null;
        }
    }

    /**
     * Crée une tête à partir d'une valeur base64 du style :
     * eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUv......In19fQ==
     */
    private ItemStack createHeadFromBase64(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            String json = new String(decoded, StandardCharsets.UTF_8);

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures == null) return null;

            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null || !skin.has("url")) return null;

            String url = skin.get("url").getAsString();
            return createHeadFromUrl(url);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[Menu] Impossible de décoder skull-texture (base64)", e);
            return null;
        }
    }
}
