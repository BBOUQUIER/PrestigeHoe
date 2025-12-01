package fr.batistou15.prestigehoe.crop;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class CropManager {

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;

    // Par id (wheat, carrot, tube_coral...)
    private final Map<String, CropDefinition> cropsById = new HashMap<>();
    // Par Material (WHEAT, CARROTS, TUBE_CORAL_BLOCK...)
    private final Map<Material, CropDefinition> cropsByMaterial = new HashMap<>();
    // Par Material de "bébé" (DEAD_TUBE_CORAL_FAN, etc.)
    private final Map<Material, CropDefinition> regrowBabyByMaterial = new HashMap<>();

    public CropManager(PrestigeHoePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        reload();
    }

    public void reload() {
        cropsById.clear();
        cropsByMaterial.clear();
        regrowBabyByMaterial.clear();

        FileConfiguration cfg = plugin.getConfigManager().getCropsConfig();
        ConfigurationSection root = cfg.getConfigurationSection("crops");
        if (root == null) {
            plugin.getLogger().warning("[PrestigeHoe] Aucune section 'crops' dans crops.yml");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            String displayName = sec.getString("display-name", id);

            String materialName = sec.getString("material", "WHEAT");
            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[PrestigeHoe] Matérial invalide pour crop '" + id + "': " + materialName);
                continue;
            }

            boolean fullyGrownRequired = sec.getBoolean("fully-grown-required", true);

            double xpHoe = sec.getDouble("xp-hoe", 0.0);
            double xpPlayer = sec.getDouble("xp-player", 0.0);
            double essence = sec.getDouble("essence", 0.0);

            double price = sec.getDouble("price", 0.0);

            String category = sec.getString("category", "default");

            // Requirements simples : niveau de houe / prestige
            ConfigurationSection req = sec.getConfigurationSection("requirements");
            int minHoeLevel = 0;
            int minPrestige = 0;
            if (req != null) {
                minHoeLevel = req.getInt("hoe-level-min", 0);
                minPrestige = req.getInt("prestige-min", 0);
            }

            // Regrow custom
            ConfigurationSection regrowSec = sec.getConfigurationSection("regrow");
            boolean regrowEnabled = false;
            Material babyMaterial = null;
            long regrowDelayTicks = 0L;

            if (regrowSec != null && regrowSec.getBoolean("enabled", false)) {
                regrowEnabled = true;

                String babyMatName = regrowSec.getString("baby-material");
                if (babyMatName != null && !babyMatName.isEmpty()) {
                    try {
                        babyMaterial = Material.valueOf(babyMatName.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("[PrestigeHoe] baby-material invalide pour crop '" + id + "': " + babyMatName);
                        regrowEnabled = false;
                    }
                } else {
                    regrowEnabled = false;
                }

                regrowDelayTicks = regrowSec.getLong("delay-ticks", 600L);
            }

            CropDefinition def = new CropDefinition(
                    id,
                    displayName,
                    material,
                    fullyGrownRequired,
                    xpHoe,
                    xpPlayer,
                    essence,
                    price,
                    category,
                    minHoeLevel,
                    minPrestige,
                    regrowEnabled,
                    babyMaterial,
                    regrowDelayTicks
            );

            cropsById.put(id, def);
            cropsByMaterial.put(material, def);

            if (regrowEnabled && babyMaterial != null) {
                regrowBabyByMaterial.put(babyMaterial, def);
            }
        }

        plugin.getLogger().info("[PrestigeHoe] Chargé " + cropsById.size() + " cultures depuis crops.yml");
    }

    public Optional<CropDefinition> getDefinition(Block block) {
        CropDefinition def = cropsByMaterial.get(block.getType());
        return Optional.ofNullable(def);
    }

    /**
     * Retourne la définition si le block est un "bébé" (regrow.baby-material)
     */
    public Optional<CropDefinition> getRegrowBabyDefinition(Block block) {
        CropDefinition def = regrowBabyByMaterial.get(block.getType());
        return Optional.ofNullable(def);
    }

    public boolean isFullyGrown(Block block, CropDefinition def) {
        // Si la crop n'exige pas d'être "mûre", on accepte toujours
        if (!def.isFullyGrownRequired()) {
            return true;
        }

        try {
            if (block.getBlockData() instanceof Ageable ageable) {
                // Cas classique (blé, patates, carottes, cacao, etc.)
                return ageable.getAge() >= ageable.getMaximumAge();
            }
        } catch (Exception ignored) {
        }

        // 👉 Ici : le bloc n'est PAS Ageable (melons, citrouilles, etc.)
        // Comme il n'a pas de notion d'âge, on le considère toujours comme "mûr"
        // => il sera traité comme une crop valide, on ne cancel plus l'event.
        return true;
    }

    public void replant(Block block, CropDefinition def) {
        if (def == null) return;

        // 🌱 Cas spécial : cultures avec regrow custom (corail, etc.)
        if (def.isRegrowEnabled() && def.getRegrowBabyMaterial() != null) {

            // On place le bloc "bébé"
            block.setType(def.getRegrowBabyMaterial(), false);
            clearWaterlogged(block);

            long delay = def.getRegrowDelayTicks();
            if (delay <= 0) {
                delay = 600L; // 30s par défaut
            }

            // On programme la repousse
            Block finalBlock = block;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // ⚠️ Si le bloc a été cassé/remplacé à la main entre-temps,
                // on NE replante PAS (cassé définitivement).
                if (finalBlock.getType() != def.getRegrowBabyMaterial()) {
                    return;
                }

                finalBlock.setType(def.getMaterial(), false);
                clearWaterlogged(finalBlock);
            }, delay);

            return;
        }

        // 🌾 Cas classique : replant automatique uniquement pour les crops "à âge"
        // Exemple : WHEAT, CARROTS, POTATOES, BEETROOTS, COCOA, NETHER_WART, etc.
        try {
            // On regarde le blockdata du MATERIAL de la crop, pas du bloc actuellement dans le monde
            BlockData matData = def.getMaterial().createBlockData();

            // Si la crop n'est pas Ageable (MELON, PUMPKIN, etc.) -> pas de replant auto
            if (!(matData instanceof Ageable)) {
                return; // on laisse le vanilla / la logique d'ailleurs gérer (tiges, etc.)
            }

            // Ici on sait que la crop est Ageable → on peut la replanter à age 0
            block.setType(def.getMaterial(), false);
            BlockData data = block.getBlockData();
            if (data instanceof Ageable ageable) {
                ageable.setAge(0);
                block.setBlockData(ageable, false);
            }

        } catch (Exception ignored) {
            // En cas de problème, on évite de crash et on ne replante pas
        }
    }

    /**
     * Si le block supporte waterlogged, on le force à false.
     * Ça évite que la repousse crée ou garde de l'eau.
     */
    private void clearWaterlogged(Block block) {
        try {
            BlockData data = block.getBlockData();
            if (data instanceof Waterlogged waterlogged) {
                waterlogged.setWaterlogged(false);
                block.setBlockData(waterlogged, false);
            }
        } catch (Exception ignored) {
        }
    }
}
