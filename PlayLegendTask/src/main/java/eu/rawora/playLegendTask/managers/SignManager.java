package eu.rawora.playLegendTask.managers;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.model.Group;
import eu.rawora.playLegendTask.model.PlayerGroupInfo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class SignManager {

    private final PlayLegendTask plugin;
    // Cache für Schild-Positionen: Location -> Target Player UUID
    // ConcurrentHashMap für Thread-Sicherheit, da Updates aus async Task kommen können
    private final Map<Location, UUID> signLocations = new ConcurrentHashMap<>();

    public SignManager(PlayLegendTask plugin) {
        this.plugin = plugin;
    }

    /**
     * Lädt die Positionen und Ziel-UUIDs aller Info-Schilder asynchron aus der Datenbank.
     */
    public void loadSigns() {
        // Nur laden, wenn Schilder in der Config aktiviert sind
        if (!plugin.getConfigManager().isSignsEnabled()) {
            plugin.getLogger().info("Sign loading skipped (Signs disabled in config).");
            return;
        }

        plugin.getLogger().info("Loading sign locations from database...");
        plugin.getDatabaseManager().getAllSignLocationsAsync().whenCompleteAsync((loadedSigns, throwable) -> {
            // Callback im Hauptthread ausführen
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load sign locations from database", throwable);
                return;
            }
            if (loadedSigns != null) {
                signLocations.clear(); // Alten Cache leeren
                signLocations.putAll(loadedSigns); // Neuen Cache füllen
                plugin.getLogger().info("Successfully loaded " + signLocations.size() + " group info sign locations.");
                // TODO Optional: Erste Aktualisierung aller Schilder nach dem Laden anstoßen
                 // updateAllSigns(); // Wird vom Task in PlayLegendTask übernommen
            } else {
                 plugin.getLogger().info("No sign locations found in database.");
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Cache-Update im Hauptthread
    }


    /**
     * Speichert alle bekannten Schild-Positionen in der Datenbank (asynchron).
     * Wird aktuell nicht explizit beim Beenden aufgerufen, da add/remove direkt speichern.
     * Könnte nützlich sein, falls In-Memory-Änderungen möglich wären..
     */
    public void saveSigns() {
        if (!plugin.getConfigManager().isSignsEnabled() || signLocations.isEmpty()) return;

        plugin.getLogger().info("Saving " + signLocations.size() + " sign locations (triggered by disable - individual add/remove handle live saves)...");
        // Normalerweise reicht das Speichern bei Add/Remove. Falls ein vollständiges Speichern
        // auf onDisable gewünscht ist, müsste man hier durch die Map iterieren und saveSignLocationAsync aufrufen.
        // Aber könnte bei vielen Schildern dauern. Ein Beispiel:
        /*
        signLocations.forEach((loc, uuid) -> {
            plugin.getDatabaseManager().saveSignLocationAsync(loc, uuid)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save sign location on disable: " + loc, throwable);
                    return null;
                });
        });
        */
         plugin.getLogger().info("Sign saving process initiated (if implemented for bulk save).");
    }

    /**
     * Fügt ein neues Info-Schild hinzu (speichert in DB und Cache).
     * Aktualisiert das Schild sofort.
     * @param location Der Standort des Schilds.
     * @param targetPlayerUUID Die UUID des Spielers, dessen Infos angezeigt werden sollen.
     */
    public void addSign(Location location, UUID targetPlayerUUID) {
         if (!plugin.getConfigManager().isSignsEnabled()) return; // Nichts tun, wenn deaktiviert

        // Füge zum Cache hinzu
        signLocations.put(location, targetPlayerUUID);

        // Speichere asynchron in der Datenbank
        plugin.getDatabaseManager().saveSignLocationAsync(location, targetPlayerUUID)
                .exceptionally(throwable -> {
                     plugin.getLogger().log(Level.SEVERE, "Failed to save new sign location to DB: " + location, throwable);
                     // TO DO Optional: ggf Aus Cache wieder entfernen, wenn DB-Speichern fehlschlägt?
                     // signLocations.remove(location);
                     return null; // Fehler behandeln
                 });

        // Aktualisiere das Schild sofort mit den aktuellen Daten (im Hauptthread)
        updateSign(location, targetPlayerUUID);
        plugin.getLogger().info("Added info sign at " + location + " tracking player " + targetPlayerUUID);
    }

    /**
     * Entfernt ein Info-Schild (aus DB und Cache).
     * @param location Der Standort des Schilds.
     */
    public void removeSign(Location location) {
        if (!plugin.getConfigManager().isSignsEnabled()) return; // Nichts tun, wenn deaktiviert

        // Entferne aus Cache, wenn vorhanden
        if (signLocations.containsKey(location)) {
            signLocations.remove(location);

            // Lösche asynchron aus der Datenbank
            plugin.getDatabaseManager().deleteSignLocationAsync(location)
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(Level.SEVERE, "Failed to delete sign location from DB: " + location, throwable);
                        // Fehler beim Löschen aus DB ist ärgerlich, aber Cache ist schon sauber.
                        return null;
                    });
            plugin.getLogger().info("Removed info sign at " + location);
        }
    }

    /**
     * Prüft, ob sich am gegebenen Standort ein registriertes Info-Schild befindet.
     * @param location Der Standort.
     * @return true, wenn es ein Info-Schild ist, sonst false.
     */
    public boolean isGroupInfoSign(Location location) {
        // Prüfe nur, ob die Location im Cache ist
        return signLocations.containsKey(location);
    }

    /**
     * Gibt die UUID des Spielers zurück, der von diesem Schild getrackt wird.
     * @param location Der Standort des Schilds.
     * @return Die UUID des Zielspielers oder null, wenn kein Info-Schild an diesem Ort.
     */
    public UUID getSignTargetPlayerUUID(Location location) {
        return signLocations.get(location);
    }

    /**
     * Aktualisiert den Text eines einzelnen Info-Schilds.
     * Holt die notwendigen Spieler- und Gruppendaten.
     * Muss im Bukkit-Hauptthread ausgeführt werden, da es die Welt verändert.
     * @param location Der Standort des Schilds.
     * @param targetPlayerUUID Die UUID des anzuzeigenden Spielers.
     */
    public void updateSign(Location location, UUID targetPlayerUUID) {
        // Stelle sicher, dass wir im Hauptthread sind, da wir Block-States ändern
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> updateSign(location, targetPlayerUUID));
            return;
        }
        if (!plugin.getConfigManager().isSignsEnabled()) return; // Prüfe erneut, falls config geändert wurde

        // Prüfe, ob die Welt geladen ist
        if (location.getWorld() == null || !location.isWorldLoaded()) {
            plugin.getLogger().fine("Skipping sign update, world not loaded: " + location);
            return;
        }

        if (!location.isChunkLoaded()) {
             plugin.getLogger().fine("Skipping sign update, chunk not loaded: " + location);
            return; // Nicht aktualisieren, wenn Chunk nicht geladen ist
        }

        Block block = location.getBlock();
        // Prüfe, ob es immer noch ein Schild ist UND ob es noch in unserem Cache ist
        if (signLocations.containsKey(location) && block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetPlayerUUID); // Geht auch für offline Spieler

            // --- Daten für das Schild holen ---
            Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
            Group group;
            PlayerGroupInfo info = null;

            if (onlinePlayer != null) {
                group = plugin.getPlayerDataManager().getPlayerGroup(targetPlayerUUID);
                info = plugin.getPlayerDataManager().getPlayerGroupInfo(targetPlayerUUID);
            } else {
                // TODO Spieler ist offline -> Versuche, letzte Daten aus DB zu holen (komplexer)
                // dies erfordert eine zusätzliche Methode im DatabaseManager / PlayerDataManager,
                // die getPlayerGroupInfoAsync auch für Offline-Spieler aufruft..
                // Aber jetzt für dieses Beispiel zeigen wir vereinfacht den Namen und "Offline" oder Default-Gruppe an.
                group = plugin.getGroupManager().getDefaultGroup(); // Zeige Default-Gruppe für Offline-Spieler?
                // TODO: Implementiere ggf. eine Methode, um letzte bekannte Gruppe aus DB zu laden.
                // PlayerGroupInfo offlineInfo = syncGetOfflinePlayerData(targetPlayerUUID); // Blockierender Aufruf wäre jedoch schlecht
            }

            // Sicherheitscheck: Haben wir eine Gruppe? (Sollte Default liefern, wenn alles andere fehlschlägt)
            if (group == null) {
                 plugin.getLogger().severe("CRITICAL: Cannot update sign at " + location + ", group is null (even default)!");
                 // Setze Fehlertext auf Schild
                 sign.setLine(0, ChatColor.RED + "Error");
                 sign.setLine(1, ChatColor.RED + "Group Data");
                 sign.setLine(2, ChatColor.RED + "Unavailable");
                 sign.setLine(3, "");
                 sign.update(true); // force=true, um Caching zu umgehen
                 return;
            }

            // --- Schild-Text formatieren ---
            String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown"; // Name holen

            String line1 = plugin.getPlayerDataManager().formatString(plugin.getConfigManager().getSignLine(1), onlinePlayer, group);
            String line2 = plugin.getPlayerDataManager().formatString(plugin.getConfigManager().getSignLine(2), onlinePlayer, group);
            String line3 = plugin.getPlayerDataManager().formatString(plugin.getConfigManager().getSignLine(3), onlinePlayer, group);
            String line4 = plugin.getPlayerDataManager().formatString(plugin.getConfigManager().getSignLine(4), onlinePlayer, group);

            if (onlinePlayer == null) {
                line1 = line1.replace("%player%", playerName);
                line2 = line2.replace("%player%", playerName);
                line3 = line3.replace("%player%", playerName);
                line4 = line4.replace("%player%", playerName);
                line4 = line4.replace("Permanent", "Offline").replaceFirst("Expires:.*", ChatColor.GRAY + "Offline");
            }

            // --- Schild aktualisieren ---
            // Prüfe, ob sich der Text geändert hat, um unnötige Updates zu vermeiden (optional)
            boolean changed = !sign.getLine(0).equals(line1) || !sign.getLine(1).equals(line2) ||
                              !sign.getLine(2).equals(line3) || !sign.getLine(3).equals(line4);

            if (changed) {
                sign.setLine(0, line1);
                sign.setLine(1, line2);
                sign.setLine(2, line3);
                sign.setLine(3, line4);
                sign.update(true);
            }

        } else {
            // Der Block ist kein Schild mehr oder wurde aus dem Cache entfernt (z.B. durch Zerstören)
            if (signLocations.containsKey(location)) {
                 plugin.getLogger().info("Removing broken or unregistered sign location during update: " + location);
                 removeSign(location); // Bereinige DB und Cache
            }
        }
    }

    /**
     * Aktualisiert alle registrierten Info-Schilder.
     * Wird vom periodischen Task in PlayLegendTask aufgerufen.
     * Führt updateSign für jedes Schild aus (welches dann im Hauptthread läuft).
     */
    public void updateAllSigns() {
        if (!plugin.getConfigManager().isSignsEnabled() || signLocations.isEmpty()) return;

        // Iteriere sicher über eine Kopie der Einträge, falls die Map währenddessen geändert wird
         new HashSet<>(signLocations.entrySet()).forEach(entry -> {
             updateSign(entry.getKey(), entry.getValue());
         });
    }

    /**
     * Aktualisiert alle Schilder, die Spieler einer bestimmten Gruppe anzeigen,
     * nachdem sich z.B. der Prefix dieser Gruppe geändert hat.
     * @param changedGroup Die geänderte Gruppe.
     */
    public void updateSignsForGroup(Group changedGroup) {
         if (!plugin.getConfigManager().isSignsEnabled() || signLocations.isEmpty()) return;

         String lowerCaseGroupName = changedGroup.getName().toLowerCase();

         signLocations.forEach((location, uuid) -> {
             // Dies erfordert, die Gruppe des Spielers zu kennen (Cache für online, ggf. DB für offline)
             Player onlinePlayer = Bukkit.getPlayer(uuid);
             if (onlinePlayer != null && onlinePlayer.isOnline()) {
                 PlayerGroupInfo info = plugin.getPlayerDataManager().getPlayerGroupInfo(uuid);
                 if (info != null && info.getGroupName().equalsIgnoreCase(lowerCaseGroupName)) {
                     updateSign(location, uuid); // Update das Schild
                 }
             } else {
                 // Spieler ist offline - Prüfung ist schwieriger
                 // TODO: Implementiere ggf. DB-Lookup für Offline-Spieler oder ignoriere Offline-Spieler bei diesem Trigger
             }
         });
    }
}