package eu.rawora.playLegendTask.db;

import eu.rawora.playLegendTask.model.Group;
import eu.rawora.playLegendTask.model.PlayerGroupInfo;
import org.bukkit.Location;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface für alle notwendigen Datenbankoperationen
 * alle werden asynchron ausgeführt , um den Server-Thread nicht zu blockieren.
 */
public interface DatabaseManager {

    /**
     * Baut die Verbindung zur Datenbank auf.
     * @throws Exception Wenn die Verbindung fehlschlägt.
     */
    void connect() throws Exception;

    /**
     * Schließt die Datenbankverbindung sicher.
     */
    void disconnect();

    /**
     * Initialisiert die Datenbankstruktur (erstellt Tabellen, falls sie nicht existieren).
     */
    void initializeDatabase();

    // --- Gruppen-Operationen (Groups) ---

    /**
     * Speichert eine Gruppe (neu oder überschreibt bestehende).
     * @param group Das zu speichernde Group-Objekt.
     * @return Ein CompletableFuture, das abgeschlossen wird, wenn die Operation beendet ist.
     */
    CompletableFuture<Void> saveGroupAsync(Group group);

    /**
     * Löscht eine Gruppe anhand ihres Namens.
     * @param groupName Der Name der zu löschenden Gruppe.
     * @return Ein CompletableFuture, das abgeschlossen wird, wenn die Operation beendet ist.
     */
    CompletableFuture<Void> deleteGroupAsync(String groupName);

    /**
     * Holt eine spezifische Gruppe anhand ihres Namens.
     * @param groupName Der Name der Gruppe.
     * @return Ein CompletableFuture, das das Group-Objekt oder null (wenn nicht gefunden) enthält.
     */
    CompletableFuture<Group> getGroupAsync(String groupName);

    /**
     * Holt alle definierten Gruppen aus der Datenbank.
     * @return Ein CompletableFuture, das eine Liste aller Group-Objekte enthält.
     */
    CompletableFuture<List<Group>> getAllGroupsAsync();

    /**
     * Aktualisiert den Prefix einer bestehenden Gruppe.
     * @param groupName Der Name der Gruppe.
     * @param prefix Der neue Prefix.
     * @return Ein CompletableFuture, das abgeschlossen wird, wenn die Operation beendet ist.
     */
    CompletableFuture<Void> updateGroupPrefixAsync(String groupName, String prefix);

    // --- Spieler-Operationen (Player Assignments) ---

    /**
     * Weist einem Spieler eine Gruppe zu (permanent oder temporär).
     * Überschreibt eine eventuell bestehende Zuweisung für den Spieler.
     * @param playerUUID Die UUID des Spielers.
     * @param groupName Der Name der Gruppe.
     * @param expiryTime Der Unix-Timestamp (ms), wann die Gruppe abläuft, oder null für permanent.
     * @return Ein CompletableFuture, das abgeschlossen wird, wenn die Operation beendet ist.
     */
    CompletableFuture<Void> setPlayerGroupAsync(UUID playerUUID, String groupName, Long expiryTime);

    /**
     * Holt die Gruppeninformationen für einen Spieler.
     * @param playerUUID Die UUID des Spielers.
     * @return Ein CompletableFuture, das das PlayerGroupInfo-Objekt oder null (wenn nicht gefunden) enthält.
     */
    CompletableFuture<PlayerGroupInfo> getPlayerGroupInfoAsync(UUID playerUUID);

    /**
     * Entfernt explizit die Gruppenzuweisung eines Spielers (selten nötig, da setPlayerGroup überschreibt).
     * @param playerUUID Die UUID des Spielers.
     * @return Ein CompletableFuture, das abgeschlossen wird, wenn die Operation beendet ist.
     */
    CompletableFuture<Void> removePlayerFromGroupAsync(UUID playerUUID);


    // --- Schilder-Operationen (Info Signs) ---

    /**
     * Speichert den Standort eines Info-Schilds und den zugehörigen Spieler.
     * Überschreibt, falls an diesem Standort bereits ein Schild gespeichert war.
     * @param location Der Standort des Schilds.
     * @param targetPlayerUUID Die UUID des Spielers, dessen Infos angezeigt werden sollen.
     * @return Ein CompletableFuture, das abgeschlossen wird, wenn die Operation beendet ist.
     */
    CompletableFuture<Void> saveSignLocationAsync(Location location, UUID targetPlayerUUID);

    /**
     * Löscht einen gespeicherten Schild-Standort.
     * @param location Der Standort des Schilds.
     * @return Ein CompletableFuture, das abgeschlossen wird, wenn die Operation beendet ist.
     */
    CompletableFuture<Void> deleteSignLocationAsync(Location location);

    /**
     * Lädt alle gespeicherten Schild-Standorte aus der Datenbank.
     * @return Ein CompletableFuture, das eine Map von Location zu TargetPlayerUUID enthält.
     */
    CompletableFuture<Map<Location, UUID>> getAllSignLocationsAsync();

}