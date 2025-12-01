package fr.batistou15.prestigehoe.data;

import java.util.*;

/**
 * Profil complet d'un joueur pour PrestigeHoe.
 */
public class PlayerProfile {

    private UUID uuid;

    // Monnaie "Essence"
    private double essence = 0.0;

    // Prestige Tokens (pour le Prestige Shop)
    private long prestigeTokens = 0L;

    // Stats globales de farm (très simplifiées pour l'instant)
    private Map<String, Long> cropsBroken = new HashMap<>(); // key = id crop (ex: "wheat")

    // Argent total généré par la houe (stat)
    private double totalMoneyEarned = 0.0;

    // Essence totale gagnée (stat)
    private double totalEssenceEarned = 0.0;

    // ✅ Nombre total d'items vendus via Autosell
    private long totalAutosellItems = 0L;

    // ✅ XP totale de la houe gagnée (pour les recaps)
    private double totalHoeXpGained = 0.0;

    // ✅ XP vanilla totale gagnée via la houe (pour les recaps)
    private int totalPlayerXpGained = 0;

    // Boosts permanents achetés (ids de perks du prestige shop)
    private Set<String> permanentPerks = new HashSet<>();

    // Skins débloqués
    private Set<String> unlockedSkins = new HashSet<>();

    // Skin actuellement équipé (pour la houe principale)
    private String activeSkinId = "default";

    // === Perks de prestige (id -> niveau) ===
    private final Map<String, Integer> prestigePerks = new HashMap<>();

    // Paramètres joueur
    private boolean recapEnabled = true;

    /**
     * Ancien flag global pour les messages d'enchant.
     * On le garde comme master switch général.
     */
    private boolean enchantMessagesEnabledGlobal = true;

    // Vitesse de marche de base du joueur (pour les perks de SPEED)
    private float baseWalkSpeed = 0.2f;

    // 🔔 Flags globaux d'affichage
    // → messages "texte" (chat / proc / infos)
    private boolean chatNotificationsEnabled = true;
    private boolean actionBarNotificationsEnabled = true;
    private boolean titleNotificationsEnabled = true;

    // → messages de proc d'enchant (TokenFinder, EssencePouch, custom, etc.)
    private boolean enchantProcMessagesEnabledGlobal = true;

    // → messages d'upgrade d'enchant (upgrade-notification)
    private boolean enchantUpgradeMessagesEnabledGlobal = true;

    // → message de level-up de la houe
    private boolean levelUpMessageEnabled = true;

    // 🔔 Toggles par enchant (proc / upgrade)
    // key = enchantId en minuscule
    private final Map<String, Boolean> enchantProcMessageToggles = new HashMap<>();
    private final Map<String, Boolean> enchantUpgradeMessageToggles = new HashMap<>();

    // Données de la PrestigeHoe principale du joueur
    private HoeData hoeData = new HoeData();

    // Constructeur vide pour Gson
    public PlayerProfile() {
    }

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
        this.unlockedSkins.add("default"); // skin par défaut débloqué
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    // ---------- Essence / Tokens ----------

    public double getEssence() {
        return essence;
    }

    public void setEssence(double value) {
        this.essence = Math.max(0.0D, value);
    }

    /**
     * ⚠️ C'est cette méthode qui DOIT être utilisée partout
     * (farm normal + essence pouch) pour que le récap voie les gains.
     */
    public void addEssence(double amount) {
        if (amount <= 0.0D) return;

        this.essence += amount;
        this.totalEssenceEarned += amount;  // ⬅ IMPORTANT pour le récap
    }

    public boolean removeEssence(double amount) {
        if (amount <= 0.0D) return true;
        if (this.essence < amount) return false;
        this.essence -= amount;
        return true;
    }

    public long getPrestigeTokens() {
        return prestigeTokens;
    }

    public void setPrestigeTokens(long prestigeTokens) {
        this.prestigeTokens = Math.max(0L, prestigeTokens);
    }

    public void addPrestigeTokens(long amount) {
        if (amount <= 0) return;
        this.prestigeTokens += amount;
    }

    public float getBaseWalkSpeed() {
        return baseWalkSpeed;
    }

    public void setBaseWalkSpeed(float baseWalkSpeed) {
        this.baseWalkSpeed = baseWalkSpeed;
    }

    // ---------- Stats de farm ----------

    public Map<String, Long> getCropsBroken() {
        return cropsBroken;
    }

    public void setCropsBroken(Map<String, Long> cropsBroken) {
        this.cropsBroken = cropsBroken != null ? cropsBroken : new HashMap<>();
    }

    public void incrementCropBroken(String cropId, long amount) {
        if (cropId == null || amount <= 0) return;
        this.cropsBroken.merge(cropId, amount, Long::sum);
    }

    /**
     * Nombre total de crops cassées (toutes cultures confondues).
     * Utilisé pour %crop_break% dans le lore.
     */
    public long getTotalCropsBroken() {
        long total = 0L;
        if (cropsBroken != null) {
            for (long v : cropsBroken.values()) {
                total += v;
            }
        }
        return total;
    }

    public double getTotalMoneyEarned() {
        return totalMoneyEarned;
    }

    public void addMoneyEarned(double amount) {
        if (amount <= 0) return;
        this.totalMoneyEarned += amount;
    }

    /**
     * Helper historique : si tu l'utilises encore, il repasse par addEssence
     * pour garder totalEssenceEarned synchro.
     */
    public void addEssenceEarned(double amount) {
        addEssence(amount);
    }

    public double getTotalEssenceEarned() {
        return totalEssenceEarned;
    }

    // ✅ Autosell : total items vendus

    public long getTotalAutosellItems() {
        return totalAutosellItems;
    }

    public void setTotalAutosellItems(long totalAutosellItems) {
        this.totalAutosellItems = Math.max(0L, totalAutosellItems);
    }

    public void addAutosellItems(long amount) {
        if (amount <= 0) return;
        this.totalAutosellItems += amount;
    }

    // ✅ Stats d'XP (pour les recaps)

    public double getTotalHoeXpGained() {
        return totalHoeXpGained;
    }

    public void addHoeXpGained(double amount) {
        if (amount <= 0) return;
        this.totalHoeXpGained += amount;
    }

    public int getTotalPlayerXpGained() {
        return totalPlayerXpGained;
    }

    public void addPlayerXpGained(int amount) {
        if (amount <= 0) return;
        this.totalPlayerXpGained += amount;
    }

    // ---------- Perks de prestige ----------

    public int getPrestigePerkLevel(String perkId) {
        if (perkId == null) return 0;
        return prestigePerks.getOrDefault(perkId.toLowerCase(Locale.ROOT), 0);
    }

    public void setPrestigePerkLevel(String perkId, int level) {
        if (perkId == null) return;
        String key = perkId.toLowerCase(Locale.ROOT);
        if (level <= 0) {
            prestigePerks.remove(key);
        } else {
            prestigePerks.put(key, level);
        }
    }

    public Map<String, Integer> getPrestigePerks() {
        return prestigePerks;
    }

// ---------- Perks / Skins ----------

    public Set<String> getPermanentPerks() {
        return permanentPerks;
    }

    public void setPermanentPerks(Set<String> permanentPerks) {
        this.permanentPerks = permanentPerks != null ? permanentPerks : new HashSet<>();
    }

    public boolean hasPerk(String perkId) {
        return permanentPerks.contains(perkId);
    }

    public Set<String> getUnlockedSkins() {
        return unlockedSkins;
    }

    public void setUnlockedSkins(Set<String> unlockedSkins) {
        // On normalise tout en lowercase
        this.unlockedSkins = new HashSet<>();
        if (unlockedSkins != null) {
            for (String id : unlockedSkins) {
                if (id != null && !id.isEmpty()) {
                    this.unlockedSkins.add(id.toLowerCase(Locale.ROOT));
                }
            }
        }

        // Sécurité : s'il n'y a rien, on s'assure que "default" est débloqué
        if (this.unlockedSkins.isEmpty()) {
            this.unlockedSkins.add("default");
        }
    }

    public boolean isSkinUnlocked(String skinId) {
        if (skinId == null) return false;
        return unlockedSkins.contains(skinId.toLowerCase(Locale.ROOT));
    }

    public void unlockSkin(String skinId) {
        if (skinId == null) return;
        unlockedSkins.add(skinId.toLowerCase(Locale.ROOT));
    }


    public String getActiveSkinId() {
        return activeSkinId;
    }

    public void setActiveSkinId(String activeSkinId) {
        this.activeSkinId = activeSkinId != null ? activeSkinId : "default";
    }

    // ---------- Paramètres joueur ----------

    public boolean isRecapEnabled() {
        return recapEnabled;
    }

    public void setRecapEnabled(boolean recapEnabled) {
        this.recapEnabled = recapEnabled;
    }

    /**
     * Ancien flag global (master switch pour les messages d'enchant).
     */
    public boolean isEnchantMessagesEnabledGlobal() {
        return enchantMessagesEnabledGlobal;
    }

    public void setEnchantMessagesEnabledGlobal(boolean enchantMessagesEnabledGlobal) {
        this.enchantMessagesEnabledGlobal = enchantMessagesEnabledGlobal;
    }

    // ---------- Paramètres globaux d'affichage des messages ----------

    public boolean isChatNotificationsEnabled() {
        return chatNotificationsEnabled;
    }

    public void setChatNotificationsEnabled(boolean chatNotificationsEnabled) {
        this.chatNotificationsEnabled = chatNotificationsEnabled;
    }

    public boolean isActionBarNotificationsEnabled() {
        return actionBarNotificationsEnabled;
    }

    public void setActionBarNotificationsEnabled(boolean actionBarNotificationsEnabled) {
        this.actionBarNotificationsEnabled = actionBarNotificationsEnabled;
    }

    public boolean isTitleNotificationsEnabled() {
        return titleNotificationsEnabled;
    }

    public void setTitleNotificationsEnabled(boolean titleNotificationsEnabled) {
        this.titleNotificationsEnabled = titleNotificationsEnabled;
    }

    public boolean isEnchantProcMessagesEnabledGlobal() {
        return enchantProcMessagesEnabledGlobal;
    }

    public void setEnchantProcMessagesEnabledGlobal(boolean enchantProcMessagesEnabledGlobal) {
        this.enchantProcMessagesEnabledGlobal = enchantProcMessagesEnabledGlobal;
    }

    public boolean isEnchantUpgradeMessagesEnabledGlobal() {
        return enchantUpgradeMessagesEnabledGlobal;
    }

    public void setEnchantUpgradeMessagesEnabledGlobal(boolean enchantUpgradeMessagesEnabledGlobal) {
        this.enchantUpgradeMessagesEnabledGlobal = enchantUpgradeMessagesEnabledGlobal;
    }

    public boolean isLevelUpMessageEnabled() {
        return levelUpMessageEnabled;
    }

    public void setLevelUpMessageEnabled(boolean levelUpMessageEnabled) {
        this.levelUpMessageEnabled = levelUpMessageEnabled;
    }

    // ---------- Toggles par enchant ----------

    public boolean isEnchantProcMessageEnabled(String enchantId) {
        if (enchantId == null) return true;
        Boolean value = enchantProcMessageToggles.get(enchantId.toLowerCase(Locale.ROOT));
        // Par défaut: activé
        return value == null || value;
    }

    public void setEnchantProcMessageEnabled(String enchantId, boolean enabled) {
        if (enchantId == null) return;
        enchantProcMessageToggles.put(enchantId.toLowerCase(Locale.ROOT), enabled);
    }

    public boolean isEnchantUpgradeMessageEnabled(String enchantId) {
        if (enchantId == null) return true;
        Boolean value = enchantUpgradeMessageToggles.get(enchantId.toLowerCase(Locale.ROOT));
        // Par défaut: activé
        return value == null || value;
    }

    public void setEnchantUpgradeMessageEnabled(String enchantId, boolean enabled) {
        if (enchantId == null) return;
        enchantUpgradeMessageToggles.put(enchantId.toLowerCase(Locale.ROOT), enabled);
    }

    // ---------- Données de houe ----------

    public HoeData getHoeData() {
        if (hoeData == null) {
            hoeData = new HoeData();
        }
        return hoeData;
    }

    public void setHoeData(HoeData hoeData) {
        this.hoeData = hoeData != null ? hoeData : new HoeData();
    }

    /**
     * Reset des "progressions" de farm :
     * - monnaies (Essence, Prestige Tokens)
     * - stats globales (crops, argent, essence, autosell, xp totals)
     *
     * ⚠ Ne touche PAS aux perks permanents / skins / prestigePerks / paramètres.
     */
    public void resetAllProgress() {
        // Monnaies
        this.essence = 0.0D;
        this.prestigeTokens = 0L;

        // Stats globales
        this.totalMoneyEarned = 0.0D;
        this.totalEssenceEarned = 0.0D;
        this.totalAutosellItems = 0L;
        this.totalHoeXpGained = 0.0D;
        this.totalPlayerXpGained = 0;

        if (this.cropsBroken != null) {
            this.cropsBroken.clear();
        }

        // On NE reset PAS :
        // - prestigePerks
        // - permanentPerks
        // - unlockedSkins
        // - activeSkinId
        // - recapEnabled / flags globaux / toggles
    }
}
