package eu.rawora.playLegendTask.managers;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.model.Group;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GroupManager {

    private final PlayLegendTask plugin;
    // Cache für Gruppen: Key = lowercase group name, Value = Group object
    // ConcurrentHashMap, da DB-Operationen asynchron laufen und Cache ggf. von Callbacks geändert wird.
    private final Map<String, Group> groupCache = new ConcurrentHashMap<>();
    private final String defaultGroupName; // Name der Default-Gruppe aus Config

    public GroupManager(PlayLegendTask plugin) {
        this.plugin = plugin;
        this.defaultGroupName = plugin.getConfigManager().getDefaultGroupName();
    }

    /**
     * Lädt alle Gruppen asynchron aus der Datenbank in den Cache.
     * Wird normalerweise beim Plugin-Start aufgerufen.
     */
    public void loadGroupsFromDatabase() {
         plugin.getLogger().info("Loading groups from database...");
         plugin.getDatabaseManager().getAllGroupsAsync().whenCompleteAsync((groups, throwable) -> {
             // Dieser Code wird ausgeführt, wenn die DB-Abfrage fertig ist
             if (throwable != null) {
                 // Fehler beim Laden
                 plugin.getLogger().log(Level.SEVERE, "Failed to load groups from database!", throwable);
                 ensureDefaultGroupExists();
                 return;
             }

             if (groups != null) {
                 groupCache.clear(); // Alten Cache leeren
                 int count = 0;
                 for (Group group : groups) {
                     // Speichere im Cache mit lowercase Namen für einfache Abfrage
                     groupCache.put(group.getName().toLowerCase(), group);
                     count++;
                 }
                 plugin.getLogger().info("Successfully loaded " + count + " groups into cache.");
                 ensureDefaultGroupExists();
             } else {
                  // Sollte nicht passieren, außer DB ist leer/Fehler
                  plugin.getLogger().warning("No groups returned from database. Ensuring default group exists.");
                  ensureDefaultGroupExists();
             }
         }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Führe Cache-Update im Hauptthread aus
    }


    /**
     * Holt eine Gruppe anhand ihres Namens aus dem Cache (ignoriert Groß-/Kleinschreibung).
     * @param name Der Name der Gruppe.
     * @return Das Group-Objekt oder null, wenn nicht gefunden.
     */
    public Group getGroup(String name) {
        if (name == null) return null;
        return groupCache.get(name.toLowerCase());
    }

    /**
     * Holt die Default-Gruppe aus dem Cache.
     * Falls sie (noch) nicht im Cache ist, wird versucht sie zu laden/erstellen.
     * @return Das Default-Group-Objekt oder null im Fehlerfall (sollte nicht passieren).
     */
    public Group getDefaultGroup() {
        Group defaultGroup = getGroup(defaultGroupName);
        if (defaultGroup == null) {
             // Dies kann passieren, wenn direkt nach dem Start darauf zugegriffen wird,
             // bevor loadGroupsFromDatabase den Cache gefüllt hat oder wenn Default in DB fehlt.
             plugin.getLogger().warning("Default group '" + defaultGroupName + "' not found in cache! Attempting to ensure existence...");
             // DAher: Versuche synchron , die Gruppe zu erstellen/laden. (TODO: oder besser asynchron mit Warten)
             // Für Einfachheit hier nur Log, ensureDefaultGroupExists wird von loadGroups aufgerufen.
             // Im Notfall könnte man hier ein temporäres Default-Objekt zurückgeben.
             return null; // Oder temporäres Fallback-Objekt
        }
        return defaultGroup;
    }

    /**
     * Gibt eine (nicht modifizierbare) Sammlung aller gecachten Gruppen zurück.
     * @return Collection aller Gruppen.
     */
    public Collection<Group> getAllGroups() {
        return List.copyOf(groupCache.values());
    }

    /**
     * Prüft, ob eine Gruppe mit dem gegebenen Namen im Cache existiert (ignoriert Groß-/Kleinschreibung).
     * @param name Der Gruppenname.
     * @return true, wenn die Gruppe existiert, sonst false.
     */
    public boolean groupExists(String name) {
        if (name == null) return false;
        return groupCache.containsKey(name.toLowerCase());
    }

    /**
     * Erstellt eine neue Gruppe, speichert sie asynchron in der DB und fügt sie zum Cache hinzu.
     * @param name Der Name der neuen Gruppe.
     * @param prefix Der Prefix der neuen Gruppe (mit '&' Farbcodes).
     * @return CompletableFuture<Boolean> - true bei Erfolg, false wenn Gruppe schon existiert oder Fehler auftritt.
     */
    public CompletableFuture<Boolean> createGroup(String name, String prefix) {
        String lowerCaseName = name.toLowerCase();
        if (groupExists(lowerCaseName)) {
            return CompletableFuture.completedFuture(false); // Direkt false zurückgeben
        }

        Group newGroup = new Group(name, prefix);

        return plugin.getDatabaseManager().saveGroupAsync(newGroup)
                .thenApplyAsync(v -> {
                    // Bei Erfolg: Füge zum Cache hinzu (im Hauptthread)
                    groupCache.put(lowerCaseName, newGroup);
                    plugin.getLogger().info("Group '" + name + "' created and cached.");
                    return true; // Erfolg
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
                .exceptionally(throwable -> {
                     // Bei Fehler während DB-Operation
                     plugin.getLogger().log(Level.SEVERE, "Failed to save created group to DB: " + name, throwable);
                     return false; // Fail
                 });
    }


    /**
     * Löscht eine Gruppe asynchron aus der DB und entfernt sie aus dem Cache.
     * Die Default-Gruppe kann nicht gelöscht werden.
     * @param name Der Name der zu löschenden Gruppe.
     * @return CompletableFuture<Boolean> - true bei Erfolg, false wenn Gruppe nicht existiert, Default ist oder Fehler auftritt.
     */
    public CompletableFuture<Boolean> deleteGroup(String name) {
        String lowerCaseName = name.toLowerCase();
        if (!groupExists(lowerCaseName)) {
            return CompletableFuture.completedFuture(false); // Existiert nicht
        }
        if (lowerCaseName.equals(defaultGroupName.toLowerCase())) {
            return CompletableFuture.completedFuture(false); // Default kann nicht gelöscht werden
        }

        // Lösche asynchron aus der DB
        // Nutze Originalnamen, falls DB case-sensitive ist
        Group groupToDelete = getGroup(lowerCaseName); // Hole Originalnamen aus Cache
        return plugin.getDatabaseManager().deleteGroupAsync(groupToDelete.getName())
                .thenApplyAsync(v -> {
                    // Bei Erfolg: Entferne aus Cache (im Hauptthread)
                    groupCache.remove(lowerCaseName);
                    plugin.getLogger().info("Group '" + name + "' deleted from database and cache.");
                    // Spieler in dieser Gruppe werden durch DB Foreign Key oder PlayerDataManager behandelt
                    return true; // Erfolg
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
                .exceptionally(throwable -> {
                    // Bei Fehler während DB-Operation
                    plugin.getLogger().log(Level.SEVERE, "Failed to delete group from DB: " + name, throwable);
                    return false; // Failed
                });
    }

    /**
     * Setzt den Prefix einer Gruppe, aktualisiert DB (asynchron) und Cache.
     * Informiert den PlayerDataManager, um Online-Spieler zu aktualisieren.
     * @param name Der Name der Gruppe.
     * @param prefix Der neue Prefix (mit '&' Farbcodes).
     * @return CompletableFuture<Boolean> - true bei Erfolg, false wenn Gruppe nicht existiert oder Fehler auftritt.
     */
     public CompletableFuture<Boolean> setGroupPrefix(String name, String prefix) {
        String lowerCaseName = name.toLowerCase();
        Group group = getGroup(lowerCaseName);
        if (group == null) {
            return CompletableFuture.completedFuture(false); // Gruppe existiert nicht
        }

        return plugin.getDatabaseManager().updateGroupPrefixAsync(group.getName(), prefix)
                .thenApplyAsync(v -> {
                    group.setPrefix(prefix);
                    plugin.getLogger().info("Prefix for group '" + group.getName() + "' updated in database and cache.");

                    plugin.getPlayerDataManager().updatePrefixForGroup(group);
                    return true; // Erfolg
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable))
                .exceptionally(throwable -> {
                    // Bei Fehler während DB-Operation
                    plugin.getLogger().log(Level.SEVERE, "Failed to update prefix for group in DB: " + name, throwable);
                    return false;
                });
    }

    /**
     * Stellt sicher, dass die Default-Gruppe (aus config.yml) existiert.
     * Wird nach dem Laden aus der DB und ggf. bei Fehlern aufgerufen.
     * Erstellt die Gruppe, falls sie fehlt.
     */
    public void ensureDefaultGroupExists() {
        String defaultName = plugin.getConfigManager().getDefaultGroupName();
        if (!groupExists(defaultName)) {
             plugin.getLogger().warning("Default group '" + defaultName + "' not found in cache/DB. Creating it now...");
             // Definiere einen Standard-Prefix, falls die Gruppe neu erstellt wird
             String defaultPrefix = "&7"; // Einfaches Grau ?

             // Erstelle die Gruppe (diese Methode speichert auch in DB und Cache)
             createGroup(defaultName, defaultPrefix).whenComplete((success, throwable) -> {
                 if (!success || throwable != null) {
                      // Dies ist ein kritischer Fehler, da die Default-Gruppe essentiell ist
                      plugin.getLogger().severe("!!! CRITICAL: Failed to create default group '" + defaultName + "'! Plugin might not work correctly. !!!");
                 } else {
                      plugin.getLogger().info("Default group '" + defaultName + "' created successfully.");
                 }
                 // Kein weiterer Callback hier nötig, da createGroup das Logging/Caching übernimmt
             });
        }
    }
}