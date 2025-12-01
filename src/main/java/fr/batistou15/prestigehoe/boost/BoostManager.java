package fr.batistou15.prestigehoe.boost;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class BoostManager {

    // =============================
    //  POJOs & enums
    // =============================

    public enum BoostType {
        ESSENCE,
        MONEY,
        XP_HOE,
        XP_PLAYER,
        JOB_XP
    }

    public enum BoostMode {
        ADD,        // +value (ex: +0.3 => +30%)
        MULTIPLY    // x(1+value) (ex: 1.0 => x2)
    }

    public static class BoostIconConfig {
        private final String material;
        private final int customModelData;
        private final String itemsAdderId;
        private final String skullOwner;
        private final String skullTexture;

        public BoostIconConfig(String material,
                               int customModelData,
                               String itemsAdderId,
                               String skullOwner,
                               String skullTexture) {
            this.material = material;
            this.customModelData = customModelData;
            this.itemsAdderId = itemsAdderId;
            this.skullOwner = skullOwner;
            this.skullTexture = skullTexture;
        }

        public String getMaterial() {
            return material;
        }

        public int getCustomModelData() {
            return customModelData;
        }

        public String getItemsAdderId() {
            return itemsAdderId;
        }

        public String getSkullOwner() {
            return skullOwner;
        }

        public String getSkullTexture() {
            return skullTexture;
        }
    }

    public static class BoostDefinition {
        private final String id;
        private final boolean enabled;
        private final String displayName;
        private final List<String> lore;
        private final BoostIconConfig icon;
        private final BoostType type;
        private final BoostMode mode;
        private final double value;
        private final int durationSeconds;

        public BoostDefinition(String id,
                               boolean enabled,
                               String displayName,
                               List<String> lore,
                               BoostIconConfig icon,
                               BoostType type,
                               BoostMode mode,
                               double value,
                               int durationSeconds) {
            this.id = id;
            this.enabled = enabled;
            this.displayName = displayName;
            this.lore = lore != null ? new ArrayList<>(lore) : Collections.emptyList();
            this.icon = icon;
            this.type = type;
            this.mode = mode;
            this.value = value;
            this.durationSeconds = durationSeconds;
        }

        public String getId() {
            return id;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getLore() {
            return lore;
        }

        public BoostIconConfig getIcon() {
            return icon;
        }

        public BoostType getType() {
            return type;
        }

        public BoostMode getMode() {
            return mode;
        }

        public double getValue() {
            return value;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }
    }

    public static class ActiveBoost {
        private final BoostDefinition definition;
        private final long startMillis;
        private final long endMillis;

        public ActiveBoost(BoostDefinition definition, long startMillis, long endMillis) {
            this.definition = definition;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }

        public BoostDefinition getDefinition() {
            return definition;
        }

        public long getStartMillis() {
            return startMillis;
        }

        public long getEndMillis() {
            return endMillis;
        }

        public boolean isExpired(long now) {
            return now >= endMillis;
        }

        public long getRemainingMillis(long now) {
            return Math.max(0L, endMillis - now);
        }
    }

    // =============================
    //  Champs
    // =============================

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;

    // boosts définis dans boosts.yml : id -> definition
    private final Map<String, BoostDefinition> definitions = new HashMap<>();

    // boosts actifs par joueur
    private final Map<UUID, List<ActiveBoost>> activeBoosts = new ConcurrentHashMap<>();

    // clé PDC pour l'item "boost"
    private final NamespacedKey boostKey;

    // id de la task d’update (actionbar + expiration)
    private int boostTaskId = -1;

    public BoostManager(PrestigeHoePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.boostKey = new NamespacedKey(plugin, "boost-id");

        reload();
        startBoostTickTask();
    }

    // =============================
    //  Tâche périodique
    // =============================

    /**
     * Tâche qui :
     * - nettoie les boosts expirés
     * - met à jour l’actionbar des joueurs boostés (1x / seconde)
     */
    private void startBoostTickTask() {
        if (boostTaskId != -1) {
            Bukkit.getScheduler().cancelTask(boostTaskId);
        }

        boostTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin,
                this::tick,
                20L,   // démarrage après 1 seconde
                20L    // toutes les 1 seconde
        );
    }

    private void tick() {
        long now = System.currentTimeMillis();

        // 1) Nettoyage + log des expirations
        for (Iterator<Map.Entry<UUID, List<ActiveBoost>>> it = activeBoosts.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, List<ActiveBoost>> entry = it.next();
            UUID uuid = entry.getKey();
            List<ActiveBoost> list = entry.getValue();

            Iterator<ActiveBoost> itBoost = list.iterator();
            while (itBoost.hasNext()) {
                ActiveBoost boost = itBoost.next();
                if (boost.isExpired(now)) {
                    itBoost.remove();

                    // Log admin de l'expiration
                    String playerName = Optional.ofNullable(
                            plugin.getServer().getOfflinePlayer(uuid).getName()
                    ).orElse(uuid.toString());

                    plugin.getLogger().info(
                            "[Boost] Le boost '" + boost.getDefinition().getId() +
                                    "' de " + playerName + " est terminé."
                    );
                }
            }

            if (list.isEmpty()) {
                it.remove();
            }
        }

        // 2) Actionbar pour les joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            List<ActiveBoost> list = activeBoosts.get(player.getUniqueId());
            if (list == null || list.isEmpty()) {
                continue;
            }

            ActiveBoost main = getMainBoost(list, now);
            if (main == null) continue;

            String msg = buildBoostActionBar(main, now);
            player.sendActionBar(Component.text(MessageUtil.color(msg)));
        }
    }

    /**
     * Choix du "boost principal" à afficher en actionbar.
     * Ici : le boost qui se termine le plus tard (ou le dernier ajouté).
     */
    private ActiveBoost getMainBoost(List<ActiveBoost> list, long now) {
        ActiveBoost best = null;
        long bestRemaining = -1L;

        for (ActiveBoost boost : list) {
            if (boost.isExpired(now)) continue;
            long rem = boost.getRemainingMillis(now);
            if (rem > bestRemaining) {
                bestRemaining = rem;
                best = boost;
            }
        }
        return best;
    }

    private String buildBoostActionBar(ActiveBoost boost, long now) {
        long seconds = boost.getRemainingMillis(now) / 1000L;
        String duration = formatDuration(seconds);
        String name = MessageUtil.color(boost.getDefinition().getDisplayName());

        return "&bBoost &f" + name + " &7(" + duration + ")";
    }

    private String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        long m = seconds / 60;
        long s = seconds % 60;
        if (m > 0) {
            if (s > 0) {
                return m + "m" + s + "s";
            }
            return m + "m";
        }
        return s + "s";
    }

    // =============================
    //  Reload des boosts.yml
    // =============================

    public void reload() {
        definitions.clear();

        FileConfiguration boostsCfg = configManager.getBoostsConfig();
        if (boostsCfg == null) {
            plugin.getLogger().warning("[Boosts] boosts.yml introuvable ou non chargé.");
            return;
        }

        ConfigurationSection root = boostsCfg.getConfigurationSection("boosts");
        if (root == null) {
            plugin.getLogger().warning("[Boosts] Section 'boosts' absente dans boosts.yml");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            boolean enabled = sec.getBoolean("enabled", true);
            String displayName = sec.getString("display-name", id);

            List<String> lore = sec.getStringList("lore");

            // Icon
            ConfigurationSection iconSec = sec.getConfigurationSection("icon");
            String materialName = "PAPER";
            int cmd = 0;
            String itemsAdderId = "";
            String skullOwner = "";
            String skullTexture = "";

            if (iconSec != null) {
                materialName = iconSec.getString("material", "PAPER");
                cmd = iconSec.getInt("custom-model-data", 0);
                itemsAdderId = iconSec.getString("itemsadder-id", "");
                skullOwner = iconSec.getString("skull-owner", "");
                skullTexture = iconSec.getString("skull-texture", "");
            }

            BoostIconConfig icon = new BoostIconConfig(
                    materialName,
                    cmd,
                    itemsAdderId,
                    skullOwner,
                    skullTexture
            );

            // Type & mode
            String typeStr = sec.getString("type", "ESSENCE").toUpperCase(Locale.ROOT);
            String modeStr = sec.getString("mode", "ADD").toUpperCase(Locale.ROOT);

            BoostType type;
            try {
                type = BoostType.valueOf(typeStr);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[Boosts] Type invalide pour " + id + " : " + typeStr + ", fallback ESSENCE.");
                type = BoostType.ESSENCE;
            }

            BoostMode mode;
            try {
                mode = BoostMode.valueOf(modeStr);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[Boosts] Mode invalide pour " + id + " : " + modeStr + ", fallback ADD.");
                mode = BoostMode.ADD;
            }

            double value = sec.getDouble("value", 0.0);
            int duration = sec.getInt("duration-seconds", 600);

            BoostDefinition def = new BoostDefinition(
                    id.toLowerCase(Locale.ROOT),
                    enabled,
                    displayName,
                    lore,
                    icon,
                    type,
                    mode,
                    value,
                    duration
            );

            definitions.put(def.getId(), def);
        }

        plugin.getLogger().info("[Boosts] " + definitions.size() + " boosts chargés.");
    }

    // =============================
    //  API publique - définition
    // =============================

    public BoostDefinition getDefinition(String id) {
        if (id == null) return null;
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<BoostDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    // =============================
    //  API publique - boosts actifs
    // =============================

    public List<ActiveBoost> getActiveBoosts(UUID uuid) {
        return activeBoosts.getOrDefault(uuid, Collections.emptyList());
    }

    public void clearExpired(UUID uuid) {
        List<ActiveBoost> list = activeBoosts.get(uuid);
        if (list == null || list.isEmpty()) return;

        long now = System.currentTimeMillis();
        list.removeIf(ab -> ab.isExpired(now));
        if (list.isEmpty()) {
            activeBoosts.remove(uuid);
        }
    }

    /**
     * Active un boost pour un joueur (UUID).
     * Gère l'ajout dans la map + log admin de l'activation.
     */
    public void applyBoost(UUID uuid, BoostDefinition def) {
        if (uuid == null || def == null) return;

        long now = System.currentTimeMillis();
        long end = now + def.getDurationSeconds() * 1000L;

        ActiveBoost ab = new ActiveBoost(def, now, end);

        activeBoosts.compute(uuid, (u, list) -> {
            if (list == null) list = new ArrayList<>();
            list.add(ab);
            return list;
        });

        // Log admin de l'activation
        String playerName = Optional.ofNullable(
                plugin.getServer().getOfflinePlayer(uuid).getName()
        ).orElse(uuid.toString());

        plugin.getLogger().info(
                "[Boost] " + playerName +
                        " a activé le boost '" + def.getId() + "'" +
                        " (type=" + def.getType() +
                        ", mode=" + def.getMode() +
                        ", value=" + def.getValue() +
                        ", durée=" + def.getDurationSeconds() + "s)"
        );
    }

    public void applyBoost(Player player, String boostId) {
        if (player == null || boostId == null || boostId.isEmpty()) return;
        BoostDefinition def = getDefinition(boostId);
        if (def == null || !def.isEnabled()) {
            MessageUtil.sendPlain(player, "§cBoost introuvable ou désactivé: §e" + boostId);
            return;
        }

        applyBoost(player.getUniqueId(), def);

        MessageUtil.sendPlain(player,
                "§aBoost §e" + def.getDisplayName() + " §aactivé pour §e" + def.getDurationSeconds() + "s§a.");
    }

    // =============================
    //  Construction de l'item boost
    // =============================

    /**
     * Donne au joueur un item "boost" physique (pour crates, rewards...).
     */
    public void giveBoostItem(Player player, String boostId, int amount) {
        if (player == null || boostId == null || boostId.isEmpty()) return;
        if (amount <= 0) amount = 1;

        BoostDefinition def = getDefinition(boostId);
        if (def == null || !def.isEnabled()) {
            MessageUtil.sendPlain(player, "§cBoost introuvable ou désactivé: §e" + boostId);
            return;
        }

        ItemStack item = buildBoostItem(def, amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            // drop au sol si inventaire plein
            leftover.values().forEach(is -> player.getWorld().dropItemNaturally(player.getLocation(), is));
        }
    }

    private ItemStack buildBoostItem(BoostDefinition def, int amount) {
        BoostIconConfig icon = def.getIcon();

        // 1) ItemsAdder si configuré
        if (icon.getItemsAdderId() != null && !icon.getItemsAdderId().isEmpty()) {
            ItemStack ia = createItemsAdderItem(icon.getItemsAdderId());
            if (ia != null) {
                ia.setAmount(amount);
                applyMeta(ia, def);
                return ia;
            }
        }

        // 2) Material
        Material mat = Material.matchMaterial(icon.getMaterial());
        if (mat == null) {
            mat = Material.PAPER;
        }

        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();

        // 2.a) Tête joueur (skull-owner)
        if (mat == Material.PLAYER_HEAD) {
            String owner = icon.getSkullOwner();
            String texture = icon.getSkullTexture();

            if (texture != null && !texture.isEmpty()) {
                // NOTE: pas de support direct base64 sans Authlib
                plugin.getLogger().warning("[Boosts] skull-texture configuré pour le boost '" + def.getId() +
                        "' mais les textures base64 ne sont pas supportées dans cette version. " +
                        "Utilise ItemsAdder (itemsadder-id) ou skull-owner (pseudo) à la place.");
            }

            if (owner != null && !owner.isEmpty() && meta instanceof SkullMeta skullMeta) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(owner);
                skullMeta.setOwningPlayer(off);
                meta = skullMeta;
            }
        }

        // 3) custom model data
        if (icon.getCustomModelData() > 0) {
            meta.setCustomModelData(icon.getCustomModelData());
        }

        // 4) Nom & lore
        meta.setDisplayName(MessageUtil.color(def.getDisplayName()));
        List<String> coloredLore = new ArrayList<>();
        for (String line : def.getLore()) {
            coloredLore.add(MessageUtil.color(line));
        }
        meta.setLore(coloredLore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // 5) Tag PDC pour connaître l'id du boost
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(boostKey, PersistentDataType.STRING, def.getId());

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Applique nom, lore, PDC, etc. sur un ItemStack déjà existant (ex: ItemsAdder).
     */
    private void applyMeta(ItemStack item, BoostDefinition def) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.setDisplayName(MessageUtil.color(def.getDisplayName()));
        List<String> coloredLore = new ArrayList<>();
        for (String line : def.getLore()) {
            coloredLore.add(MessageUtil.color(line));
        }
        meta.setLore(coloredLore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // PDC
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(boostKey, PersistentDataType.STRING, def.getId());

        item.setItemMeta(meta);
    }

    /**
     * Support ItemsAdder via réflexion → pas besoin de dépendance Gradle.
     */
    private ItemStack createItemsAdderItem(String iaId) {
        try {
            Class<?> clazz = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = clazz.getMethod("getInstance", String.class);
            Object cs = getInstance.invoke(null, iaId);
            if (cs == null) {
                plugin.getLogger().warning("[Boosts] ItemsAdder CustomStack introuvable pour id: " + iaId);
                return null;
            }
            Method getItemStack = clazz.getMethod("getItemStack");
            Object result = getItemStack.invoke(cs);
            if (result instanceof ItemStack is) {
                return is.clone();
            }
        } catch (ClassNotFoundException e) {
            // ItemsAdder pas installé → silencieux
            plugin.getLogger().fine("[Boosts] ItemsAdder non présent, impossible de créer l'item: " + iaId);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Boosts] Erreur lors de la création d'un item ItemsAdder pour id: " + iaId, t);
        }
        return null;
    }

    // =============================
    //  Utilitaire : détection item boost
    // =============================

    /**
     * Tente de récupérer l'id de boost depuis un ItemStack (via PDC).
     */
    public String getBoostIdFromItem(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(boostKey, PersistentDataType.STRING);
    }

    /**
     * Consomme l'item dans la main et active le boost correspondant.
     * - Consommation 1 item
     * - Activation du boost (map + log)
     * - Message de confirmation
     * - L'actionbar sera gérée automatiquement via la task périodique.
     */
    public boolean consumeAndActivateBoost(Player player, ItemStack item) {
        if (player == null || item == null) return false;

        String boostId = getBoostIdFromItem(item);
        if (boostId == null || boostId.isEmpty()) {
            return false;
        }

        BoostDefinition def = getDefinition(boostId);
        if (def == null || !def.isEnabled()) {
            MessageUtil.sendPlain(player, "§cCe boost n'est plus valide.");
            return false;
        }

        // Consommer 1 item
        PlayerInventory inv = player.getInventory();
        int slot = inv.getHeldItemSlot();
        ItemStack hand = inv.getItem(slot);
        if (hand == null || hand.getType().isAir()) return false;

        if (hand.getAmount() <= 1) {
            inv.setItem(slot, null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            inv.setItem(slot, hand);
        }

        // Activer le boost (avec log admin)
        applyBoost(player.getUniqueId(), def);

        MessageUtil.sendPlain(player,
                "§aBoost §e" + def.getDisplayName() + " §aactivé pour §e" + def.getDurationSeconds() + "s§a.");
        return true;
    }
    /**
     * Retourne le multiplicateur global pour un joueur et un type de boost donné.
     *
     * - Si aucun boost actif : 1.0
     * - ADD : additionne les valeurs (ex: +0.3 -> +30%)
     * - MULTIPLY : multiplie les (1 + value) (ex: 1.0 -> x2)
     *
     * Exemple :
     *  - ADD:  +0.3  (+30%)
     *  - MULT: 1.0   (x2)
     *  => résultat final = (1 + 0.3) * (1 + 1.0) = 1.3 * 2 = 2.6 (soit x2.6)
     */
    public double getBoostMultiplier(UUID uuid, BoostType type) {
        if (uuid == null || type == null) return 1.0D;

        // Nettoie les boosts expirés pour ce joueur
        clearExpired(uuid);

        var list = activeBoosts.get(uuid);
        if (list == null || list.isEmpty()) {
            return 1.0D;
        }

        double add = 0.0D;   // somme des ADD
        double mul = 1.0D;   // produit des MULTIPLY (1+value)

        for (ActiveBoost ab : list) {
            if (ab == null) continue;
            BoostDefinition def = ab.getDefinition();
            if (def == null) continue;
            if (def.getType() != type) continue;

            if (def.getMode() == BoostMode.ADD) {
                add += def.getValue();
            } else if (def.getMode() == BoostMode.MULTIPLY) {
                mul *= (1.0D + def.getValue());
            }
        }

        // On applique d'abord les MULTIPLY, puis on ajoute les ADD
        return mul * (1.0D + add);
    }

}
