package fr.batistou15.prestigehoe.recap;

import fr.batistou15.prestigehoe.PrestigeHoePlugin;
import fr.batistou15.prestigehoe.config.ConfigManager;
import fr.batistou15.prestigehoe.data.PlayerDataManager;
import fr.batistou15.prestigehoe.data.PlayerProfile;
import fr.batistou15.prestigehoe.util.MessageUtil;
import fr.batistou15.prestigehoe.util.NumberFormatUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RecapService {

    private final PrestigeHoePlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;

    // intervalle entre 2 récap, en ticks
    private long intervalTicks;

    // config d’affichage
    private String displayMode; // "CHAT" ou "ACTIONBAR"
    private List<String> recapLines;

    // sessions par joueur
    private final Map<UUID, RecapSession> sessions = new ConcurrentHashMap<>();

    public RecapService(PrestigeHoePlugin plugin,
                        ConfigManager configManager,
                        PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;

        reload();
    }

    // =========================================================
    //                    CONFIG / RELOAD
    // =========================================================

    public void reload() {
        FileConfiguration main = configManager.getMainConfig();
        FileConfiguration messages = configManager.getMessagesConfig();

        int seconds = main.getInt("recap.interval-seconds", 10);
        if (seconds < 1) seconds = 10;
        this.intervalTicks = seconds * 20L;

        this.displayMode = messages
                .getString("recap.display-mode", "CHAT")
                .toUpperCase(Locale.ROOT);

        List<String> lines = messages.getStringList("recap.lines");
        if (lines == null) {
            lines = Collections.emptyList();
        }
        this.recapLines = lines;
    }

    // =========================================================
    //                    API PUBLIQUE
    // =========================================================

    /**
     * Marque une activité de farm pour le joueur.
     * Démarre une session de recap si aucune n'est active.
     * (Ne compte pas les gains, juste s'assure qu'une session existe.)
     */
    public void markActivity(Player player) {
        if (player == null || !player.isOnline()) return;

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null) return;

        // Si le joueur a désactivé le recap, on ne fait rien
        if (!profile.isRecapEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        RecapSession session = sessions.get(uuid);

        if (session == null || !session.running) {
            startSession(player, profile);
        }
    }

    /**
     * Compat pour l'ancien code : startFor(player) équivaut à markActivity(player).
     */
    public void startFor(Player player) {
        markActivity(player);
    }

    /**
     * Enregistre les gains d'un "tick de farm" (une casse de crop, en gros).
     * C'est cette méthode qui alimente les stats du recap.
     */
    public void recordFarmGain(Player player,
                               double money,
                               double essence,
                               long crops,
                               long autosellItems) {
        if (player == null || !player.isOnline()) return;

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null || !profile.isRecapEnabled()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        RecapSession session = sessions.get(uuid);

        if (session == null || !session.running) {
            // Si aucune session n'existe (ou plus), on en démarre une
            startSession(player, profile);
            session = sessions.get(uuid);
            if (session == null || !session.running) {
                return;
            }
        }

        // On ne comptabilise que les valeurs positives
        if (money > 0.0D) {
            session.moneyGained += money;
        }
        if (essence > 0.0D) {
            session.essenceGained += essence;
        }
        if (crops > 0L) {
            session.cropsBroken += crops;
        }
        if (autosellItems > 0L) {
            session.autosellItems += autosellItems;
        }

        // On note qu'il y a eu de l'activité sur cet intervalle
        session.hadActivitySinceLastRecap = true;
    }

    public void stop(Player player) {
        if (player == null) return;
        stop(player.getUniqueId());
    }

    public void stop(UUID uuid) {
        RecapSession session = sessions.remove(uuid);
        if (session != null && session.taskId != -1) {
            Bukkit.getScheduler().cancelTask(session.taskId);
        }
    }

    public void stopAll() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            stop(uuid);
        }
    }

    // =========================================================
    //                    LOGIQUE INTERNE
    // =========================================================

    private void startSession(Player player, PlayerProfile profile) {
        UUID uuid = player.getUniqueId();

        RecapSession session = new RecapSession(uuid);
        session.running = true;
        session.hadActivitySinceLastRecap = false; // les gains arriveront via recordFarmGain

        // on stocke la session AVANT de lancer la tâche
        sessions.put(uuid, session);

        int taskId = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> tick(uuid),
                intervalTicks,
                intervalTicks
        ).getTaskId();

        session.taskId = taskId;
    }

    private void tick(UUID uuid) {
        RecapSession session = sessions.get(uuid);
        if (session == null || !session.running) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            stop(uuid);
            return;
        }

        PlayerProfile profile = playerDataManager.getProfile(player);
        if (profile == null || !profile.isRecapEnabled()) {
            stop(uuid);
            return;
        }

        // Si aucune activité (aucun gain) depuis le dernier tick -> on arrête
        if (!session.hadActivitySinceLastRecap) {
            stop(uuid);
            return;
        }

        double deltaMoney = session.moneyGained;
        double deltaEssence = session.essenceGained;
        long deltaCrops = session.cropsBroken;
        long deltaAutosell = session.autosellItems;

        boolean hasChanges =
                deltaMoney != 0.0 ||
                        deltaEssence != 0.0 ||
                        deltaCrops != 0L ||
                        deltaAutosell != 0L;

        if (!hasChanges) {
            // pas de gain réel, on arrête juste la session
            stop(uuid);
            return;
        }

        // Afficher le recap
        sendRecap(player, deltaMoney, deltaEssence, deltaCrops, deltaAutosell);

        // On reset les compteurs pour le prochain intervalle
        session.moneyGained = 0.0D;
        session.essenceGained = 0.0D;
        session.cropsBroken = 0L;
        session.autosellItems = 0L;
        session.hadActivitySinceLastRecap = false;
    }

    // =========================================================
    //                   AFFICHAGE DU RECAP
    // =========================================================

    private void sendRecap(Player player,
                           double deltaMoney,
                           double deltaEssence,
                           long deltaCrops,
                           long deltaAutosell) {

        // On recharge les lignes au cas où messages.yml a changé entre-temps
        FileConfiguration messages = configManager.getMessagesConfig();
        List<String> lines = messages.getStringList("recap.lines");
        if (lines == null || lines.isEmpty()) {
            lines = this.recapLines;
        }

        // Placeholders utilisés dans messages.yml
        Map<String, String> ph = new HashMap<>();
        ph.put("%prestigehoe_recap_money%", NumberFormatUtil.formatShort(deltaMoney));
        // Essence : on laisse 2 décimales pour voir les petits gains
        ph.put("%prestigehoe_recap_essence%",
                String.format(Locale.US, "%.2f", deltaEssence));
        ph.put("%prestigehoe_recap_crops%", NumberFormatUtil.formatShort(deltaCrops));
        ph.put("%prestigehoe_recap_autosell_items%", NumberFormatUtil.formatShort(deltaAutosell));

        String mode = this.displayMode;
        if (mode == null) {
            mode = messages.getString("recap.display-mode", "CHAT");
        }
        mode = mode.toUpperCase(Locale.ROOT);

        if ("ACTIONBAR".equals(mode)) {
            // On prend la première ligne non vide pour l'actionbar
            String firstLine = lines.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .findFirst()
                    .orElse("");

            if (firstLine.isEmpty()) {
                return;
            }

            String line = applyPlaceholders(firstLine, ph);
            line = MessageUtil.color(line);
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(line)
            );
        } else {
            // CHAT par défaut
            for (String raw : lines) {
                if (raw == null || raw.isEmpty()) continue;
                String line = applyPlaceholders(raw, ph);
                MessageUtil.sendPlain(player, MessageUtil.color(line));
            }
        }
    }

    private String applyPlaceholders(String input, Map<String, String> placeholders) {
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // =========================================================
    //                   CLASSE INTERNE SESSION
    // =========================================================

    private static class RecapSession {
        final UUID playerId;
        int taskId = -1;
        boolean running = false;
        boolean hadActivitySinceLastRecap = false;

        // Gains cumulés sur l'intervalle
        double moneyGained = 0.0D;
        double essenceGained = 0.0D;
        long cropsBroken = 0L;
        long autosellItems = 0L;

        RecapSession(UUID playerId) {
            this.playerId = playerId;
        }
    }
}
