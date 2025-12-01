package fr.batistou15.prestigehoe.skin;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.crop.CropDefinition;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class SkinManager {

    private final PrestigeHoePlugin plugin;
    private final Map<String, SkinDefinition> skins = new LinkedHashMap<>();
    private SkinDefinition defaultSkin;

    public SkinManager(PrestigeHoePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        skins.clear();
        defaultSkin = null;

        File file = new File(plugin.getDataFolder(), "skins.yml");
        if (!file.exists()) {
            plugin.getLogger().warning("[SkinManager] skins.yml introuvable, aucun skin chargé.");
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("skins");
        if (root == null) {
            plugin.getLogger().warning("[SkinManager] Section 'skins' absente dans skins.yml");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sSec = root.getConfigurationSection(id);
            if (sSec == null) continue;

            SkinDefinition def = new SkinDefinition(id);

            boolean enabled = sSec.getBoolean("enabled", true);
            def.setEnabled(enabled);

            String displayName = sSec.getString("display-name", "&f" + id);
            def.setDisplayName(displayName);

            // Icône
            Material iconMat = Material.DIAMOND_HOE;
            ConfigurationSection iconSec = sSec.getConfigurationSection("icon");
            if (iconSec != null) {
                String matName = iconSec.getString("material", "DIAMOND_HOE");
                if (matName != null) {
                    Material tmp = Material.matchMaterial(matName.toUpperCase(Locale.ROOT));
                    if (tmp != null && tmp.isItem()) {
                        iconMat = tmp;
                    }
                }
            }
            def.setIconMaterial(iconMat);

            // Matériau réel de la houe = icône par défaut
            def.setHoeMaterial(iconMat);

            // Custom model data éventuel
            ConfigurationSection modelSec = sSec.getConfigurationSection("model");
            if (modelSec != null) {
                int cmd = modelSec.getInt("custom-model-data", 0);
                def.setHoeCustomModelData(cmd);
            }

            // Bonuses globaux
            ConfigurationSection bonusSec = sSec.getConfigurationSection("bonuses");
            if (bonusSec != null) {
                def.setMoneyMultiplier(bonusSec.getDouble("money-multiplier", 1.0));
                def.setEssenceMultiplier(bonusSec.getDouble("essence-multiplier", 1.0));
                def.setXpHoeMultiplier(bonusSec.getDouble("xp-hoe-multiplier", 1.0));
                def.setXpPlayerMultiplier(bonusSec.getDouble("xp-player-multiplier", 1.0));
                def.setJobXpMultiplier(bonusSec.getDouble("job-xp-multiplier", 1.0));
                def.setEnchantProcMultiplier(bonusSec.getDouble("enchant-proc-multiplier", 1.0));

                // crop-bonuses
                ConfigurationSection cropBonusSec = bonusSec.getConfigurationSection("crop-bonuses");
                if (cropBonusSec != null) {
                    for (String cropId : cropBonusSec.getKeys(false)) {
                        ConfigurationSection cbSec = cropBonusSec.getConfigurationSection(cropId);
                        if (cbSec == null) continue;

                        SkinDefinition.CropBonus cb = new SkinDefinition.CropBonus();
                        cb.setMoneyMultiplier(cbSec.getDouble("money-multiplier", 1.0));
                        cb.setEssenceMultiplier(cbSec.getDouble("essence-multiplier", 1.0));
                        cb.setXpHoeMultiplier(cbSec.getDouble("xp-hoe-multiplier", 1.0));
                        cb.setXpPlayerMultiplier(cbSec.getDouble("xp-player-multiplier", 1.0));
                        cb.setJobXpMultiplier(cbSec.getDouble("job-xp-multiplier", 1.0));
                        cb.setEnchantProcMultiplier(cbSec.getDouble("enchant-proc-multiplier", 1.0));

                        def.addCropBonus(cropId, cb);
                    }
                }
            }

            // Requirements
            ConfigurationSection reqSec = sSec.getConfigurationSection("requirements");
            if (reqSec != null) {
                SkinDefinition.Requirements req = new SkinDefinition.Requirements();
                req.setMinPrestige(reqSec.getInt("min-prestige", 0));
                req.setMinTotalCrops(reqSec.getLong("min-total-crops", 0L));
                req.setRequiredPermission(reqSec.getString("required-permission", ""));
                def.setRequirements(req);
            }

            // Cost
            ConfigurationSection costSec = sSec.getConfigurationSection("cost");
            if (costSec != null) {
                SkinDefinition.Cost cost = new SkinDefinition.Cost();
                cost.setMoney(costSec.getDouble("money", 0.0));
                cost.setEssence(costSec.getDouble("essence", 0.0));
                def.setCost(cost);
            }

            // Lore templates (locked / unlocked)
            ConfigurationSection loreSec = sSec.getConfigurationSection("lore");
            if (loreSec != null) {
                def.setLockedLoreTemplate(loreSec.getStringList("locked"));
                def.setUnlockedLoreTemplate(loreSec.getStringList("unlocked"));
            }

            skins.put(def.getId(), def);

            if (defaultSkin == null || "default".equalsIgnoreCase(def.getId())) {
                defaultSkin = def;
            }
        }

        plugin.getLogger().info("[PrestigeHoe] [SkinManager] Skins chargés: " + skins.keySet());
        if (defaultSkin == null) {
            plugin.getLogger().warning("[SkinManager] Aucun skin 'default' trouvé, le premier skin sera utilisé comme fallback.");
        }
    }

    public Map<String, SkinDefinition> getAllSkins() {
        return Collections.unmodifiableMap(skins);
    }

    public SkinDefinition getSkin(String id) {
        if (id == null) return null;
        return skins.get(id.toLowerCase(Locale.ROOT));
    }

    public SkinDefinition getSkinOrDefault(String id) {
        SkinDefinition def = getSkin(id);
        if (def != null && def.isEnabled()) {
            return def;
        }
        if (defaultSkin != null && defaultSkin.isEnabled()) {
            return defaultSkin;
        }
        // fallback ultime
        return skins.values().stream()
                .filter(SkinDefinition::isEnabled)
                .findFirst()
                .orElse(null);
    }

    public SkinDefinition getActiveSkin(PlayerProfile profile) {
        if (profile == null) return defaultSkin;
        String skinId = profile.getActiveSkinId();
        return getSkinOrDefault(skinId);
    }

    // ========= Multiplicateurs prêts à l'emploi =========

    private String cropIdOrNull(CropDefinition def) {
        return def != null ? def.getId() : null;
    }

    private double safe(double value) {
        return value <= 0.0D ? 1.0D : value;
    }

    // --- Multiplicateurs par CROP (utilisés dans AoE, etc.) ---

    public double getMoneyMultiplier(PlayerProfile profile, CropDefinition crop) {
        SkinDefinition skin = getActiveSkin(profile);
        if (skin == null) return 1.0D;
        return safe(skin.getMoneyMultiplierForCrop(cropIdOrNull(crop)));
    }

    public double getEssenceMultiplier(PlayerProfile profile, CropDefinition crop) {
        SkinDefinition skin = getActiveSkin(profile);
        if (skin == null) return 1.0D;
        return safe(skin.getEssenceMultiplierForCrop(cropIdOrNull(crop)));
    }

    public double getXpHoeMultiplier(PlayerProfile profile, CropDefinition crop) {
        SkinDefinition skin = getActiveSkin(profile);
        if (skin == null) return 1.0D;
        return safe(skin.getXpHoeMultiplierForCrop(cropIdOrNull(crop)));
    }

    public double getXpPlayerMultiplier(PlayerProfile profile, CropDefinition crop) {
        SkinDefinition skin = getActiveSkin(profile);
        if (skin == null) return 1.0D;
        return safe(skin.getXpPlayerMultiplierForCrop(cropIdOrNull(crop)));
    }

    public double getJobXpMultiplier(PlayerProfile profile, CropDefinition crop) {
        SkinDefinition skin = getActiveSkin(profile);
        if (skin == null) return 1.0D;
        return safe(skin.getJobXpMultiplierForCrop(cropIdOrNull(crop)));
    }

    public double getEnchantProcMultiplier(PlayerProfile profile, CropDefinition crop) {
        SkinDefinition skin = getActiveSkin(profile);
        if (skin == null) return 1.0D;
        return safe(skin.getEnchantProcMultiplierForCrop(cropIdOrNull(crop)));
    }

    // --- Multiplicateurs GLOBAUX (sans notion de crop) ---

    public double getMoneyMultiplier(PlayerProfile profile) {
        return getMoneyMultiplier(profile, null);
    }

    public double getEssenceMultiplier(PlayerProfile profile) {
        return getEssenceMultiplier(profile, null);
    }

    public double getHoeXpMultiplier(PlayerProfile profile) {
        return getXpHoeMultiplier(profile, null);
    }

    public double getPlayerXpMultiplier(PlayerProfile profile) {
        return getXpPlayerMultiplier(profile, null);
    }

    public double getJobXpMultiplier(PlayerProfile profile) {
        SkinDefinition skin = getActiveSkin(profile);
        if (skin == null) return 1.0D;
        return safe(skin.getJobXpMultiplier());
    }

    public double getEnchantProcMultiplier(PlayerProfile profile) {
        SkinDefinition skin = getActiveSkin(profile);
        if (skin == null) return 1.0D;
        return safe(skin.getEnchantProcMultiplier());
    }
}
