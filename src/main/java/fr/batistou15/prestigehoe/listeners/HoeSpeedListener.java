package fr.batistou15.prestigehoe.listeners;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.data.HoeData;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.enchant.SpeedEnchant;
import fr.batistou15.prestigehoe.hoe.HoeItemManager;
import fr.batistou15.prestigehoe.prestige.PrestigeBonusService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class HoeSpeedListener implements Listener {

    private final PrestigeHoePlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final HoeItemManager hoeItemManager;
    private final PrestigeBonusService prestigeBonusService;

    // vitesse vanilla de Minecraft
    private static final float BASE_WALK_SPEED = 0.2f;

    public HoeSpeedListener(PrestigeHoePlugin plugin,
                            PlayerDataManager playerDataManager,
                            HoeItemManager hoeItemManager,
                            PrestigeBonusService prestigeBonusService) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.hoeItemManager = hoeItemManager;
        this.prestigeBonusService = prestigeBonusService;
    }

    // ==========================
    //   EVENTS
    // ==========================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // On laisse le temps au joueur de charger son inventaire
        Bukkit.getScheduler().runTaskLater(plugin, () -> applySpeedFor(player), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Par sécurité, on remet la vitesse vanilla
        Player player = event.getPlayer();
        try {
            player.setWalkSpeed(BASE_WALK_SPEED);
        } catch (IllegalArgumentException ignored) {
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // On attend 1 tick pour que le main hand soit bien mis à jour
        Bukkit.getScheduler().runTaskLater(plugin, () -> applySpeedFor(player), 1L);
    }

    // ==========================
    //   LOGIQUE DE VITESSE
    // ==========================

    /**
     * Applique la bonne vitesse au joueur :
     *  - Si le joueur tient la PrestigeHoe en main -> vitesse boostée via perks + enchant Speed
     *  - Sinon -> vitesse vanilla
     */
    private void applySpeedFor(Player player) {
        if (player == null || !player.isOnline()) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        boolean holdingPrestigeHoe = mainHand != null
                && !mainHand.getType().isAir()
                && hoeItemManager.isPrestigeHoe(mainHand);

        if (!holdingPrestigeHoe) {
            // ❌ Pas de PrestigeHoe en main -> vitesse vanilla
            setSafeWalkSpeed(player, BASE_WALK_SPEED);
            return;
        }

        // ✅ PrestigeHoe en main -> on regarde profil + hoeData
        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null) {
            setSafeWalkSpeed(player, BASE_WALK_SPEED);
            return;
        }

        HoeData hoeData = profile.getHoeData();
        if (hoeData == null) {
            setSafeWalkSpeed(player, BASE_WALK_SPEED);
            return;
        }

        // 🔢 Calcul du multiplicateur de vitesse
        double speedMultiplier = 1.0D;

        // 1) Perks de prestige (boutique prestige)
        if (prestigeBonusService != null) {
            try {
                double perkMult = prestigeBonusService.getSpeedMultiplier(profile);
                if (perkMult > 0.0D) {
                    speedMultiplier *= perkMult;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2) Enchant Speed (enchants.yml -> speed)
        SpeedEnchant speedEnchant = plugin.getEnchantManager().getSpeedEnchant();
        if (speedEnchant != null && speedEnchant.isEnabled()) {
            double enchantMult = speedEnchant.getSpeedMultiplier(hoeData);
            if (enchantMult > 0.0D) {
                speedMultiplier *= enchantMult;
            }
        }

        // On force au minimum x1 (au cas où un truc renvoie < 1)
        if (speedMultiplier < 1.0D) {
            speedMultiplier = 1.0D;
        }

        float targetSpeed = (float) (BASE_WALK_SPEED * speedMultiplier);

        // clamp pour éviter les valeurs délirantes
        if (targetSpeed > 1.0f) {
            targetSpeed = 1.0f;
        }
        if (targetSpeed < 0.0f) {
            targetSpeed = 0.0f;
        }

        setSafeWalkSpeed(player, targetSpeed);
    }

    private void setSafeWalkSpeed(Player player, float speed) {
        try {
            player.setWalkSpeed(speed);
        } catch (IllegalArgumentException ignored) {
            // Si un plugin externe ou Spigot n'aime pas la valeur, on ignore
        }
    }

    // Optionnel : méthode publique si tu veux rafraîchir la vitesse depuis une commande
    public void refreshPlayerSpeed(Player player) {
        applySpeedFor(player);
    }
}
