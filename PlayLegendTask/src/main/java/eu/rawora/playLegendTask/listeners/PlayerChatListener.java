package eu.rawora.playLegendTask.listeners;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.managers.PlayerDataManager;
import eu.rawora.playLegendTask.model.Group;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class PlayerChatListener implements Listener {

    /**
     * Dieser Listener stellt ein konsistentes Chat-Format sicher (z.B. "Prefix Spielername: Nachricht").
     * Er verlässt sich darauf, dass der Anzeigename des Spielers (player.getDisplayName(), eingefügt via %1$s)
     * bereits den korrekten Gruppen-Prefix vom PlayerDataManager enthält. Das explizite Setzen des Formats
     * hier garantiert das gewünschte Aussehen unabhängig von Server-Standardeinstellungen.
     * Ist aber natürlich in dieser Umsetzung hier nur eine basic Formatierung und daher momentan unnötig das Event zu ändern,
     * könnte man natürlich aber weiter ausbauen wie unten im TO DO erwähnt
     */

    private final PlayLegendTask plugin;
    private final PlayerDataManager playerDataManager;

    public PlayerChatListener(PlayLegendTask plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
    }

    /**
     * Modifiziert das Chat-Format, um den Gruppen-Prefix hinzuzufügen.
     * läuft asynchron.
     * @param event Das AsyncPlayerChatEvent.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true) // Ignoriere gecancelte Events
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Hole die Gruppe des Spielers (inkl. Fallback auf Default)
        Group group = playerDataManager.getPlayerGroup(player.getUniqueId());

        // Sollte nie null sein, wenn Default-Gruppe existiert..
        if (group == null) {
            plugin.getLogger().warning("Could not get group for player " + player.getName() + " in AsyncPlayerChatEvent!");
            // Format bleibt unverändert
            return;
        }

        String format = "%1$s" + ChatColor.RESET + ": %2$s";
        event.setFormat(format);

        // TODO: wenn komplexere Formatierung gewünscht ist,-> ggf. canceln und mit Components arbeiten und Prefix erst hier setzen
    }
}