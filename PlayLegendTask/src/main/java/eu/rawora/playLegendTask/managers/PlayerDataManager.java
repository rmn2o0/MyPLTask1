package eu.rawora.playLegendTask.managers;

import eu.rawora.playLegendTask.PlayLegendTask;
import eu.rawora.playLegendTask.model.Group;
import eu.rawora.playLegendTask.model.PlayerGroupInfo;
import eu.rawora.playLegendTask.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager {

    private final PlayLegendTask plugin;
    // Cache für Online-Spielerdaten: UUID -> PlayerGroupInfo
    private final Map<UUID, PlayerGroupInfo> onlinePlayerData = new ConcurrentHashMap<>();
    // Cache für individuelle Spieler-Scoreboards
    private final Map<UUID, Scoreboard> playerBoards = new ConcurrentHashMap<>();

    public PlayerDataManager(PlayLegendTask plugin) {
        this.plugin = plugin;
        // Starte den Task, der abgelaufene Gruppen prüft
        startExpiryCheckTask();
    }

    /**
     * Lädt die Gruppendaten eines Spielers asynchron, wenn er den Server betritt.
     * Weist Default-Gruppe zu, falls keine Daten, Gruppe ungültig oder abgelaufen.
     * Aktualisiert danach die visuellen Elemente des Spielers.
     *
     * @param player Der Spieler, der beitritt.
     */
    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        getLogger().info("Loading group data for player: " + player.getName() + " (UUID: " + uuid + ")");

        plugin.getDatabaseManager().getPlayerGroupInfoAsync(uuid).whenCompleteAsync((info, throwable) -> {

            if (throwable != null) {
                getLogger().log(Level.SEVERE, "Failed to load player data from DB for " + player.getName(), throwable);
                // Fallback: Default-Gruppe zuweisen
                assignDefaultGroup(player);
                return;
            }

            if (info != null) {
                // Daten aus DB erhalten
                Group group = plugin.getGroupManager().getGroup(info.getGroupName());

                if (group == null) {
                    // Gruppe aus DB existiert nicht mehr im Cache/System
                    getLogger().warning("Player " + player.getName() + " references group '" + info.getGroupName()
                            + "' which no longer exists. Assigning default group.");
                    assignDefaultGroup(player);
                } else if (info.hasExpired()) {
                    // Temporäre Gruppe ist abgelaufen
                    getLogger().info("Player " + player.getName() + "'s temporary group '" + info.getGroupName()
                            + "' has expired. Assigning default group.");
                    assignDefaultGroup(player);
                } else {
                    // Gültige Daten gefunden
                    // Speichere im Cache (dies kann im Async-Thread passieren, da ConcurrentHashMap)
                    onlinePlayerData.put(uuid, info);
                    getLogger().info("Loaded group data for " + player.getName() + ": Group=" + info.getGroupName()
                            + ", Expiry=" + (info.isPermanent() ? "Permanent" : info.getExpiryTime()));
                    // Visuelle Updates müssen im Hauptthread passieren!
                    Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisuals(player));
                }
            } else {
                // Spieler nicht in der DB gefunden -> Default-Gruppe zuweisen
                getLogger().info("No group data found for " + player.getName() + ". Assigning default group.");
                assignDefaultGroup(player);
            }
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Stelle sicher, dass der Callback im Hauptthread ausgeführt wird!
    }

    /**
     * Weist einem Spieler die Default-Gruppe zu (im Cache und speichert in DB).
     * Muss im Bukkit-Hauptthread ausgeführt werden, da es ggf. visuals aktualisiert.
     *
     * @param player Der Spieler.
     */
    private void assignDefaultGroup(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> assignDefaultGroup(player));
            return;
        }

        Group defaultGroup = plugin.getGroupManager().getDefaultGroup();
        if (defaultGroup == null) {
            // Kritischer Fehler, Default-Gruppe nicht verfügbar
            getLogger().severe("CRITICAL: Default group is null! Cannot assign default group to " + player.getName());
            // TODO optional ggf. Spieler kicken?Oder nur loggen?
            return;
        }

        UUID uuid = player.getUniqueId();
        // Erstelle Info-Objekt für Default-Gruppe (immer permanent)
        PlayerGroupInfo defaultInfo = new PlayerGroupInfo(uuid, defaultGroup.getName(), null);
        onlinePlayerData.put(uuid, defaultInfo);

        // Speichere Änderung asynchron in der Datenbank
        plugin.getDatabaseManager().setPlayerGroupAsync(uuid, defaultGroup.getName(), null)
                .exceptionally(ex -> {
                    // Logge Fehler beim DB-Speichern
                    getLogger().log(Level.SEVERE, "Failed to save default group assignment to DB for " + player.getName(), ex);
                    return null;
                });

        // Aktualisiere sofort die visuellen Elemente des Spielers
        updatePlayerVisuals(player);
        getLogger().info("Assigned default group '" + defaultGroup.getName() + "' to " + player.getName());

        // Sende dem Spieler ggf. eine Nachricht über den Gruppenwechsel
        PlayerGroupInfo oldInfo = onlinePlayerData.get(uuid);
        if (oldInfo != null && !oldInfo.isPermanent() && oldInfo.hasExpired()) {
            player.sendMessage(plugin.getConfigManager().getPrefixedMessage("expiry.expired-notice"));
        }
    }

    /**
     * Entfernt die Daten eines Spielers aus dem Cache, wenn er den Server verlässt.
     * Setzt auch Permissions zurück.
     *
     * @param player Der Spieler, der geht.
     */
    public void unloadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        onlinePlayerData.remove(uuid);
        plugin.getPermissionManager().removeAttachment(player); // Permission Attachment entfernen
        playerBoards.remove(uuid); // Scoreboard aus Cache entfernen
        getLogger().info("Unloaded group data for " + player.getName());
    }

    /**
     * Holt die gecachten Gruppeninformationen für einen online Spieler.
     *
     * @param uuid Die UUID des Spielers.
     * @return PlayerGroupInfo oder null, wenn Spieler nicht online oder keine Daten.
     */
    public PlayerGroupInfo getPlayerGroupInfo(UUID uuid) {
        return onlinePlayerData.get(uuid);
    }

    /**
     * Holt das tatsächliche Group-Objekt für einen online Spieler aus dem Cache.
     * Greift auf die Default-Gruppe zurück, falls der Spieler keine gültige Gruppe hat.
     *
     * @param uuid Die UUID des Spielers.
     * @return Das Group-Objekt oder das Default-Group-Objekt (oder null im Extremfall).
     */
    public Group getPlayerGroup(UUID uuid) {
        PlayerGroupInfo info = getPlayerGroupInfo(uuid);
        if (info != null) {
            Group group = plugin.getGroupManager().getGroup(info.getGroupName());
            if (group != null) {
                return group; // Gruppe gefunden und gültig
            }
            // Fallback, falls Gruppe aus PlayerInfo nicht mehr existiert (sollte durch loadPlayerData behandelt werden)
            getLogger().warning("Player " + uuid + " has invalid group '" + info.getGroupName() + "' in cache. Falling back to default.");
        }
        // Fallback zur Default-Gruppe, wenn Spieler keine Info hat oder Gruppe ungültig war
        return plugin.getGroupManager().getDefaultGroup();
    }


    /**
     * Weist einem Spieler (online oder offline) eine neue Gruppe zu.
     * Aktualisiert Cache (falls online) und DB (asynchron).
     * Gibt ein CompletableFuture zurück, das true bei Erfolg der DB-Operation oder false bei einem Fehler liefert.
     *
     * @param uuid           Die UUID des Spielers.
     * @param groupName      Der Name der Zielgruppe.
     * @param durationMillis Dauer in Millisekunden oder null/<=0 für permanent.
     * @return CompletableFuture<Boolean> - true bei Erfolg, false wenn Gruppe nicht existiert oder DB-Fehler.
     */
    public CompletableFuture<Boolean> setPlayerGroup(UUID uuid, String groupName, Long durationMillis) {
        final Group targetGroup = plugin.getGroupManager().getGroup(groupName); // Final für Lambda
        if (targetGroup == null) {
            getLogger().warning("Attempted to set player " + uuid + " to non-existent group: " + groupName);
            return CompletableFuture.completedFuture(false); // Zielgruppe existiert nicht
        }

        // Berechne Ablaufzeitpunkt (null für permanent)
        final Long expiryTime = (durationMillis == null || durationMillis <= 0) ? null : (System.currentTimeMillis() + durationMillis);
        // Erstelle neues Info-Objekt (final für Lambda)
        final PlayerGroupInfo newInfo = new PlayerGroupInfo(uuid, targetGroup.getName(), expiryTime);

        // Speichere asynchron in der Datenbank
        return plugin.getDatabaseManager().setPlayerGroupAsync(uuid, targetGroup.getName(), expiryTime)
                .thenRunAsync(() -> {
                    // Dieser Block wird NUR ausgeführt, wenn setPlayerGroupAsync ERFOLGREICH war (keine Exception)
                    // Führe Cache-Update etc. im Hauptthread aus (falls Spieler online)
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            onlinePlayerData.put(uuid, newInfo); // Update Cache
                            updatePlayerVisuals(player);       // Update Aussehen etc.

                            // Sende Nachricht an den Spieler über die Änderung
                            String timeString = (expiryTime == null) ? "Permanent" : TimeUtil.formatDuration(durationMillis);
                            if (newInfo.isPermanent()) {
                                player.sendMessage(plugin.getConfigManager().getFormattedPrefixedMessage("setgroup.updated-player", "%group%", targetGroup.getName()));
                            } else {
                                player.sendMessage(plugin.getConfigManager().getFormattedPrefixedMessage("setgroup.updated-player-temp", "%group%", targetGroup.getName(), "%time%", timeString));
                            }
                        });
                    }
                    // Logge Erfolg im Async-Thread
                    getLogger().info("Successfully initiated group update in DB for player " + uuid + " to '" + targetGroup.getName() + "'.");

                })
                .thenApply(v -> true)
                .exceptionally(throwable -> {
                    getLogger().log(Level.SEVERE, "Failed to set player group in DB for " + uuid, throwable);
                    return false;
                });
    }

    /**
     * Aktualisiert alle visuellen Aspekte eines Spielers basierend auf seiner aktuellen Gruppe.
     * (DisplayName, Tablist, Scoreboard, Permissions)
     * Muss im Bukkit-Hauptthread ausgeführt werden.
     *
     * @param player Der Spieler, der aktualisiert werden soll.
     */
    public void updatePlayerVisuals(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> updatePlayerVisuals(player));
            return;
        }
        if (player == null || !player.isOnline()) return; // Nur für online Spieler

        // Hole die aktuelle (oder Default) Gruppe des Spielers
        Group group = getPlayerGroup(player.getUniqueId());
        if (group == null) {
            getLogger().severe("CRITICAL: Could not get any group (not even default) for player " + player.getName() + " during visual update!");
            return;
        }

        String prefix = group.getPrefix();
        String displayName = prefix + player.getName();
        player.setDisplayName(displayName);

        if (plugin.getConfigManager().isTablistEnabled()) {
            String tabFormat = plugin.getConfigManager().getTablistFormat();
            String tabName = formatString(tabFormat, player, group);
            player.setPlayerListName(tabName);
        }

        plugin.getPermissionManager().updatePlayerPermissions(player, group);

        if (plugin.getConfigManager().isScoreboardEnabled()) {
            updateScoreboard(player);
        } else {
            if (playerBoards.containsKey(player.getUniqueId())) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                playerBoards.remove(player.getUniqueId());
            }
        }
    }

    /**
     * Wird vom GroupManager aufgerufen, wenn sich der Prefix einer Gruppe ändert.
     * Aktualisiert die visuellen Elemente aller online Spieler in dieser Gruppe.
     *
     * @param changedGroup Die Gruppe, deren Prefix geändert wurde.
     */
    public void updatePrefixForGroup(Group changedGroup) {
        String lowerCaseName = changedGroup.getName().toLowerCase();
        // Iteriere durch alle online Spieler im Cache
        onlinePlayerData.forEach((uuid, info) -> {
            // Prüfe, ob der Spieler in der geänderten Gruppe ist
            if (info.getGroupName().equalsIgnoreCase(lowerCaseName)) {
                Player player = Bukkit.getPlayer(uuid);
                // Wenn Spieler noch online ist, aktualisiere seine Visuals
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        updatePlayerVisuals(player);
                        getLogger().info("Updated visuals for " + player.getName() + " due to prefix change for group " + changedGroup.getName());
                    });
                }
            }
        });
        // Aktualisiere auch die Schilder
        plugin.getSignManager().updateSignsForGroup(changedGroup);
    }

    // --- Interne Scoreboard Logik ---

    /**
     * Erstellt oder aktualisiert das individuelle Scoreboard für einen Spieler.
     * Muss im Bukkit-Hauptthread laufen.
     *
     * @param player Der Spieler.
     */
    private void updateScoreboard(Player player) {
        // computeIfAbsent ist thread-sicher, aber die Operationen danach müssen im Main-Thread sein
        Scoreboard board = playerBoards.computeIfAbsent(player.getUniqueId(), k -> {
            getLogger().info("Creating new scoreboard for " + player.getName());
            return Bukkit.getScoreboardManager().getNewScoreboard();
        });

        Objective objective = board.getObjective("playerInfo"); // Fester Name für unser Objective
        String configTitle = plugin.getConfigManager().getScoreboardTitle();
        if (objective == null) {
            // Objective existiert nicht, neu erstellen
            objective = board.registerNewObjective("playerInfo", "dummy", configTitle);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR); // Anzeige rechts
        } else {
            if (!objective.getDisplayName().equals(configTitle)) {
                objective.setDisplayName(configTitle);
            }
        }

        // Hole aktuelle Spielerdaten
        PlayerGroupInfo info = getPlayerGroupInfo(player.getUniqueId());
        Group group = getPlayerGroup(player.getUniqueId());
        if (group == null) { // Sollte nicht passieren
            getLogger().severe("Cannot update scoreboard for " + player.getName() + ": Group is null!");
            // Zeige Fehler auf Scoreboard an?..
            for (String entry : board.getEntries()) board.resetScores(entry); // Clear old scores
            objective.getScore(ChatColor.RED + "Error: No Group").setScore(1);
            player.setScoreboard(board);
            return;
        }

        // --- Zeilen setzen ---
        List<String> configLines = plugin.getConfigManager().getScoreboardLines();
        int scoreCount = configLines.size(); // Höchster Score = oberste Zeile

        // Temporäre Liste für neue Einträge, um alte zu entfernen
        List<String> currentEntries = new ArrayList<>();

        for (String lineFormat : configLines) {
            String lineText = formatString(lineFormat, player, group); // Ersetze Platzhalter
            // Scoreboard-Zeilen haben Längenlimits (ca. 40 Zeichen, je nach Version)
            // und jede Zeile braucht einen eindeutigen "Entry"-Namen.
            // Wir kürzen und versuchen, durch Leerzeichen am Ende Eindeutigkeit zu schaffen, falls nötig.
            String entry = ensureUniqueEntry(board, lineText, scoreCount);
            currentEntries.add(entry);

            try {
                objective.getScore(entry).setScore(scoreCount--);
            } catch (IllegalArgumentException e) {
                getLogger().warning("Could not set scoreboard score for entry (too long?): " + entry);
            } catch (IllegalStateException e) {
                getLogger().warning("Could not set scoreboard score (objective unregistered?): " + entry);
            }

        }

        for (String oldEntry : new HashSet<>(board.getEntries())) {
            if (!currentEntries.contains(oldEntry) && objective.equals(board.getObjective(DisplaySlot.SIDEBAR))) {
                board.resetScores(oldEntry);
            }
        }

        player.setScoreboard(board);
    }

    /**
     * Stellt sicher, dass ein Scoreboard-Eintrag eindeutig ist und nicht zu lang.
     * Fügt ggf. unsichtbare Farb-Codes hinzu, um Duplikate zu vermeiden.
     *
     * @param board Das Scoreboard.
     * @param text  Der gewünschte Text.
     * @param score Der Score-Wert (kann zur Eindeutigkeit beitragen).
     * @return Ein (hoffentlich) eindeutiger und gültiger Eintrag.
     */
    private String ensureUniqueEntry(Scoreboard board, String text, int score) {
        int maxLength = 40;
        String baseEntry = text.length() > maxLength ? text.substring(0, maxLength) : text;
        String finalEntry = baseEntry;
        int attempt = 0;

        while (board.getEntries().contains(finalEntry)) {
            attempt++;
            finalEntry = baseEntry.substring(0, Math.min(baseEntry.length(), maxLength - 2)) + ChatColor.values()[attempt % 16] + ChatColor.RESET;
            if (attempt > 16) {
                finalEntry = baseEntry.substring(0, Math.min(baseEntry.length(), maxLength - 4)) + score;
                getLogger().warning("Could not create unique scoreboard entry for: " + text);
                break;
            }
        }
        return finalEntry;
    }

    /**
     * Ersetzt Platzhalter in einem Format-String mit Spieler- und Gruppen-Daten.
     *
     * @param format Der String mit Platzhaltern (z.B. "%player%", "%group_prefix%").
     * @param player Der Spieler.
     * @param group  Die Gruppe des Spielers.
     * @return Der formatierte String mit ersetzten Werten und angewendeten Farben.
     */
    public String formatString(String format, Player player, Group group) {
        if (format == null) return "";

        Group effectiveGroup = (group != null) ? group : plugin.getGroupManager().getDefaultGroup();
        PlayerGroupInfo info = (player != null) ? getPlayerGroupInfo(player.getUniqueId()) : null;

        String playerName = (player != null) ? player.getName() : "N/A";
        String groupName = (effectiveGroup != null) ? effectiveGroup.getName() : "N/A";
        String groupPrefix = (effectiveGroup != null) ? effectiveGroup.getPrefix() : "";
        String expiryTimeStr = "Permanent"; // Standard

        // Bestimme Ablaufzeit-String
        if (info != null && !info.isPermanent()) {
            long remaining = info.getExpiryTime() - System.currentTimeMillis();
            if (remaining > 0) {
                expiryTimeStr = TimeUtil.formatDuration(remaining);
            } else {
                expiryTimeStr = "Expired";
            }
        }

        // Ersetze Platzhalter
        format = format.replace("%player%", playerName);
        format = format.replace("%group_name%", groupName);
        format = format.replace("%rank%", groupName);
        format = format.replace("%group_prefix%", groupPrefix);
        format = format.replace("%expiry_time%", expiryTimeStr);
        format = format.replace("%online_players%", String.valueOf(Bukkit.getOnlinePlayers().size()));

        return ChatColor.translateAlternateColorCodes('&', format);
    }


    // --- Task für Ablauf-Checks ---

    /**
     * Startet einen wiederholenden, asynchronen Task, der prüft, ob Gruppen abgelaufen sind.
     */
    private void startExpiryCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpiredGroups();
            }
            // Prüfe z.B. alle 5 Sekunden (100 Ticks). Passe Intervall nach Bedarf an.
            // Häufigere Prüfung = schnellere Reaktion, aber mehr Last.
        }.runTaskTimerAsynchronously(plugin, 120L, 100L); // Start nach 6 Sek, dann alle 5 Sek
    }

    /**
     * Prüft alle online Spieler im Cache auf abgelaufene temporäre Gruppen.
     * Wenn eine Gruppe abgelaufen ist, wird im Hauptthread die Default-Gruppe zugewiesen.
     * Läuft asynchron.
     */
    private void checkExpiredGroups() {
        long now = System.currentTimeMillis();
        List<UUID> expiredPlayersUUIDs = new ArrayList<>();

        // Iteriere sicher durch die ConcurrentHashMap
        onlinePlayerData.forEach((uuid, info) -> {
            // Prüfe nur temporäre Gruppen, die jetzt abgelaufen sind
            if (!info.isPermanent() && info.getExpiryTime() <= now) {
                expiredPlayersUUIDs.add(uuid);
            }
        });

        // Wenn Spieler mit abgelaufenen Gruppen gefunden wurden...
        if (!expiredPlayersUUIDs.isEmpty()) {
            // ...führe die Zuweisung der Default-Gruppe im Hauptthread aus
            Bukkit.getScheduler().runTask(plugin, () -> {
                getLogger().info("Found " + expiredPlayersUUIDs.size() + " players with expired groups. Assigning default group...");
                for (UUID uuid : expiredPlayersUUIDs) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        PlayerGroupInfo currentInfo = onlinePlayerData.get(uuid);
                        if (currentInfo != null && !currentInfo.isPermanent() && currentInfo.getExpiryTime() <= System.currentTimeMillis()) {
                            assignDefaultGroup(player);
                        }
                    } else {
                        // Spieler ist offline gegangen, bevor wir im Main-Thread waren.
                        // Daten werden beim nächsten Login korrekt geladen. Cache wurde bei Quit geleert.
                        // TODO: Man könnte hier die DB direkt aktualisieren, aber Login-Check reicht meist.
                    }
                }
            });
        }
    }

    // --- Hilfsmethoden ---

    /**
     * Lädt Daten für alle Spieler, die beim Plugin-Start bereits online sind.
     */
    public void initializeOnlinePlayers() {
        getLogger().info("Initializing data for " + Bukkit.getOnlinePlayers().size() + " players already online...");
        // Muss im Hauptthread laufen, da loadPlayerData Callbacks im Hauptthread ausführt
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                loadPlayerData(player);
            }
        });
        getLogger().info("Finished initializing online players.");
    }

    /**
     * Räumt beim Deaktivieren des Plugins alle Spielerdaten auf (Permissions, Scoreboard).
     */
    public void cleanupAllPlayers() {
        getLogger().info("Cleaning up player data (Permissions, Scoreboards)...");
        // Muss im Hauptthread laufen wegen Bukkit API (getScoreboardManager, removeAttachment)
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Iteriere sicher über die Keys des Caches
            for (UUID uuid : new HashSet<>(onlinePlayerData.keySet())) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getPermissionManager().removeAttachment(player);
                    if (playerBoards.containsKey(uuid)) {
                        try {
                            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                        } catch (Exception e) {
                            getLogger().log(Level.WARNING, "Error resetting scoreboard for " + player.getName() + " on disable.", e);
                        }
                    }
                }
            }
            onlinePlayerData.clear();
            playerBoards.clear();
            getLogger().info("Player data cleanup finished.");
        });
    }

    // Shortcut zum Logger
    private java.util.logging.Logger getLogger() {
        return plugin.getLogger();
    }
}