package eu.rawora.playLegendTask.listeners;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.managers.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Dieser Listener behandelt das Betreten und Verlassen des Servers durch Spieler.
 * Er ist verantwortlich für das Laden und Entladen der Spieler-Gruppendaten.
 */
public class PlayerJoinQuitListener implements Listener {

    private final PlayLegendTask plugin;
    private final PlayerDataManager playerDataManager;

    public PlayerJoinQuitListener(PlayLegendTask plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    /**
     * Wird aufgerufen, wenn ein Spieler den Server betritt.
     * Lädt die Gruppendaten des Spielers über den PlayerDataManager.
     *
     * @param event Das PlayerJoinEvent.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerDataManager.loadPlayerData(player);
        // Das Setzen von Prefix, Scoreboard etc. geschieht innerhalb von loadPlayerData -> updatePlayerVisuals
    }

    /**
     * Wird aufgerufen, wenn ein Spieler den Server verlässt.
     * Entfernt die Daten des Spielers aus dem Cache und räumt Permissions etc. auf.
     *
     * @param event Das PlayerQuitEvent.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerDataManager.unloadPlayerData(player);
    }
}