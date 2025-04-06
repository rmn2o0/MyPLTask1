package eu.rawora.playLegendTask.listeners;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.managers.ConfigManager;
import eu.rawora.playLegendTask.managers.SignManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

import java.util.UUID;

public class SignListener implements Listener {

    private final PlayLegendTask plugin;
    private final SignManager signManager;
    private final ConfigManager configManager;
    private final String creationIdentifier; // Identifier aus config.yml

    public SignListener(PlayLegendTask plugin) {
        this.plugin = plugin;
        this.signManager = plugin.getSignManager();
        this.configManager = plugin.getConfigManager();
        // Lese den Identifier aus der Config für einfachen Zugriff
        this.creationIdentifier = ChatColor.stripColor(configManager.getSignCreationIdentifier()).toLowerCase(); // Ohne Farben und lowercase für Vergleich
    }

    /**
     * Wird aufgerufen, wenn ein Spieler ein Schild bearbeitet und auf "Fertig" klickt.
     * Prüft, ob ein Info-Schild erstellt werden soll.
     * @param event Das SignChangeEvent.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Hole die Zeilen vom Schild (können null sein)
        String line1 = event.getLine(0); // Erste Zeile für Identifier
        String line2 = event.getLine(1); // Zweite Zeile für Target spieler name

        // Prüfe, ob Zeile 1 den Identifier enthält (ignoriere Farben/Groß-Klein)
        if (line1 != null && ChatColor.stripColor(line1).trim().equalsIgnoreCase(creationIdentifier)) {

            // TODO: Optional: Permission zum Erstellen von Schildern prüfen ?
            // if (!player.hasPermission("playlegendtask.admin.createsign")) {
            //     player.sendMessage(configManager.getPrefixedMessage("error.no-permission"));
            //     event.setCancelled(true); // Verhindere Schild-Erstellung
            //     block.breakNaturally(); // Zerstöre das Schild wieder
            //     return;
            // }

            // Prüfe, ob Zeile 2 einen Spielernamen enthält
            if (line2 == null || line2.trim().isEmpty()) {
                player.sendMessage(configManager.getFormattedPrefixedMessage("sign.error-creating", "%player%", "No Player Name"));
                event.setCancelled(true);
                 block.breakNaturally();
                return;
            }

            String targetPlayerName = line2.trim();

            // Finde den Target Spieler (online oder offline)
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
            UUID targetUUID;

            try {
                targetUUID = targetPlayer.getUniqueId();
                // Prüfe, ob Spielername gültig ist / UUID gefunden wurde
                if (targetUUID == null || targetPlayer.getName() == null) {
                     player.sendMessage(configManager.getFormattedPrefixedMessage("sign.error-creating", "%player%", targetPlayerName));
                     event.setCancelled(true);
                     block.breakNaturally();
                     return;
                 }
            } catch (Exception e) {
                player.sendMessage(configManager.getFormattedPrefixedMessage("sign.error-creating", "%player%", targetPlayerName));
                event.setCancelled(true);
                block.breakNaturally();
                return;
            }

            // Registriere das Schild beim SignManager
            Location location = block.getLocation();
            signManager.addSign(location, targetUUID);

            // Setze den finalen Text auf dem Schild (optional, da der Updater das  übernimmt)
            // Man könnte hier schon den formatierten Text setzen, aber der Updater ist zuverlässiger.
            event.setLine(0, configManager.getSignLine(1));
            event.setLine(1, configManager.getSignLine(2).replace("%player%", targetPlayer.getName()));
            event.setLine(2, configManager.getSignLine(3));
            event.setLine(3, configManager.getSignLine(4));

            player.sendMessage(configManager.getPrefixedMessage("sign.created"));
        }
        // Wenn Zeile 1 nicht passt, ignoriere das Event (also normales Schild)
    }

    /**
     * Wird aufgerufen, wenn ein Block zerstört wird.
     * Prüft, ob es sich um ein registriertes Info-Schild handelt und entfernt es ggf.
     * @param event Das BlockBreakEvent.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Prüfe, ob der Block ein Schild ist und ob seine Location im SignManager registriert ist
        if (block.getState() instanceof Sign && signManager.isGroupInfoSign(block.getLocation())) {

            // TODO: Optional: Permission zum Zerstören von Info-Schildern prüfen ?
            // if (!player.hasPermission("playlegendtask.admin.breaksign")) {
            //     player.sendMessage(configManager.getPrefixedMessage("error.no-permission"));
            //     event.setCancelled(true); // Verhindere Zerstörung
            //     return;
            // }

            signManager.removeSign(block.getLocation());

            player.sendMessage(configManager.getPrefixedMessage("sign.broken"));
        }
    }
}