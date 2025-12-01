package fr.batistou15.prestigehoe.farm;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.boost.BoostManager;
import fr.batistou15.prestigehoe.boost.BoostManager.BoostType;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.crop.CropDefinition;
import fr.batistou15.prestigehoe.crop.CropManager;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.enchant.*;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.hooks.EconomyHook;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;
import fr.batistou15.prestigehoe.skin.SkinManager;
import fr.batistou15.prestigehoe.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FarmExecutionService {

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final HoeItemManager hoeItemManager;
    private final CropManager cropManager;
    private final EconomyHook economyHook;
    private final EnchantManager enchantManager;

    private final RewardFormulaService rewardFormulaService;
    private final HoeDisplayService displayService;
    private final HoeProgressionService progressionService;

    public FarmExecutionService(PrestigeHoePlugin plugin,
                                ConfigManager configManager,
                                PlayerDataManager playerDataManager,
                                HoeItemManager hoeItemManager,
                                CropManager cropManager,
                                EconomyHook economyHook,
                                EnchantManager enchantManager,
                                RewardFormulaService rewardFormulaService,
                                HoeDisplayService displayService,
                                HoeProgressionService progressionService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.hoeItemManager = hoeItemManager;
        this.cropManager = cropManager;
        this.economyHook = economyHook;
        this.enchantManager = enchantManager;
        this.rewardFormulaService = rewardFormulaService;
        this.displayService = displayService;
        this.progressionService = progressionService;
    }

    /**
     * Pipeline complet du farm quand un bloc est cassé avec la PrestigeHoe.
     */
    /**
     * Pipeline complet du farm quand un bloc est cassé avec la PrestigeHoe.
     */
    public void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        // 1) Vérifier que le joueur tient une PrestigeHoe
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hoeItemManager.isPrestigeHoe(tool)) {
            return;
        }

        // 1.bis) Si c'est un bloc "bébé" d'une culture à regrow, on empêche la casse
        var babyDefOpt = cropManager.getRegrowBabyDefinition(block);
        if (babyDefOpt.isPresent()) {
            event.setCancelled(true);
            return;
        }

        // 2) Vérifier que la houe lui appartient
        if (!hoeItemManager.isOwnedBy(tool, player)) {
            MessageUtil.send(player, "errors.not-owner");
            event.setCancelled(true);
            return;
        }

        // ✅ recap : on signale une activité, le service gère lui-même
        if (plugin.getRecapService() != null) {
            plugin.getRecapService().markActivity(player);
        }

        // 2.bis) Empêcher de casser les tiges / "graines" de melon & citrouille
        if (type == Material.MELON_STEM || type == Material.ATTACHED_MELON_STEM
                || type == Material.PUMPKIN_STEM || type == Material.ATTACHED_PUMPKIN_STEM) {
            event.setCancelled(true);
            return;
        }

        // 3) Vérifier si le bloc est une crop gérée
        var defOpt = cropManager.getDefinition(block);
        if (defOpt.isEmpty()) {
            return; // bloc non géré
        }
        CropDefinition def = defOpt.get();

        // 4) Vérifier la maturité
        if (!cropManager.isFullyGrown(block, def)) {
            event.setCancelled(true);
            return;
        }

        // 5) Récupérer profil & HoeData
        PlayerProfile profile = playerDataManager.getProfile(player);
        HoeData hoe = profile.getHoeData();

        // Requirements culture (niveau houe / prestige)
        if (!checkCropRequirements(player, profile, hoe, def)) {
            event.setCancelled(true);
            return;
        }

        // 🔹 Skins : multiplicateurs pour CE crop (bloc principal)
        final SkinManager skinManager = plugin.getSkinManager();
        double skinHoeXpMult = 1.0D;
        double skinPlayerXpMult = 1.0D;
        double skinEssenceMult = 1.0D;
        double skinMoneyMult = 1.0D;

        if (skinManager != null && profile != null) {
            skinHoeXpMult = skinManager.getXpHoeMultiplier(profile, def);
            skinPlayerXpMult = skinManager.getXpPlayerMultiplier(profile, def);
            skinEssenceMult = skinManager.getEssenceMultiplier(profile, def);
            skinMoneyMult = skinManager.getMoneyMultiplier(profile, def);
        }

        // 🧪 Boosts temporaires (items boosts)
        BoostManager boostManager = plugin.getBoostManager();
        double boostEssenceMult = 1.0D;
        double boostMoneyMult = 1.0D;
        double boostXpHoeMult = 1.0D;
        double boostXpPlayerMult = 1.0D;

        if (boostManager != null) {
            try {
                UUID uuid = player.getUniqueId();
                boostEssenceMult = boostManager.getBoostMultiplier(uuid, BoostType.ESSENCE);
                boostMoneyMult = boostManager.getBoostMultiplier(uuid, BoostType.MONEY);
                boostXpHoeMult = boostManager.getBoostMultiplier(uuid, BoostType.XP_HOE);
                boostXpPlayerMult = boostManager.getBoostMultiplier(uuid, BoostType.XP_PLAYER);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Boosts] Erreur lors de la récupération des boosts pour " + player.getName());
            }
        }

        // Versions finales utilisables dans les lambdas (Explosive)
        final double fBoostEssenceMult = boostEssenceMult;
        final double fBoostMoneyMult = boostMoneyMult;
        final double fBoostXpHoeMult = boostXpHoeMult;
        final double fBoostXpPlayerMult = boostXpPlayerMult;

        // 🧨 Explosive & 🪓 LineBreaker : mutuellement exclus
        int explosiveRadius = 0;
        int lineLength = 0;

        ExplosiveEnchant explosiveEnchant = enchantManager.getExplosiveEnchant();
        LineBreakerEnchant lineBreakerEnchant = enchantManager.getLineBreakerEnchant();

        // 1) On tente d'abord Explosive
        boolean explosiveTriggered = false;
        if (explosiveEnchant != null && explosiveEnchant.isEnabled()) {
            explosiveRadius = explosiveEnchant.tryExplode(player, hoe, block); // fait déjà l'effet TNT + notif
            explosiveTriggered = explosiveRadius > 0;
        }

        // 2) Si Explosive n'a PAS proc, on tente LineBreaker
        if (!explosiveTriggered && lineBreakerEnchant != null && lineBreakerEnchant.isEnabled()) {
            lineLength = lineBreakerEnchant.tryGetLineLength(player, hoe);
            if (lineLength > 0) {
                // 👀 Rayon de particules (visuel seulement)
                lineBreakerEnchant.playVisualRay(player, block, lineLength);
            }
        }

        // 6) Enchants
        FortuneEnchant fortune = enchantManager.getFortuneEnchant();
        AutosellEnchant autosell = enchantManager.getAutosellEnchant();

        // 7) Fortune façon vanilla : quantité logique (1..1+fortuneLevel)
        int quantity = 1;
        if (fortune != null && fortune.isEnabled()) {
            int fortuneLevel = fortune.getLevel(hoe);
            if (fortuneLevel > 0) {
                int extra = ThreadLocalRandom.current().nextInt(fortuneLevel + 1); // 0..fortuneLevel
                quantity += extra;
            }
        }

        // 8) Autosell actif ?
        boolean hasAutosell = autosell != null && autosell.hasAutosell(hoe);
        if (hasAutosell) {
            event.setDropItems(false);
        }

        // 9) XP houe (scalée par la quantité) + BoostXP
        double xpHoePerBlock = def.getXpHoe();
        double totalXpHoe = xpHoePerBlock * quantity;

        // Boosts "classiques" (formules, enchants, prestige brut, etc.)
        totalXpHoe = progressionService.applyHoeXpBoost(hoe, totalXpHoe);

        // 🔥 PERK : XP Hoe (prestige_shop.yml -> type: XP_HOE_MULTIPLIER)
        try {
            PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
            if (bonusService != null && profile != null) {
                double perkMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.XP_HOE_MULTIPLIER);
                if (perkMulti > 0.0D) {
                    totalXpHoe *= perkMulti;
                }
            }
        } catch (Throwable ignored) {
        }

        // 🎨 Bonus de skin sur l'XP houe (avec crop-bonus)
        totalXpHoe *= skinHoeXpMult;

        // 🧪 Boost item : XP_HOE
        totalXpHoe *= fBoostXpHoeMult;

        addHoeXpAndHandleLevelUp(player, profile, hoe, totalXpHoe);

        // 10) XP vanilla joueur
        double xpPlayerPerBlock = def.getXpPlayer();
        if (xpPlayerPerBlock > 0) {
            double totalXpPlayer = xpPlayerPerBlock * quantity;

            // 🆕 Enchant Player XP Booster
            PlayerXpBoosterEnchant playerXpBooster = enchantManager.getPlayerXpBoosterEnchant();
            if (playerXpBooster != null && playerXpBooster.isEnabled()) {
                double multi = playerXpBooster.getXpMultiplier(hoe);
                if (multi > 0.0D) {
                    totalXpPlayer *= multi;
                }
            }

            // 🔥 PERK : XP Joueur (prestige_shop.yml -> type: XP_PLAYER_MULTIPLIER)
            try {
                PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
                if (bonusService != null && profile != null) {
                    double perkMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.XP_PLAYER_MULTIPLIER);
                    if (perkMulti > 0.0D) {
                        totalXpPlayer *= perkMulti;
                    }
                }
            } catch (Throwable ignored) {
            }

            // 🎨 Bonus de skin sur l'XP joueur (avec crop-bonus)
            totalXpPlayer *= skinPlayerXpMult;

            // 🧪 Boost item : XP_PLAYER
            totalXpPlayer *= fBoostXpPlayerMult;

            int rounded = (int) Math.round(totalXpPlayer);
            if (rounded > 0) {
                player.giveExp(rounded);
                profile.addPlayerXpGained(rounded);
            }
        }

        // 10.x) TokenFinder
        if (enchantManager != null && enchantManager.getTokenFinderEnchant() != null) {
            enchantManager.getTokenFinderEnchant().tryExecute(player, hoe);
        }

        // 10.x+1) KeyFinder
        if (enchantManager != null && enchantManager.getKeyFinderEnchant() != null) {
            enchantManager.getKeyFinderEnchant().tryExecute(player, hoe);
        }

        // 10.x+2) SpawnerFinder
        if (enchantManager != null && enchantManager.getSpawnerFinderEnchant() != null) {
            enchantManager.getSpawnerFinderEnchant().tryExecute(player, hoe);
        }

        // 10.z) Custom enchants (custom_enchantX)
        if (enchantManager != null) {
            for (ConfigurableCustomProcEnchant custom : enchantManager.getCustomProcEnchants().values()) {
                custom.tryExecute(player, hoe);
            }
        }

        // 10.y) Furie (proc avant le calcul des récompenses pour qu'elle s'applique sur CE bloc)
        FuryEnchant furyEnchant = enchantManager.getFuryEnchant();
        if (furyEnchant != null && furyEnchant.isEnabled()) {
            furyEnchant.tryExecute(player, hoe);
        }

        // Essence
        double cropEssence = def.getEssence();
        double baseEssenceGain = rewardFormulaService.computeEssenceGain(cropEssence, quantity, hoe, profile);

        double totalEssence = baseEssenceGain;

        EssenceBoosterEnchant essenceBooster = enchantManager.getEssenceBoosterEnchant();
        if (essenceBooster != null && essenceBooster.isEnabled()) {
            totalEssence *= essenceBooster.getMultiplier(hoe);
        }

        // 🔥 FURIE : multiplicateur d'Essence
        if (furyEnchant != null && furyEnchant.isEnabled()) {
            double furyEssenceMult = furyEnchant.getCurrentEssenceMultiplier(player);
            if (furyEssenceMult > 0.0D) {
                totalEssence *= furyEssenceMult;
            }
        }

        // 🎨 Bonus de skin sur l'essence (avec crop-bonus)
        totalEssence *= skinEssenceMult;

        // 🧪 Boost item : ESSENCE
        totalEssence *= fBoostEssenceMult;

        if (totalEssence > 0) {
            profile.addEssence(totalEssence);
        }

        // 🆕 Essence Pouch – proc bonus, on récupère le montant pour le récap
        double essencePouchAmount = 0.0D;
        EssencePouchEnchant essencePouch = enchantManager.getEssencePouchEnchant();
        if (essencePouch != null && essencePouch.isEnabled()) {
            essencePouchAmount = essencePouch.tryExecute(player, hoe); // doit retourner double
        }

        // 12) Argent via formule + MoneyBooster
        double basePrice = def.getPrice();
        double moneyGain = rewardFormulaService.computeMoneyGain(basePrice, quantity, hoe, profile);

        double moneyMultiplier = 1.0;
        MoneyBoosterEnchant moneyBooster = enchantManager.getMoneyBoosterEnchant();
        if (moneyBooster != null) {
            moneyMultiplier = moneyBooster.getMoneyMultiplier(hoe);
            if (moneyMultiplier < 1.0) {
                moneyMultiplier = 1.0;
            }
        }

        double finalMoney = moneyGain * moneyMultiplier;

        // 🔥 FURIE : multiplicateur d'argent
        if (furyEnchant != null && furyEnchant.isEnabled()) {
            double furyMoneyMult = furyEnchant.getCurrentMoneyMultiplier(player);
            if (furyMoneyMult > 0.0D) {
                finalMoney *= furyMoneyMult;
            }
        }

        // 🎨 Bonus de skin sur l'argent (avec crop-bonus)
        finalMoney *= skinMoneyMult;

        // 🧪 Boost item : MONEY
        finalMoney *= fBoostMoneyMult;

        if (finalMoney > 0) {
            economyHook.deposit(player, finalMoney);
            profile.addMoneyEarned(finalMoney);
        }

        // 🆕 Money Pouch – proc bonus, comme EssencePouch
        double moneyPouchAmount = 0.0D;
        MoneyPouchEnchant moneyPouch = enchantManager.getMoneyPouchEnchant();
        if (moneyPouch != null && moneyPouch.isEnabled()) {
            moneyPouchAmount = moneyPouch.tryExecute(player, hoe);
        }

        // Stat Autosell
        if (hasAutosell && quantity > 0) {
            profile.addAutosellItems(quantity);
        }

        // 13) Stats crops cassées (1 bloc par casse)
        profile.incrementCropBroken(def.getId(), 1);

        // Référence au récap : on pousse les gains de CE bloc
        if (plugin.getRecapService() != null) {
            plugin.getRecapService().recordFarmGain(
                    player,
                    finalMoney + moneyPouchAmount,
                    totalEssence + essencePouchAmount,
                    1L,
                    hasAutosell ? quantity : 0L
            );
        }

        // 14) Fortune sans Autosell -> drops manuels pour les extra
        if (!hasAutosell && quantity > 1) {
            dropExtraFortuneItems(block, tool, player, quantity - 1);
        }

        // 15) Config auto-replant
        FileConfiguration cfg = configManager.getMainConfig();
        boolean autoReplant = cfg.getBoolean("hoe.auto-replant.enabled", true);

        // 🪓 15.bis) LineBreaker : casse une ligne devant le joueur (immédiat)
        if (lineLength > 0) {
            BlockFace face = player.getFacing();
            if (face == BlockFace.NORTH || face == BlockFace.SOUTH
                    || face == BlockFace.EAST || face == BlockFace.WEST) {

                for (int i = 1; i <= lineLength; i++) {
                    Block b2 = block.getRelative(face, i);

                    // éviter les tiges
                    Material t2 = b2.getType();
                    if (t2 == Material.MELON_STEM || t2 == Material.ATTACHED_MELON_STEM
                            || t2 == Material.PUMPKIN_STEM || t2 == Material.ATTACHED_PUMPKIN_STEM) {
                        continue;
                    }

                    var def2Opt = cropManager.getDefinition(b2);
                    if (def2Opt.isEmpty()) continue;
                    CropDefinition def2 = def2Opt.get();

                    if (!cropManager.isFullyGrown(b2, def2)) continue;

                    // Requirements culture pour ce bloc aussi
                    if (!checkCropRequirements(player, profile, hoe, def2)) {
                        continue;
                    }

                    // Fortune pour ce bloc
                    int quantity2 = 1;
                    if (fortune != null && fortune.isEnabled()) {
                        int fLevel = fortune.getLevel(hoe);
                        if (fLevel > 0) {
                            int extra2 = ThreadLocalRandom.current().nextInt(fLevel + 1);
                            quantity2 += extra2;
                        }
                    }

                    // XP Hoe
                    double xpHoe2 = def2.getXpHoe() * quantity2;
                    xpHoe2 = progressionService.applyHoeXpBoost(hoe, xpHoe2);
                    try {
                        PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
                        if (bonusService != null && profile != null) {
                            double perkMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.XP_HOE_MULTIPLIER);
                            if (perkMulti > 0.0D) {
                                xpHoe2 *= perkMulti;
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    // 🔥 SKIN : XP Hoe sur LineBreaker (par crop)
                    if (skinManager != null && profile != null) {
                        double skinHoeMult2 = skinManager.getXpHoeMultiplier(profile, def2);
                        xpHoe2 *= skinHoeMult2;
                    }

                    // 🧪 Boost item : XP_HOE
                    xpHoe2 *= fBoostXpHoeMult;

                    addHoeXpAndHandleLevelUp(player, profile, hoe, xpHoe2);

                    // XP vanilla joueur
                    double xpPlayer2 = def2.getXpPlayer() * quantity2;
                    if (xpPlayer2 > 0) {
                        PlayerXpBoosterEnchant playerXpBooster2 = enchantManager.getPlayerXpBoosterEnchant();
                        if (playerXpBooster2 != null && playerXpBooster2.isEnabled()) {
                            double m = playerXpBooster2.getXpMultiplier(hoe);
                            if (m > 0.0D) {
                                xpPlayer2 *= m;
                            }
                        }

                        try {
                            PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
                            if (bonusService != null && profile != null) {
                                double perkMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.XP_PLAYER_MULTIPLIER);
                                if (perkMulti > 0.0D) {
                                    xpPlayer2 *= perkMulti;
                                }
                            }
                        } catch (Throwable ignored) {
                        }

                        // 🔥 SKIN : XP Joueur sur LineBreaker (par crop)
                        if (skinManager != null && profile != null) {
                            double skinPlayerMult2 = skinManager.getXpPlayerMultiplier(profile, def2);
                            xpPlayer2 *= skinPlayerMult2;
                        }

                        // 🧪 Boost item : XP_PLAYER
                        xpPlayer2 *= fBoostXpPlayerMult;

                        int rounded2 = (int) Math.round(xpPlayer2);
                        if (rounded2 > 0) {
                            player.giveExp(rounded2);
                            profile.addPlayerXpGained(rounded2);
                        }
                    }

                    // Essence
                    double essBase2 = rewardFormulaService.computeEssenceGain(def2.getEssence(), quantity2, hoe, profile);
                    double essTotal2 = essBase2;
                    if (essenceBooster != null && essenceBooster.isEnabled()) {
                        essTotal2 *= essenceBooster.getMultiplier(hoe);
                    }
                    if (furyEnchant != null && furyEnchant.isEnabled()) {
                        double fMult = furyEnchant.getCurrentEssenceMultiplier(player);
                        if (fMult > 0.0D) {
                            essTotal2 *= fMult;
                        }
                    }

                    // 🔥 SKIN : Essence sur LineBreaker
                    if (skinManager != null && profile != null) {
                        double skinEssMult2 = skinManager.getEssenceMultiplier(profile, def2);
                        essTotal2 *= skinEssMult2;
                    }

                    // 🧪 Boost item : ESSENCE
                    essTotal2 *= fBoostEssenceMult;

                    if (essTotal2 > 0) {
                        profile.addEssence(essTotal2);
                    }

                    // Money
                    double moneyBase2 = rewardFormulaService.computeMoneyGain(def2.getPrice(), quantity2, hoe, profile);
                    double finalMoney2 = moneyBase2;
                    if (moneyBooster != null) {
                        double mm = moneyBooster.getMoneyMultiplier(hoe);
                        if (mm < 1.0D) mm = 1.0D;
                        finalMoney2 *= mm;
                    }
                    if (furyEnchant != null && furyEnchant.isEnabled()) {
                        double fMult = furyEnchant.getCurrentMoneyMultiplier(player);
                        if (fMult > 0.0D) {
                            finalMoney2 *= fMult;
                        }
                    }

                    // 🔥 SKIN : argent sur LineBreaker
                    if (skinManager != null && profile != null) {
                        double skinMoneyMult2 = skinManager.getMoneyMultiplier(profile, def2);
                        finalMoney2 *= skinMoneyMult2;
                    }

                    // 🧪 Boost item : MONEY
                    finalMoney2 *= fBoostMoneyMult;

                    if (finalMoney2 > 0) {
                        economyHook.deposit(player, finalMoney2);
                        profile.addMoneyEarned(finalMoney2);
                    }

                    // Autosell / recap pour ce bloc
                    if (hasAutosell && quantity2 > 0) {
                        profile.addAutosellItems(quantity2);
                    }

                    if (plugin.getRecapService() != null) {
                        plugin.getRecapService().recordFarmGain(
                                player,
                                finalMoney2,
                                essTotal2,
                                0L,                              // ⚠️ pas de +1 cropsBroken ici
                                hasAutosell ? quantity2 : 0L
                        );
                    }

                    // Casse du bloc + replant si enabled
                    b2.setType(Material.AIR);
                    if (autoReplant) {
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                                () -> cropManager.replant(b2, def2), 1L);
                    }
                }
            }
        }

        // 🧨 15.ter) Explosive : on décale la casse AoE de 1 seconde (20 ticks)
        if (explosiveRadius > 0) {
            final int radiusFinal = explosiveRadius;
            final boolean finalHasAutosell = hasAutosell;
            final boolean finalAutoReplant = autoReplant;

            final FortuneEnchant fortuneRef = fortune;
            final EssenceBoosterEnchant essenceBoosterRef = essenceBooster;
            final MoneyBoosterEnchant moneyBoosterRef = moneyBooster;
            final FuryEnchant furyRef = furyEnchant;
            final PlayerXpBoosterEnchant playerXpBoosterRef = enchantManager.getPlayerXpBoosterEnchant();

            final Location originLoc = block.getLocation();

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                World world = originLoc.getWorld();
                if (world == null) return;

                for (int dx = -radiusFinal; dx <= radiusFinal; dx++) {
                    for (int dz = -radiusFinal; dz <= radiusFinal; dz++) {
                        if (dx == 0 && dz == 0) continue; // on ne retrait pas le bloc central

                        Block b2 = world.getBlockAt(
                                originLoc.getBlockX() + dx,
                                originLoc.getBlockY(),
                                originLoc.getBlockZ() + dz
                        );

                        // éviter les tiges
                        Material t2 = b2.getType();
                        if (t2 == Material.MELON_STEM || t2 == Material.ATTACHED_MELON_STEM
                                || t2 == Material.PUMPKIN_STEM || t2 == Material.ATTACHED_PUMPKIN_STEM) {
                            continue;
                        }

                        var def2Opt = cropManager.getDefinition(b2);
                        if (def2Opt.isEmpty()) continue;
                        CropDefinition def2 = def2Opt.get();

                        if (!cropManager.isFullyGrown(b2, def2)) continue;

                        // requirements culture pour ce bloc aussi
                        if (!checkCropRequirements(player, profile, hoe, def2)) {
                            continue;
                        }

                        // Fortune pour ce bloc
                        int quantity2 = 1;
                        if (fortuneRef != null && fortuneRef.isEnabled()) {
                            int fLevel = fortuneRef.getLevel(hoe);
                            if (fLevel > 0) {
                                int extra2 = ThreadLocalRandom.current().nextInt(fLevel + 1);
                                quantity2 += extra2;
                            }
                        }

                        // XP Hoe
                        double xpHoe2 = def2.getXpHoe() * quantity2;
                        xpHoe2 = progressionService.applyHoeXpBoost(hoe, xpHoe2);
                        try {
                            PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
                            if (bonusService != null && profile != null) {
                                double perkMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.XP_HOE_MULTIPLIER);
                                if (perkMulti > 0.0D) {
                                    xpHoe2 *= perkMulti;
                                }
                            }
                        } catch (Throwable ignored) {
                        }

                        // 🔥 SKIN : XP Hoe sur Explosive (par crop)
                        if (skinManager != null && profile != null) {
                            double skinHoeMult2 = skinManager.getXpHoeMultiplier(profile, def2);
                            xpHoe2 *= skinHoeMult2;
                        }

                        // 🧪 Boost item : XP_HOE
                        xpHoe2 *= fBoostXpHoeMult;

                        addHoeXpAndHandleLevelUp(player, profile, hoe, xpHoe2);

                        // XP vanilla joueur
                        double xpPlayer2 = def2.getXpPlayer() * quantity2;
                        if (xpPlayer2 > 0) {
                            if (playerXpBoosterRef != null && playerXpBoosterRef.isEnabled()) {
                                double m = playerXpBoosterRef.getXpMultiplier(hoe);
                                if (m > 0.0D) {
                                    xpPlayer2 *= m;
                                }
                            }

                            try {
                                PrestigeBonusService bonusService = plugin.getPrestigeBonusService();
                                if (bonusService != null && profile != null) {
                                    double perkMulti = bonusService.getPerkMultiplier(profile, PrestigeBonusService.BonusContext.XP_PLAYER_MULTIPLIER);
                                    if (perkMulti > 0.0D) {
                                        xpPlayer2 *= perkMulti;
                                    }
                                }
                            } catch (Throwable ignored) {
                            }

                            // 🔥 SKIN : XP Joueur sur Explosive (par crop)
                            if (skinManager != null && profile != null) {
                                double skinPlayerMult2 = skinManager.getXpPlayerMultiplier(profile, def2);
                                xpPlayer2 *= skinPlayerMult2;
                            }

                            // 🧪 Boost item : XP_PLAYER
                            xpPlayer2 *= fBoostXpPlayerMult;

                            int rounded2 = (int) Math.round(xpPlayer2);
                            if (rounded2 > 0) {
                                player.giveExp(rounded2);
                                profile.addPlayerXpGained(rounded2);
                            }
                        }

                        // Essence
                        double essBase2 = rewardFormulaService.computeEssenceGain(def2.getEssence(), quantity2, hoe, profile);
                        double essTotal2 = essBase2;
                        if (essenceBoosterRef != null && essenceBoosterRef.isEnabled()) {
                            essTotal2 *= essenceBoosterRef.getMultiplier(hoe);
                        }
                        if (furyRef != null && furyRef.isEnabled()) {
                            double fMult = furyRef.getCurrentEssenceMultiplier(player);
                            if (fMult > 0.0D) {
                                essTotal2 *= fMult;
                            }
                        }

                        // 🔥 SKIN : Essence sur Explosive (par crop)
                        if (skinManager != null && profile != null) {
                            double skinEssMult2 = skinManager.getEssenceMultiplier(profile, def2);
                            essTotal2 *= skinEssMult2;
                        }

                        // 🧪 Boost item : ESSENCE
                        essTotal2 *= fBoostEssenceMult;

                        if (essTotal2 > 0) {
                            profile.addEssence(essTotal2);
                        }

                        // Money
                        double moneyBase2 = rewardFormulaService.computeMoneyGain(def2.getPrice(), quantity2, hoe, profile);
                        double finalMoney2 = moneyBase2;
                        if (moneyBoosterRef != null) {
                            double mm = moneyBoosterRef.getMoneyMultiplier(hoe);
                            if (mm < 1.0D) mm = 1.0D;
                            finalMoney2 *= mm;
                        }
                        if (furyRef != null && furyRef.isEnabled()) {
                            double fMult = furyRef.getCurrentMoneyMultiplier(player);
                            if (fMult > 0.0D) {
                                finalMoney2 *= fMult;
                            }
                        }

                        // 🔥 SKIN : argent sur Explosive (par crop)
                        if (skinManager != null && profile != null) {
                            double skinMoneyMult2 = skinManager.getMoneyMultiplier(profile, def2);
                            finalMoney2 *= skinMoneyMult2;
                        }

                        // 🧪 Boost item : MONEY
                        finalMoney2 *= fBoostMoneyMult;

                        if (finalMoney2 > 0) {
                            economyHook.deposit(player, finalMoney2);
                            profile.addMoneyEarned(finalMoney2);
                        }

                        // Autosell / recap pour ce bloc
                        if (finalHasAutosell && quantity2 > 0) {
                            profile.addAutosellItems(quantity2);
                        }

                        if (plugin.getRecapService() != null) {
                            plugin.getRecapService().recordFarmGain(
                                    player,
                                    finalMoney2,
                                    essTotal2,
                                    0L,                                  // ⚠️ pas de +1 cropsBroken ici
                                    finalHasAutosell ? quantity2 : 0L
                            );
                        }

                        // Casse du bloc + replant si enabled
                        b2.setType(Material.AIR);
                        if (finalAutoReplant) {
                            plugin.getServer().getScheduler().runTaskLater(plugin,
                                    () -> cropManager.replant(b2, def2), 1L);
                        }
                    }
                }
            }, 20L); // 1 seconde après la casse du bloc principal
        }

        // 16) Auto-replant du bloc principal
        if (autoReplant) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    cropManager.replant(block, def), 1L);
        }

        // 17) Mise à jour visuelle de la houe
        displayService.updateHoeDisplay(player, hoe);
    }





    // =========================================================
    //  Helpers privés : requirements / XP / drops
    // =========================================================

    private boolean checkCropRequirements(Player player,
                                          PlayerProfile profile,
                                          HoeData hoe,
                                          CropDefinition def) {
        if (def == null || hoe == null || profile == null) {
            return true;
        }

        int minHoeLevel = def.getMinHoeLevel();
        if (minHoeLevel > 0 && hoe.getLevel() < minHoeLevel) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cTu dois avoir une houe niveau &e" + minHoeLevel
                            + " &cpour farm cette culture (&e" + def.getDisplayName() + "&c).");
            return false;
        }

        int minPrestige = def.getMinPrestige();
        if (minPrestige > 0 && hoe.getPrestige() < minPrestige) {
            MessageUtil.sendPlain(player,
                    MessageUtil.getPrefix() + "&cTu dois avoir un prestige de houe &e" + minPrestige
                            + " &cpour farm cette culture (&e" + def.getDisplayName() + "&c).");
            return false;
        }

        return true;
    }

    private void addHoeXpAndHandleLevelUp(Player player,
                                          PlayerProfile profile,
                                          HoeData hoe,
                                          double amount) {
        if (hoe == null || amount <= 0) return;

        if (profile != null) {
            profile.addHoeXpGained(amount);
        }

        double xp = hoe.getXp() + amount;
        hoe.setXp(xp);

        boolean leveledUp = false;
        int maxLevel = progressionService.getMaxHoeLevel(hoe);

        while (true) {
            if (hoe.getLevel() >= maxLevel) {
                double required = progressionService.getXpRequiredForLevel(hoe);
                if (hoe.getXp() > required) {
                    hoe.setXp(required);
                }
                break;
            }

            double required = progressionService.getXpRequiredForLevel(hoe);
            if (hoe.getXp() < required) {
                break;
            }

            hoe.setXp(hoe.getXp() - required);

            int oldLevel = hoe.getLevel();
            hoe.setLevel(oldLevel + 1);
            leveledUp = true;
        }

        if (leveledUp && plugin.getNotificationService() != null) {
            plugin.getNotificationService().notifyLevelUp(player, hoe);
            displayService.updateHoeDisplay(player, hoe);
        }
    }

    private void dropExtraFortuneItems(Block block,
                                       ItemStack tool,
                                       Player player,
                                       int extraBlocks) {
        if (extraBlocks <= 0) return;

        Collection<ItemStack> vanillaDrops = block.getDrops(tool, player);
        if (vanillaDrops.isEmpty()) return;

        Location loc = block.getLocation().add(0.5, 0.1, 0.5);
        for (int i = 0; i < extraBlocks; i++) {
            for (ItemStack drop : vanillaDrops) {
                if (drop == null || drop.getType() == Material.AIR) continue;
                player.getWorld().dropItemNaturally(loc, drop.clone());
            }
        }
    }
}
